package com.officialpapers.api.service;

import com.officialpapers.domain.CreateUploadedDocumentCommand;
import com.officialpapers.domain.CreatedUpload;
import com.officialpapers.domain.DownloadTarget;
import com.officialpapers.domain.UploadTarget;
import com.officialpapers.domain.UploadedDocument;
import com.officialpapers.domain.UploadedDocumentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class UploadedDocumentServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-03-29T06:00:00Z"), ZoneOffset.UTC);

    private InMemoryUploadedDocumentRepository repository;
    private FakeUploadedDocumentObjectStore objectStore;
    private InstructionRecompileTrigger recompileTrigger;
    private UploadedDocumentService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUploadedDocumentRepository();
        objectStore = new FakeUploadedDocumentObjectStore();
        recompileTrigger = mock(InstructionRecompileTrigger.class);
        service = new UploadedDocumentService(
                repository,
                objectStore,
                recompileTrigger,
                FIXED_CLOCK,
                () -> "11111111-1111-1111-1111-111111111111"
        );
    }

    @Test
    void createDocumentReturnsPendingUploadWithoutPersistingMetadata() {
        CreatedUpload created = service.createDocument(
                new CreateUploadedDocumentCommand("memo.pdf", "application/pdf", 128L)
        );

        assertEquals("11111111-1111-1111-1111-111111111111", created.document().id());
        assertEquals("memo.pdf", created.document().filename());
        assertEquals("application/pdf", created.document().contentType());
        assertEquals(UploadedDocumentStatus.PENDING_UPLOAD, created.document().status());
        assertEquals(
                "sample-documents/11111111-1111-1111-1111-111111111111/memo.pdf",
                created.document().sourceObjectKey()
        );
        assertEquals("2026-03-29T06:00:00Z", created.document().createdAt());
        assertEquals("2026-03-29T06:15:00Z", created.upload().expiresAt());
        assertEquals("PUT", created.upload().uploadMethod());
        assertEquals("application/pdf", created.upload().uploadHeaders().get("Content-Type"));
        assertTrue(repository.findById(created.document().id()).isEmpty());
        assertEquals(created.document().sourceObjectKey(), objectStore.lastUploadObjectKey);
        assertEquals("application/pdf", objectStore.lastUploadContentType);
        assertEquals(Duration.ofMinutes(15), objectStore.lastUploadExpiry);
    }

    @Test
    void createDocumentRejectsUnsupportedFileExtension() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.createDocument(
                        new CreateUploadedDocumentCommand("payload.exe", "application/octet-stream", 42L)
                )
        );

        assertEquals("Unsupported file extension: .exe", exception.getMessage());
    }

    @Test
    void listDocumentsReturnsOnlyAvailableDocumentsInUpdatedAtDescendingOrder() {
        repository.save(document(
                "11111111-1111-1111-1111-111111111111",
                "older.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:05:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));
        repository.save(document(
                "22222222-2222-2222-2222-222222222222",
                "pending.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:20:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        ));
        repository.save(document(
                "33333333-3333-3333-3333-333333333333",
                "newer.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));

        List<UploadedDocument> listed = service.listDocuments();

        assertEquals(List.of(
                "33333333-3333-3333-3333-333333333333",
                "11111111-1111-1111-1111-111111111111"
        ), listed.stream().map(UploadedDocument::id).toList());
    }

    @Test
    void completeUploadCreatesMetadataOnlyAfterUploadedObjectExists() {
        objectStore.objectsByPrefix.put(
                "sample-documents/33333333-3333-3333-3333-333333333333/",
                new com.officialpapers.domain.StoredUploadedObject(
                        "sample-documents/33333333-3333-3333-3333-333333333333/memo.pdf",
                        "application/pdf",
                        512L
                )
        );

        UploadedDocument completed = service.completeUpload("33333333-3333-3333-3333-333333333333");

        assertEquals(UploadedDocumentStatus.AVAILABLE, completed.status());
        assertEquals("memo.pdf", completed.filename());
        assertEquals(512L, completed.sizeBytes());
        assertEquals(
                completed,
                repository.findById("33333333-3333-3333-3333-333333333333").orElseThrow()
        );
        verify(recompileTrigger).requestRecompile();
    }

    @Test
    void completeUploadMarksPendingDocumentAvailableAndTriggersRecompile() {
        UploadedDocument pending = document(
                "33333333-3333-3333-3333-333333333333",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);
        objectStore.objectSizes.put(pending.sourceObjectKey(), 512L);

        UploadedDocument completed = service.completeUpload(pending.id());

        assertEquals(UploadedDocumentStatus.AVAILABLE, completed.status());
        assertEquals(512L, completed.sizeBytes());
        assertEquals("2026-03-29T06:00:00Z", completed.createdAt());
        assertEquals("2026-03-29T06:00:00Z", completed.updatedAt());
        verify(recompileTrigger).requestRecompile();
    }

    @Test
    void completeUploadIsIdempotentForAvailableDocument() {
        UploadedDocument available = document(
                "44444444-4444-4444-4444-444444444444",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);

        UploadedDocument completed = service.completeUpload(available.id());

        assertEquals(available, completed);
        assertEquals(0, objectStore.getObjectSizeCalls);
        verifyNoInteractions(recompileTrigger);
    }

    @Test
    void completeUploadReturnsConflictWhenObjectIsMissing() {
        UploadedDocument pending = document(
                "55555555-5555-5555-5555-555555555555",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.completeUpload(pending.id())
        );

        assertEquals("UPLOAD_NOT_FOUND", exception.code());
    }

    @Test
    void createDownloadTargetRejectsPendingDocument() {
        UploadedDocument pending = document(
                "66666666-6666-6666-6666-666666666666",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.createDownloadTarget(pending.id())
        );

        assertEquals("DOCUMENT_NOT_READY", exception.code());
    }

    @Test
    void createDownloadTargetReturnsPresignedDownloadForAvailableDocument() {
        UploadedDocument available = document(
                "77777777-7777-7777-7777-777777777777",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);
        objectStore.objectSizes.put(available.sourceObjectKey(), 2048L);

        DownloadTarget downloadTarget = service.createDownloadTarget(available.id());

        assertEquals("https://download.example.com/object", downloadTarget.downloadUrl());
        assertEquals("GET", downloadTarget.downloadMethod());
        assertEquals(Duration.ofMinutes(15), objectStore.lastDownloadExpiry);
        assertEquals(available.sourceObjectKey(), objectStore.lastDownloadObjectKey);
    }

    @Test
    void deleteDocumentRemovesMetadataAndObjectAndTriggersRecompile() {
        UploadedDocument available = document(
                "88888888-8888-8888-8888-888888888888",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);

        service.deleteDocument(available.id());

        assertTrue(repository.findById(available.id()).isEmpty());
        assertEquals(List.of(available.sourceObjectKey()), objectStore.deletedKeys);
        verify(recompileTrigger).requestRecompile();
    }

    @Test
    void deleteDocumentWrapsObjectStoreDeletionFailure() {
        UploadedDocument available = document(
                "99999999-9999-9999-9999-999999999999",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);
        objectStore.deleteException = new IllegalStateException("boom");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.deleteDocument(available.id())
        );

        assertEquals("Failed to delete uploaded object", exception.getMessage());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        verifyNoInteractions(recompileTrigger);
    }

    private static UploadedDocument document(
            String id,
            String filename,
            String createdAt,
            String updatedAt,
            UploadedDocumentStatus status
    ) {
        return new UploadedDocument(
                id,
                filename,
                "application/pdf",
                256L,
                status,
                "sample-documents/" + id + "/" + filename,
                createdAt,
                updatedAt
        );
    }

    private static final class InMemoryUploadedDocumentRepository implements UploadedDocumentRepository {

        private final Map<String, UploadedDocument> documents = new LinkedHashMap<>();

        @Override
        public void save(UploadedDocument document) {
            documents.put(document.id(), document);
        }

        @Override
        public Optional<UploadedDocument> findById(String documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public List<UploadedDocument> findAll() {
            return documents.values().stream()
                    .sorted(Comparator.comparing(UploadedDocument::id))
                    .toList();
        }

        @Override
        public void deleteById(String documentId) {
            documents.remove(documentId);
        }
    }

    private static final class FakeUploadedDocumentObjectStore implements UploadedDocumentObjectStore {

        private final Map<String, Long> objectSizes = new HashMap<>();
        private final Map<String, com.officialpapers.domain.StoredUploadedObject> objectsByPrefix = new HashMap<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private RuntimeException deleteException;
        private String lastUploadObjectKey;
        private String lastUploadContentType;
        private Duration lastUploadExpiry;
        private String lastDownloadObjectKey;
        private Duration lastDownloadExpiry;
        private int getObjectSizeCalls;

        @Override
        public UploadTarget createUploadTarget(String objectKey, String contentType, Duration expiry) {
            lastUploadObjectKey = objectKey;
            lastUploadContentType = contentType;
            lastUploadExpiry = expiry;
            return new UploadTarget(
                    "https://upload.example.com/object",
                    "PUT",
                    Map.of("Content-Type", contentType),
                    "2026-03-29T06:15:00Z"
            );
        }

        @Override
        public DownloadTarget createDownloadTarget(String objectKey, Duration expiry) {
            lastDownloadObjectKey = objectKey;
            lastDownloadExpiry = expiry;
            return new DownloadTarget(
                    "https://download.example.com/object",
                    "GET",
                    "2026-03-29T06:15:00Z"
            );
        }

        @Override
        public Optional<Long> getObjectSize(String objectKey) {
            getObjectSizeCalls++;
            return Optional.ofNullable(objectSizes.get(objectKey));
        }

        @Override
        public Optional<com.officialpapers.domain.StoredUploadedObject> findObjectByPrefix(String objectKeyPrefix) {
            return Optional.ofNullable(objectsByPrefix.get(objectKeyPrefix));
        }

        @Override
        public void delete(String objectKey) {
            if (deleteException != null) {
                throw deleteException;
            }
            deletedKeys.add(objectKey);
        }
    }
}
