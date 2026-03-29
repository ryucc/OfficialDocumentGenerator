package com.officialpapers.api.service;

public interface InstructionContentStore {

    void put(String s3Key, String content);

    String get(String s3Key);

    void delete(String s3Key);
}
