package com.officialpapers.api.persistence;

import com.officialpapers.api.service.InstructionContentStore;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;

public class S3InstructionContentStore implements InstructionContentStore {

    private final S3Client s3Client;
    private final String bucketName;

    public S3InstructionContentStore(S3Client s3Client, String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    @Override
    public void put(String s3Key, String content) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType("text/plain; charset=utf-8")
                        .build(),
                RequestBody.fromString(content, StandardCharsets.UTF_8)
        );
    }

    @Override
    public String get(String s3Key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .build())
                .asString(StandardCharsets.UTF_8);
    }

    @Override
    public void delete(String s3Key) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build());
    }
}
