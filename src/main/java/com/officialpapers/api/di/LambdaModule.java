package com.officialpapers.api.di;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.officialpapers.api.persistence.DynamoDbInstructionMetadataRepository;
import com.officialpapers.api.persistence.S3InstructionContentStore;
import com.officialpapers.api.service.InstructionContentStore;
import com.officialpapers.api.service.InstructionMetadataRepository;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

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
    @Named("instructionMetadataTable")
    static String provideInstructionMetadataTableName() {
        return requireEnvironmentVariable("INSTRUCTION_METADATA_TABLE");
    }

    @Provides
    @Named("instructionContentBucket")
    static String provideInstructionContentBucketName() {
        return requireEnvironmentVariable("INSTRUCTION_CONTENT_BUCKET");
    }

    @Provides
    @Singleton
    static InstructionMetadataRepository provideInstructionMetadataRepository(
            DynamoDbInstructionMetadataRepository repository
    ) {
        return repository;
    }

    @Provides
    @Singleton
    static InstructionContentStore provideInstructionContentStore(
            S3InstructionContentStore store
    ) {
        return store;
    }

    private static String requireEnvironmentVariable(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
