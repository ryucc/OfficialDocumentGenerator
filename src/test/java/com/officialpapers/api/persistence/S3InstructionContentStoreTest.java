package com.officialpapers.api.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3InstructionContentStoreTest {

    @Mock
    private S3Client s3Client;

    @Test
    void putUploadsUtf8TextFile() throws Exception {
        S3InstructionContentStore store = new S3InstructionContentStore(s3Client, "instruction-content");

        store.put("instructions/11111111-1111-1111-1111-111111111111.txt", "Formal tone");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client).putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertEquals("instruction-content", requestCaptor.getValue().bucket());
        assertEquals("text/plain; charset=utf-8", requestCaptor.getValue().contentType());
        assertEquals("Formal tone", new String(bodyCaptor.getValue().contentStreamProvider().newStream().readAllBytes(),
                StandardCharsets.UTF_8));
    }

    @Test
    void getReturnsUtf8Content() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        "Formal tone".getBytes(StandardCharsets.UTF_8)
                ));

        S3InstructionContentStore store = new S3InstructionContentStore(s3Client, "instruction-content");

        String content = store.get("instructions/11111111-1111-1111-1111-111111111111.txt");

        assertEquals("Formal tone", content);
    }

    @Test
    void deleteRemovesObject() {
        S3InstructionContentStore store = new S3InstructionContentStore(s3Client, "instruction-content");

        store.delete("instructions/11111111-1111-1111-1111-111111111111.txt");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(requestCaptor.capture());
        assertEquals("instruction-content", requestCaptor.getValue().bucket());
        assertEquals("instructions/11111111-1111-1111-1111-111111111111.txt", requestCaptor.getValue().key());
    }
}
