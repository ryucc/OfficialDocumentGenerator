package com.officialpapers.api.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3UploadedDocumentObjectStoreTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    private S3UploadedDocumentObjectStore store;

    @BeforeEach
    void setUp() {
        store = new S3UploadedDocumentObjectStore(s3Client, s3Presigner, "uploaded-documents-test");
    }

    @Test
    void createUploadTargetUsesPresignerResponse() throws Exception {
        PresignedPutObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedPutObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://upload.example.com/object"));
        when(presignedRequest.httpRequest()).thenReturn(SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.PUT)
                .uri(URI.create("https://upload.example.com/object"))
                .build());
        when(presignedRequest.expiration()).thenReturn(Instant.parse("2026-03-29T06:15:00Z"));
        when(s3Presigner.presignPutObject(org.mockito.ArgumentMatchers.any(PutObjectPresignRequest.class))).thenReturn(presignedRequest);

        com.officialpapers.domain.UploadTarget uploadTarget =
                store.createUploadTarget("sample-documents/id/memo.pdf", "application/pdf", Duration.ofMinutes(15));

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());

        assertEquals("uploaded-documents-test", captor.getValue().putObjectRequest().bucket());
        assertEquals("sample-documents/id/memo.pdf", captor.getValue().putObjectRequest().key());
        assertEquals("application/pdf", captor.getValue().putObjectRequest().contentType());
        assertEquals(Duration.ofMinutes(15), captor.getValue().signatureDuration());
        assertEquals("https://upload.example.com/object", uploadTarget.uploadUrl());
        assertEquals("PUT", uploadTarget.uploadMethod());
        assertEquals("application/pdf", uploadTarget.uploadHeaders().get("Content-Type"));
        assertEquals("2026-03-29T06:15:00Z", uploadTarget.expiresAt());
    }

    @Test
    void createDownloadTargetUsesPresignerResponse() throws Exception {
        PresignedGetObjectRequest presignedRequest = org.mockito.Mockito.mock(PresignedGetObjectRequest.class);
        when(presignedRequest.url()).thenReturn(new URL("https://download.example.com/object"));
        when(presignedRequest.httpRequest()).thenReturn(SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.GET)
                .uri(URI.create("https://download.example.com/object"))
                .build());
        when(presignedRequest.expiration()).thenReturn(Instant.parse("2026-03-29T06:15:00Z"));
        when(s3Presigner.presignGetObject(org.mockito.ArgumentMatchers.any(GetObjectPresignRequest.class))).thenReturn(presignedRequest);

        com.officialpapers.domain.DownloadTarget downloadTarget =
                store.createDownloadTarget("sample-documents/id/memo.pdf", Duration.ofMinutes(15));

        ArgumentCaptor<GetObjectPresignRequest> captor = ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(s3Presigner).presignGetObject(captor.capture());

        assertEquals("uploaded-documents-test", captor.getValue().getObjectRequest().bucket());
        assertEquals("sample-documents/id/memo.pdf", captor.getValue().getObjectRequest().key());
        assertEquals(Duration.ofMinutes(15), captor.getValue().signatureDuration());
        assertEquals("https://download.example.com/object", downloadTarget.downloadUrl());
        assertEquals("GET", downloadTarget.downloadMethod());
    }

    @Test
    void getObjectSizeReturnsContentLengthWhenObjectExists() {
        when(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder().contentLength(2048L).build()
        );

        Optional<Long> size = store.getObjectSize("sample-documents/id/memo.pdf");

        assertEquals(Optional.of(2048L), size);
    }

    @Test
    void getObjectSizeReturnsEmptyWhenS3RespondsNotFound() {
        when(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class))).thenThrow(
                S3Exception.builder().statusCode(404).build()
        );

        Optional<Long> size = store.getObjectSize("sample-documents/id/memo.pdf");

        assertTrue(size.isEmpty());
    }

    @Test
    void findObjectByPrefixReturnsStoredObjectDetailsWhenExactlyOneMatchExists() {
        when(s3Client.listObjectsV2(org.mockito.ArgumentMatchers.any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder()
                        .contents(List.of(S3Object.builder().key("sample-documents/id/memo.pdf").build()))
                        .build()
        );
        when(s3Client.headObject(org.mockito.ArgumentMatchers.any(HeadObjectRequest.class))).thenReturn(
                HeadObjectResponse.builder()
                        .contentLength(2048L)
                        .contentType("application/pdf")
                        .build()
        );

        Optional<com.officialpapers.domain.StoredUploadedObject> object =
                store.findObjectByPrefix("sample-documents/id/");

        ArgumentCaptor<ListObjectsV2Request> captor = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        verify(s3Client).listObjectsV2(captor.capture());
        assertEquals("uploaded-documents-test", captor.getValue().bucket());
        assertEquals("sample-documents/id/", captor.getValue().prefix());
        assertEquals(Optional.of("sample-documents/id/memo.pdf"), object.map(com.officialpapers.domain.StoredUploadedObject::objectKey));
        assertEquals(Optional.of(2048L), object.map(com.officialpapers.domain.StoredUploadedObject::sizeBytes));
    }

    @Test
    void findObjectByPrefixReturnsEmptyWhenNoMatchExists() {
        when(s3Client.listObjectsV2(org.mockito.ArgumentMatchers.any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().contents(List.of()).build()
        );

        Optional<com.officialpapers.domain.StoredUploadedObject> object =
                store.findObjectByPrefix("sample-documents/id/");

        assertTrue(object.isEmpty());
    }

    @Test
    void deleteRemovesObjectFromConfiguredBucket() {
        store.delete("sample-documents/id/memo.pdf");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertEquals("uploaded-documents-test", captor.getValue().bucket());
        assertEquals("sample-documents/id/memo.pdf", captor.getValue().key());
    }
}
