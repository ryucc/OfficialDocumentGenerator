package com.officialpapers.api.repository;

import com.officialpapers.api.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByEmailEventIdOrderByCreatedAtDesc(UUID emailEventId);
}
