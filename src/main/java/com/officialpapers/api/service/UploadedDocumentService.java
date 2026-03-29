package com.officialpapers.api.service;

import com.officialpapers.domain.CreateUploadedDocumentCommand;
import com.officialpapers.domain.CreatedUpload;
import com.officialpapers.domain.DownloadTarget;
import com.officialpapers.domain.StoredUploadedObject;
import com.officialpapers.domain.UploadTarget;
import com.officialpapers.domain.UploadedDocument;
import com.officialpapers.domain.UploadedDocumentStatus;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Singleton
public class UploadedDocumentService {

    private static final Duration URL_EXPIRY = Duration.ofMinutes(15);
    private static final Comparator<UploadedDocument> UPDATED_AT_DESC =
            Comparator.comparing(UploadedDocument::updatedAt).reversed();
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "doc", "docx", "rtf", "odt",
            "xls", "xlsx", "csv", "ods",
            "ppt", "pptx", "odp",
            "pdf", "png", "jpg", "jpeg"
    );

    private final UploadedDocumentRepository repository;
    private final UploadedDocumentObjectStore objectStore;
    private final InstructionRecompileTrigger recompileTrigger;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public UploadedDocumentService(
            UploadedDocumentRepository repository,
            UploadedDocumentObjectStore objectStore,
            InstructionRecompileTrigger recompileTrigger,
            Clock clock,
            Supplier<String> idSupplier
    ) {
        this.repository = repository;
        this.objectStore = objectStore;
        this.recompileTrigger = recompileTrigger;
        this.clock = clock;
        this.idSupplier = idSupplier;
    }

    @Inject
    public UploadedDocumentService(
            UploadedDocumentRepository repository,
            UploadedDocumentObjectStore objectStore,
            InstructionRecompileTrigger recompileTrigger,
            Clock clock
    ) {
        this(repository, objectStore, recompileTrigger, clock, () -> UUID.randomUUID().toString());
    }

    public List<UploadedDocument> listDocuments() {
        return repository.findAll().stream()
                .filter(document -> document.status() == UploadedDocumentStatus.AVAILABLE)
                .sorted(UPDATED_AT_DESC)
                .toList();
    }

    public CreatedUpload createDocument(CreateUploadedDocumentCommand request) {
        validateCreateRequest(request);

        String documentId = idSupplier.get();
        String timestamp = Instant.now(clock).toString();
        UploadedDocument document = new UploadedDocument(
                documentId,
                request.filename().trim(),
                request.contentType().trim(),
                request.sizeBytes(),
                UploadedDocumentStatus.PENDING_UPLOAD,
                buildObjectKey(documentId, request.filename().trim()),
                null,
                timestamp,
                timestamp
        );

        UploadTarget uploadTarget = objectStore.createUploadTarget(
                document.sourceObjectKey(),
                document.contentType(),
                URL_EXPIRY
        );
        return new CreatedUpload(document, uploadTarget);
    }

    public UploadedDocument getDocument(String documentId) {
        return findDocument(documentId);
    }

    public UploadedDocument completeUpload(String documentId) {
        Optional<UploadedDocument> existing = repository.findById(documentId);
        if (existing.isPresent()) {
            UploadedDocument current = existing.get();
            if (current.status() == UploadedDocumentStatus.AVAILABLE) {
                return current;
            }

            Long objectSize = objectStore.getObjectSize(current.sourceObjectKey())
                    .orElseThrow(() -> new ConflictException("UPLOAD_NOT_FOUND", "Uploaded object was not found"));

            String timestamp = Instant.now(clock).toString();
            UploadedDocument updated = new UploadedDocument(
                    current.id(),
                    current.filename(),
                    current.contentType(),
                    objectSize,
                    UploadedDocumentStatus.AVAILABLE,
                    current.sourceObjectKey(),
                    current.textObjectKey(),
                    current.createdAt(),
                    timestamp
            );

            repository.save(updated);
            triggerRecompileQuietly();
            return updated;
        }

        StoredUploadedObject uploadedObject = objectStore.findObjectByPrefix(buildObjectPrefix(documentId))
                .orElseThrow(() -> new ConflictException("UPLOAD_NOT_FOUND", "Uploaded object was not found"));

        String timestamp = Instant.now(clock).toString();
        UploadedDocument created = new UploadedDocument(
                documentId,
                filenameFromObjectKey(uploadedObject.objectKey()),
                normalizeContentType(uploadedObject.contentType()),
                uploadedObject.sizeBytes(),
                UploadedDocumentStatus.AVAILABLE,
                uploadedObject.objectKey(),
                null,
                timestamp,
                timestamp
        );

        repository.save(created);
        triggerRecompileQuietly();
        return created;
    }

    public DownloadTarget createDownloadTarget(String documentId) {
        UploadedDocument existing = findDocument(documentId);
        if (existing.status() != UploadedDocumentStatus.AVAILABLE) {
            throw new ConflictException("DOCUMENT_NOT_READY", "Document is not ready for download");
        }
        if (objectStore.getObjectSize(existing.sourceObjectKey()).isEmpty()) {
            throw new ConflictException("DOCUMENT_STORAGE_INCONSISTENT", "Document storage is inconsistent");
        }
        return objectStore.createDownloadTarget(existing.sourceObjectKey(), URL_EXPIRY);
    }

    public void deleteDocument(String documentId) {
        UploadedDocument existing = findDocument(documentId);
        repository.deleteById(documentId);
        try {
            objectStore.delete(existing.sourceObjectKey());
            // Also delete the text extraction file if it exists
            if (existing.textObjectKey() != null) {
                try {
                    objectStore.delete(existing.textObjectKey());
                } catch (RuntimeException textException) {
                    // Log but don't fail if text file deletion fails
                }
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to delete uploaded object", exception);
        }
        triggerRecompileQuietly();
    }

    private UploadedDocument findDocument(String documentId) {
        return repository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    private void validateCreateRequest(CreateUploadedDocumentCommand request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.filename() == null || request.filename().isBlank()) {
            throw new BadRequestException("filename is required");
        }
        if (request.contentType() == null || request.contentType().isBlank()) {
            throw new BadRequestException("contentType is required");
        }
        if (request.sizeBytes() != null && request.sizeBytes() < 0) {
            throw new BadRequestException("sizeBytes must be greater than or equal to 0");
        }

        String filename = request.filename().trim();
        if (filename.contains("/") || filename.contains("\\")) {
            throw new BadRequestException("filename must not contain path separators");
        }

        String extension = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported file extension: ." + extension);
        }
    }

    private String buildObjectKey(String documentId, String filename) {
        return "sample-documents/" + documentId + "/" + filename;
    }

    private String buildObjectPrefix(String documentId) {
        return "sample-documents/" + documentId + "/";
    }

    private String extensionOf(String filename) {
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == filename.length() - 1) {
            throw new BadRequestException("filename must include a supported file extension");
        }
        return filename.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }

    private void triggerRecompileQuietly() {
        try {
            recompileTrigger.requestRecompile();
        } catch (RuntimeException ignored) {
            // Placeholder hook must not block document CRUD in this phase.
        }
    }

    private String filenameFromObjectKey(String objectKey) {
        int separatorIndex = objectKey.lastIndexOf('/');
        return separatorIndex >= 0 ? objectKey.substring(separatorIndex + 1) : objectKey;
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType;
    }
}
