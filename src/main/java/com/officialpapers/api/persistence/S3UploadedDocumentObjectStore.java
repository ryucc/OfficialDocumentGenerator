package com.officialpapers.api.persistence;

import com.officialpapers.api.service.UploadedDocumentObjectStore;
import com.officialpapers.domain.DownloadTarget;
import com.officialpapers.domain.UploadTarget;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import javax.inject.Inject;
import javax.inject.Named;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public class S3UploadedDocumentObjectStore implements UploadedDocumentObjectStore {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucketName;

    @Inject
    public S3UploadedDocumentObjectStore(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Named("uploadedDocumentBucket") String bucketName
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucketName = bucketName;
    }

    @Override
    public UploadTarget createUploadTarget(String objectKey, String contentType, Duration expiry) {
        PresignedPutObjectRequest request = s3Presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .contentType(contentType)
                        .build())
                .build());

        return new UploadTarget(
                request.url().toString(),
                request.httpRequest().method().name(),
                Map.of("Content-Type", contentType),
                request.expiration().toString()
        );
    }

    @Override
    public DownloadTarget createDownloadTarget(String objectKey, Duration expiry) {
        PresignedGetObjectRequest request = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(expiry)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build())
                .build());

        return new DownloadTarget(
                request.url().toString(),
                request.httpRequest().method().name(),
                request.expiration().toString()
        );
    }

    @Override
    public Optional<Long> getObjectSize(String objectKey) {
        try {
            return Optional.of(s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build())
                    .contentLength());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build());
    }
}
