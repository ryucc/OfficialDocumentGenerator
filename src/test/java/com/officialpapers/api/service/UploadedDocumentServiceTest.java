package com.officialpapers.api.service;

import com.officialpapers.domain.AuthenticatedUser;
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
    private static final AuthenticatedUser USER =
            new AuthenticatedUser("user-123", "user@example.com", true);
    private static final AuthenticatedUser OTHER_USER =
            new AuthenticatedUser("user-999", "other@example.com", true);

    private InMemoryUploadedDocumentRepository repository;
    private FakeUploadedDocumentObjectStore objectStore;
    private List<String> deleteOperations;
    private InstructionRecompileTrigger recompileTrigger;
    private UploadedDocumentService service;

    @BeforeEach
    void setUp() {
        deleteOperations = new ArrayList<>();
        repository = new InMemoryUploadedDocumentRepository(deleteOperations);
        objectStore = new FakeUploadedDocumentObjectStore(deleteOperations);
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
    void createDocumentReturnsPendingUploadAndPersistsOwnerScopedMetadata() {
        CreatedUpload created = service.createDocument(
                USER,
                new CreateUploadedDocumentCommand("memo.pdf", "application/pdf", 128L)
        );

        assertEquals("11111111-1111-1111-1111-111111111111", created.document().id());
        assertEquals("user-123", created.document().ownerUserId());
        assertEquals(
                "sample-documents/user-123/11111111-1111-1111-1111-111111111111/memo.pdf",
                created.document().sourceObjectKey()
        );
        assertEquals(created.document(), repository.findById(USER.userId(), created.document().id()).orElseThrow());
        assertEquals(created.document().sourceObjectKey(), objectStore.lastUploadObjectKey);
        assertEquals(Duration.ofMinutes(15), objectStore.lastUploadExpiry);
    }

    @Test
    void createDocumentRejectsUnsupportedFileExtension() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> service.createDocument(
                        USER,
                        new CreateUploadedDocumentCommand("payload.exe", "application/octet-stream", 42L)
                )
        );

        assertEquals("Unsupported file extension: .exe", exception.getMessage());
    }

    @Test
    void listDocumentsReturnsOnlyAvailableDocumentsForAuthenticatedUser() {
        repository.save(document(
                USER.userId(),
                "11111111-1111-1111-1111-111111111111",
                "older.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:05:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));
        repository.save(document(
                USER.userId(),
                "22222222-2222-2222-2222-222222222222",
                "pending.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:20:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        ));
        repository.save(document(
                USER.userId(),
                "33333333-3333-3333-3333-333333333333",
                "newer.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));
        repository.save(document(
                OTHER_USER.userId(),
                "44444444-4444-4444-4444-444444444444",
                "other.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:30:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));

        List<UploadedDocument> listed = service.listDocuments(USER);

        assertEquals(List.of(
                "33333333-3333-3333-3333-333333333333",
                "11111111-1111-1111-1111-111111111111"
        ), listed.stream().map(UploadedDocument::id).toList());
    }

    @Test
    void getDocumentReturnsNotFoundForAnotherUsersDocument() {
        repository.save(document(
                OTHER_USER.userId(),
                "11111111-1111-1111-1111-111111111111",
                "other.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        ));

        assertThrows(NotFoundException.class, () -> service.getDocument(USER, "11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void completeUploadMarksPendingDocumentAvailableAndTriggersRecompile() {
        UploadedDocument pending = document(
                USER.userId(),
                "33333333-3333-3333-3333-333333333333",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);
        objectStore.objectSizes.put(pending.sourceObjectKey(), 512L);

        UploadedDocument completed = service.completeUpload(USER, pending.id());

        assertEquals(UploadedDocumentStatus.AVAILABLE, completed.status());
        assertEquals(512L, completed.sizeBytes());
        assertEquals(USER.userId(), completed.ownerUserId());
        verify(recompileTrigger).requestRecompile();
    }

    @Test
    void completeUploadIsIdempotentForAvailableDocument() {
        UploadedDocument available = document(
                USER.userId(),
                "44444444-4444-4444-4444-444444444444",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);

        UploadedDocument completed = service.completeUpload(USER, available.id());

        assertEquals(available, completed);
        assertEquals(0, objectStore.getObjectSizeCalls);
        verifyNoInteractions(recompileTrigger);
    }

    @Test
    void completeUploadReturnsConflictWhenObjectIsMissing() {
        UploadedDocument pending = document(
                USER.userId(),
                "55555555-5555-5555-5555-555555555555",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.completeUpload(USER, pending.id())
        );

        assertEquals("UPLOAD_NOT_FOUND", exception.code());
    }

    @Test
    void createDownloadTargetRejectsPendingDocument() {
        UploadedDocument pending = document(
                USER.userId(),
                "66666666-6666-6666-6666-666666666666",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:00:00Z",
                UploadedDocumentStatus.PENDING_UPLOAD
        );
        repository.save(pending);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.createDownloadTarget(USER, pending.id())
        );

        assertEquals("DOCUMENT_NOT_READY", exception.code());
    }

    @Test
    void createDownloadTargetReturnsPresignedDownloadForAvailableDocument() {
        UploadedDocument available = document(
                USER.userId(),
                "77777777-7777-7777-7777-777777777777",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);
        objectStore.objectSizes.put(available.sourceObjectKey(), 2048L);

        DownloadTarget downloadTarget = service.createDownloadTarget(USER, available.id());

        assertEquals("https://download.example.com/object", downloadTarget.downloadUrl());
        assertEquals(available.sourceObjectKey(), objectStore.lastDownloadObjectKey);
    }

    @Test
    void deleteDocumentRemovesObjectBeforeMetadataAndTriggersRecompile() {
        UploadedDocument available = document(
                USER.userId(),
                "88888888-8888-8888-8888-888888888888",
                "memo.pdf",
                "2026-03-29T06:00:00Z",
                "2026-03-29T06:10:00Z",
                UploadedDocumentStatus.AVAILABLE
        );
        repository.save(available);

        service.deleteDocument(USER, available.id());

        assertTrue(repository.findById(USER.userId(), available.id()).isEmpty());
        assertEquals(List.of(available.sourceObjectKey()), objectStore.deletedKeys);
        assertEquals(List.of(
                "objectStore:" + available.sourceObjectKey(),
                "repository:" + USER.userId() + "#" + available.id()
        ), deleteOperations);
        verify(recompileTrigger).requestRecompile();
    }

    @Test
    void deleteDocumentWrapsObjectStoreDeletionFailure() {
        UploadedDocument available = document(
                USER.userId(),
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
                () -> service.deleteDocument(USER, available.id())
        );

        assertEquals("Failed to delete uploaded object", exception.getMessage());
        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertTrue(repository.findById(USER.userId(), available.id()).isPresent());
        assertTrue(objectStore.deletedKeys.isEmpty());
        verifyNoInteractions(recompileTrigger);
    }

    private static UploadedDocument document(
            String ownerUserId,
            String id,
            String filename,
            String createdAt,
            String updatedAt,
            UploadedDocumentStatus status
    ) {
        return new UploadedDocument(
                id,
                ownerUserId,
                filename,
                "application/pdf",
                256L,
                status,
                "sample-documents/" + ownerUserId + "/" + id + "/" + filename,
                createdAt,
                updatedAt
        );
    }

    private static final class InMemoryUploadedDocumentRepository implements UploadedDocumentRepository {

        private final Map<String, UploadedDocument> documents = new LinkedHashMap<>();
        private final List<String> deleteOperations;

        private InMemoryUploadedDocumentRepository(List<String> deleteOperations) {
            this.deleteOperations = deleteOperations;
        }

        @Override
        public void save(UploadedDocument document) {
            documents.put(key(document.ownerUserId(), document.id()), document);
        }

        @Override
        public Optional<UploadedDocument> findById(String ownerUserId, String documentId) {
            return Optional.ofNullable(documents.get(key(ownerUserId, documentId)));
        }

        @Override
        public List<UploadedDocument> findAllByOwnerUserId(String ownerUserId) {
            return documents.values().stream()
                    .filter(document -> document.ownerUserId().equals(ownerUserId))
                    .sorted(Comparator.comparing(UploadedDocument::id))
                    .toList();
        }

        @Override
        public void deleteById(String ownerUserId, String documentId) {
            deleteOperations.add("repository:" + key(ownerUserId, documentId));
            documents.remove(key(ownerUserId, documentId));
        }

        private String key(String ownerUserId, String documentId) {
            return ownerUserId + "#" + documentId;
        }
    }

    private static final class FakeUploadedDocumentObjectStore implements UploadedDocumentObjectStore {

        private final Map<String, Long> objectSizes = new HashMap<>();
        private final List<String> deletedKeys = new ArrayList<>();
        private final List<String> deleteOperations;
        private RuntimeException deleteException;
        private String lastUploadObjectKey;
        private Duration lastUploadExpiry;
        private String lastDownloadObjectKey;
        private int getObjectSizeCalls;

        private FakeUploadedDocumentObjectStore(List<String> deleteOperations) {
            this.deleteOperations = deleteOperations;
        }

        @Override
        public UploadTarget createUploadTarget(String objectKey, String contentType, Duration expiry) {
            lastUploadObjectKey = objectKey;
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
        public void delete(String objectKey) {
            if (deleteException != null) {
                throw deleteException;
            }
            deleteOperations.add("objectStore:" + objectKey);
            deletedKeys.add(objectKey);
        }
    }
}
