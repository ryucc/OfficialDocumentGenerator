package com.officialpapers.api.repository;

import com.officialpapers.api.entity.DocumentInstruction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DocumentInstructionRepository extends JpaRepository<DocumentInstruction, UUID> {
}
