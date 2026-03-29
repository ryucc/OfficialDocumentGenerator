package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.officialpapers.api.generated.model.ApiError;
import com.officialpapers.api.generated.model.CreateSampleDocumentRequest;
import com.officialpapers.api.generated.model.CreateSampleDocumentResponse;
import com.officialpapers.api.generated.model.SampleDocument;
import com.officialpapers.api.generated.model.SampleDocumentListResponse;
import com.officialpapers.api.generated.model.SampleDocumentStatus;
import com.officialpapers.domain.CreateUploadedDocumentCommand;
import com.officialpapers.domain.CreatedUpload;
import com.officialpapers.domain.UploadedDocument;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Singleton
public class SampleDocumentApiMapper {

    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Headers", "Content-Type,Authorization",
            "Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS",
            "Content-Type", "application/json"
    );

    @Inject
    public SampleDocumentApiMapper() {
    }

    public CreateUploadedDocumentCommand toDomain(CreateSampleDocumentRequest request) {
        return new CreateUploadedDocumentCommand(
                request.getFilename(),
                request.getContentType(),
                request.getSizeBytes()
        );
    }

    public SampleDocument toApi(UploadedDocument document) {
        return new SampleDocument()
                .id(UUID.fromString(document.id()))
                .filename(document.filename())
                .contentType(document.contentType())
                .sizeBytes(document.sizeBytes())
                .status(SampleDocumentStatus.fromValue(document.status().name()))
                .createdAt(OffsetDateTime.parse(document.createdAt()))
                .updatedAt(OffsetDateTime.parse(document.updatedAt()));
    }

    public SampleDocumentListResponse toApiList(List<UploadedDocument> documents) {
        return new SampleDocumentListResponse().items(
                documents.stream()
                        .map(this::toApi)
                        .toList()
        );
    }

    public CreateSampleDocumentResponse toApi(CreatedUpload createdUpload) {
        return new CreateSampleDocumentResponse()
                .document(toApi(createdUpload.document()))
                .upload(new com.officialpapers.api.generated.model.UploadTarget()
                        .uploadUrl(URI.create(createdUpload.upload().uploadUrl()))
                        .uploadMethod(createdUpload.upload().uploadMethod())
                        .uploadHeaders(createdUpload.upload().uploadHeaders())
                        .expiresAt(OffsetDateTime.parse(createdUpload.upload().expiresAt())));
    }

    public com.officialpapers.api.generated.model.DownloadTarget toApi(com.officialpapers.domain.DownloadTarget downloadTarget) {
        return new com.officialpapers.api.generated.model.DownloadTarget()
                .downloadUrl(URI.create(downloadTarget.downloadUrl()))
                .downloadMethod(downloadTarget.downloadMethod())
                .expiresAt(OffsetDateTime.parse(downloadTarget.expiresAt()));
    }

    public ApiError toApiError(String code, String message) {
        return new ApiError()
                .code(code)
                .message(message);
    }

    public APIGatewayProxyResponseEvent toNoContentResponse() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(204);
        response.setHeaders(CORS_HEADERS);
        return response;
    }

    public APIGatewayProxyResponseEvent toOptionsResponse() {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(204);
        response.setHeaders(CORS_HEADERS);
        return response;
    }

    public Map<String, String> corsHeaders() {
        return CORS_HEADERS;
    }
}
