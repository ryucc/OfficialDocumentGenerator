package com.officialpapers.api.di;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.officialpapers.api.persistence.DynamoDbUploadedDocumentRepository;
import com.officialpapers.api.persistence.S3UploadedDocumentObjectStore;
import com.officialpapers.api.service.InstructionRecompileTrigger;
import com.officialpapers.api.service.NoOpInstructionRecompileTrigger;
import com.officialpapers.api.service.UploadedDocumentObjectStore;
import com.officialpapers.api.service.UploadedDocumentRepository;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Clock;

@Module
public interface LambdaModule {

    @Provides
    @Singleton
    static ObjectMapper provideObjectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Provides
    @Singleton
    static Clock provideClock() {
        return Clock.systemUTC();
    }

    @Provides
    @Singleton
    static DynamoDbClient provideDynamoDbClient() {
        return DynamoDbClient.create();
    }

    @Provides
    @Singleton
    static S3Client provideS3Client() {
        return S3Client.create();
    }

    @Provides
    @Singleton
    static S3Presigner provideS3Presigner() {
        return S3Presigner.create();
    }

    @Provides
    @Named("uploadedDocumentMetadataTable")
    static String provideUploadedDocumentMetadataTableName() {
        return requireEnvironmentVariable("UPLOADED_DOCUMENT_METADATA_TABLE");
    }

    @Provides
    @Named("uploadedDocumentBucket")
    static String provideUploadedDocumentBucketName() {
        return requireEnvironmentVariable("UPLOADED_DOCUMENT_BUCKET");
    }

    @Provides
    @Singleton
    static UploadedDocumentRepository provideUploadedDocumentRepository(
            DynamoDbUploadedDocumentRepository repository
    ) {
        return repository;
    }

    @Provides
    @Singleton
    static UploadedDocumentObjectStore provideUploadedDocumentObjectStore(
            S3UploadedDocumentObjectStore store
    ) {
        return store;
    }

    @Provides
    @Singleton
    static InstructionRecompileTrigger provideInstructionRecompileTrigger(
            NoOpInstructionRecompileTrigger trigger
    ) {
        return trigger;
    }

    private static String requireEnvironmentVariable(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
