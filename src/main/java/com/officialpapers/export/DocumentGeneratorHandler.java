package com.officialpapers.export;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.Map;

/**
 * Lambda handler that generates a .docx file from OfficialDocumentData JSON.
 *
 * <p>Input event:
 * <pre>
 * {
 *   "projectId": "...",
 *   "documentData": { ... OfficialDocumentData JSON ... }
 * }
 * </pre>
 *
 * <p>Output:
 * <pre>
 * {
 *   "bucket": "...",
 *   "s3Key": "...",
 *   "filename": "..."
 * }
 * </pre>
 */
public class DocumentGeneratorHandler implements RequestHandler<Map<String, Object>, Map<String, String>> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OfficialDocumentDocxGenerator docxGenerator =
            new OfficialDocumentDocxGenerator(OfficialDocumentGeneratorConfig.defaultConfig());
    private final S3Client s3Client = S3Client.create();

    @Override
    public Map<String, String> handleRequest(Map<String, Object> event, Context context) {
        try {
            String projectId = (String) event.get("projectId");
            Object documentDataObj = event.get("documentData");

            if (projectId == null || projectId.isBlank()) {
                throw new IllegalArgumentException("projectId is required");
            }
            if (documentDataObj == null) {
                throw new IllegalArgumentException("documentData is required");
            }

            // Deserialize documentData to OfficialDocumentData
            String documentDataJson = objectMapper.writeValueAsString(documentDataObj);
            OfficialDocumentData data = objectMapper.readValue(documentDataJson, OfficialDocumentData.class);

            context.getLogger().log("Generating docx for project " + projectId);

            // Generate .docx
            GeneratedFile generatedFile = docxGenerator.generate(data);

            // Upload to S3
            String bucket = requireEnv("GENERATED_DOCUMENT_BUCKET");
            String s3Key = "generated-documents/" + projectId + "/" + generatedFile.fileName();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Key)
                            .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                            .build(),
                    RequestBody.fromBytes(generatedFile.bytes())
            );

            context.getLogger().log("Uploaded docx to s3://" + bucket + "/" + s3Key);

            return Map.of(
                    "bucket", bucket,
                    "s3Key", s3Key,
                    "filename", generatedFile.fileName()
            );

        } catch (Exception e) {
            context.getLogger().log("Error generating document: " + e.getMessage());
            throw new RuntimeException("Document generation failed: " + e.getMessage(), e);
        }
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
