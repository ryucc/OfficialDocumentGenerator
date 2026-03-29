package com.officialpapers.api.service;

import com.officialpapers.api.dto.DocumentDto;
import com.officialpapers.api.dto.DocumentExportResponse;
import com.officialpapers.api.dto.ExportDocumentRequest;
import com.officialpapers.api.entity.Document;
import com.officialpapers.api.enums.DocumentFormat;
import com.officialpapers.api.enums.DocumentStatus;
import com.officialpapers.api.mapper.DocumentMapper;
import com.officialpapers.api.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper documentMapper;

    @Transactional
    public DocumentExportResponse exportDocument(UUID emailEventId, ExportDocumentRequest request) {
        Document document = new Document();
        document.setEmailEventId(emailEventId);
        document.setTitle(request.getTitle() != null ? request.getTitle() : "Generated Document");
        document.setFormat(request.getFormat() != null ? request.getFormat() : DocumentFormat.PDF);
        document.setStatus(DocumentStatus.GENERATING);
        document.setVersion(1);

        // TODO: Integrate actual document generation logic
        document.setStatus(DocumentStatus.READY);
        document.setDownloadUrl("/api/v1/documents/" + document.getId() + "/download");

        document = documentRepository.save(document);

        DocumentExportResponse response = new DocumentExportResponse();
        response.setDocument(documentMapper.toDto(document));
        return response;
    }

    @Transactional(readOnly = true)
    public Optional<DocumentDto> getDocument(UUID id) {
        return documentRepository.findById(id)
                .map(documentMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<byte[]> downloadDocument(UUID id) {
        // TODO: Implement actual document download logic
        return Optional.of("Document content placeholder".getBytes());
    }
}
