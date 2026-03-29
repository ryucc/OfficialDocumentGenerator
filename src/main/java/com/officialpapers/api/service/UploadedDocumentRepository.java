package com.officialpapers.api.service;

import com.officialpapers.domain.UploadedDocument;

import java.util.List;
import java.util.Optional;

public interface UploadedDocumentRepository {

    void save(UploadedDocument document);

    Optional<UploadedDocument> findById(String ownerUserId, String documentId);

    List<UploadedDocument> findAllByOwnerUserId(String ownerUserId);

    void deleteById(String ownerUserId, String documentId);
}
