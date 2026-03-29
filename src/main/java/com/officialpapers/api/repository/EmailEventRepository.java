package com.officialpapers.api.repository;

import com.officialpapers.api.entity.EmailEvent;
import com.officialpapers.api.enums.EmailEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailEventRepository extends JpaRepository<EmailEvent, UUID> {

    Page<EmailEvent> findByTitleContaining(String title, Pageable pageable);

    Page<EmailEvent> findByStatus(EmailEventStatus status, Pageable pageable);

    Page<EmailEvent> findByTitleContainingAndStatus(String title, EmailEventStatus status, Pageable pageable);

    Optional<EmailEvent> findByCorrelationKey(String correlationKey);
}
