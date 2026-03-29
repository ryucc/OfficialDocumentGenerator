package com.officialpapers.api.service;

import com.officialpapers.domain.DownloadTarget;
import com.officialpapers.domain.StoredUploadedObject;
import com.officialpapers.domain.UploadTarget;

import java.time.Duration;
import java.util.Optional;

public interface UploadedDocumentObjectStore {

    UploadTarget createUploadTarget(String objectKey, String contentType, Duration expiry);

    DownloadTarget createDownloadTarget(String objectKey, Duration expiry);

    Optional<Long> getObjectSize(String objectKey);

    Optional<StoredUploadedObject> findObjectByPrefix(String objectKeyPrefix);

    void delete(String objectKey);
}
