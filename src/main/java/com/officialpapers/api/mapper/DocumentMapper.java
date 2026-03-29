package com.officialpapers.api.mapper;

import com.officialpapers.api.dto.DocumentDto;
import com.officialpapers.api.dto.DocumentSummaryDto;
import com.officialpapers.api.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentSummaryDto toSummaryDto(Document document) {
        if (document == null) return null;
        return new DocumentSummaryDto(
                document.getId(),
                document.getTitle(),
                document.getFormat(),
                document.getStatus(),
                document.getCreatedAt()
        );
    }

    public DocumentDto toDto(Document document) {
        if (document == null) return null;
        return new DocumentDto(
                document.getId(),
                document.getEmailEventId(),
                document.getTitle(),
                document.getFormat(),
                document.getStatus(),
                document.getVersion(),
                document.getDownloadUrl(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
