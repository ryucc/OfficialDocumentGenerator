package com.officialpapers.api.entity;

import com.officialpapers.api.enums.EmailEventStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmailEventStatus status;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "source_email_id", nullable = false)
    private Email sourceEmail;

    @Column
    private String correlationKey;

    @Column
    private UUID latestDocumentId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
