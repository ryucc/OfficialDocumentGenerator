package com.officialpapers.api.service;

import com.officialpapers.domain.AuthenticatedUser;
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

    public List<UploadedDocument> listDocuments(AuthenticatedUser user) {
        requireUserId(user);
        return repository.findAll().stream()
                .filter(document -> document.status() == UploadedDocumentStatus.AVAILABLE)
                .sorted(UPDATED_AT_DESC)
                .toList();
    }

    public CreatedUpload createDocument(AuthenticatedUser user, CreateUploadedDocumentCommand request) {
        validateCreateRequest(request);

        String ownerUserId = requireUserId(user);
        String documentId = idSupplier.get();
        String timestamp = Instant.now(clock).toString();
        UploadedDocument document = new UploadedDocument(
                documentId,
                ownerUserId,
                request.filename().trim(),
                request.contentType().trim(),
                request.sizeBytes(),
                UploadedDocumentStatus.PENDING_UPLOAD,
                buildObjectKey(ownerUserId, documentId, request.filename().trim()),
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

    public UploadedDocument getDocument(AuthenticatedUser user, String documentId) {
        return findVisibleDocument(user, documentId);
    }

    public UploadedDocument completeUpload(AuthenticatedUser user, String documentId) {
        String ownerUserId = requireUserId(user);
        Optional<UploadedDocument> existing = repository.findById(ownerUserId, documentId);
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
                    current.ownerUserId(),
                    current.filename(),
                    current.contentType(),
                    objectSize,
                    UploadedDocumentStatus.AVAILABLE,
                    current.sourceObjectKey(),
                    current.createdAt(),
                    timestamp
            );

            repository.save(updated);
            triggerRecompileQuietly();
            return updated;
        }

        StoredUploadedObject uploadedObject = objectStore.findObjectByPrefix(buildObjectPrefix(ownerUserId, documentId))
                .orElseThrow(() -> new ConflictException("UPLOAD_NOT_FOUND", "Uploaded object was not found"));

        String timestamp = Instant.now(clock).toString();
        UploadedDocument created = new UploadedDocument(
                documentId,
                ownerUserId,
                filenameFromObjectKey(uploadedObject.objectKey()),
                normalizeContentType(uploadedObject.contentType()),
                uploadedObject.sizeBytes(),
                UploadedDocumentStatus.AVAILABLE,
                uploadedObject.objectKey(),
                timestamp,
                timestamp
        );

        repository.save(created);
        triggerRecompileQuietly();
        return created;
    }

    public DownloadTarget createDownloadTarget(AuthenticatedUser user, String documentId) {
        UploadedDocument existing = findVisibleDocument(user, documentId);
        if (existing.status() != UploadedDocumentStatus.AVAILABLE) {
            throw new ConflictException("DOCUMENT_NOT_READY", "Document is not ready for download");
        }
        if (objectStore.getObjectSize(existing.sourceObjectKey()).isEmpty()) {
            throw new ConflictException("DOCUMENT_STORAGE_INCONSISTENT", "Document storage is inconsistent");
        }
        return objectStore.createDownloadTarget(existing.sourceObjectKey(), URL_EXPIRY);
    }

    public void deleteDocument(AuthenticatedUser user, String documentId) {
        UploadedDocument existing = findOwnedDocument(user, documentId);
        try {
            objectStore.delete(existing.sourceObjectKey());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Failed to delete uploaded object", exception);
        }
        repository.deleteById(existing.ownerUserId(), documentId);
        triggerRecompileQuietly();
    }

    private UploadedDocument findOwnedDocument(AuthenticatedUser user, String documentId) {
        return repository.findById(requireUserId(user), documentId)
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    private UploadedDocument findVisibleDocument(AuthenticatedUser user, String documentId) {
        requireUserId(user);
        return repository.findByDocumentId(documentId)
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

    private String buildObjectKey(String ownerUserId, String documentId, String filename) {
        return "sample-documents/" + ownerUserId + "/" + documentId + "/" + filename;
    }

    private String buildObjectPrefix(String ownerUserId, String documentId) {
        return "sample-documents/" + ownerUserId + "/" + documentId + "/";
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

    private String requireUserId(AuthenticatedUser user) {
        if (user == null || user.userId() == null || user.userId().isBlank()) {
            throw new UnauthorizedException("UNAUTHORIZED", "Authenticated user is required");
        }
        return user.userId();
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
