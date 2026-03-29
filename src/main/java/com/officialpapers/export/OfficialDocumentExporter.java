package com.officialpapers.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OfficialDocumentExporter {

    private static final String TEMPLATE_RESOURCE = "/templates/important-person-invitation-template.docx";

    private final ObjectMapper objectMapper;
    private final OfficialDocumentDocxWriter docxWriter;

    public OfficialDocumentExporter() {
        this(new ObjectMapper(), new OfficialDocumentDocxWriter());
    }

    OfficialDocumentExporter(ObjectMapper objectMapper, OfficialDocumentDocxWriter docxWriter) {
        this.objectMapper = objectMapper;
        this.docxWriter = docxWriter;
    }

    OfficialDocumentData load(Path inputJson) throws IOException {
        try (var inputStream = Files.newInputStream(inputJson)) {
            OfficialDocumentData data = objectMapper.readValue(inputStream, OfficialDocumentData.class);
            validate(data);
            return data;
        }
    }

    public Path export(Path inputJson, Path outputDirectory) throws IOException {
        OfficialDocumentData data = load(inputJson);
        Files.createDirectories(outputDirectory);

        Path outputPath = outputDirectory.resolve(buildOutputFilename(data));
        try (
                InputStream templateStream = openTemplate();
                XWPFDocument document = new XWPFDocument(templateStream);
                OutputStream outputStream = Files.newOutputStream(outputPath)
        ) {
            docxWriter.write(document, data);
            document.write(outputStream);
        }

        return outputPath;
    }

    void validate(OfficialDocumentData data) {
        requireSection(data, "文件內容");
        requireNonBlank(data.outputFileBaseName(), "輸出檔名");
        requireNonBlank(data.title(), "標題");
        requireSection(data.applicationForm(), "申請表");
        requireSection(data.inviteeAttachment(), "附件一");
        requireSection(data.scheduleAttachment(), "附件二");
        requireNonBlank(data.applicationForm().applicationUnit(), "申請表.申請單位");
        requireNonBlank(data.applicationForm().applicationDate(), "申請表.申請日期");
        requireNonBlank(data.applicationForm().documentNumber(), "申請表.申請文號");
        requireNonEmpty(data.inviteeAttachment().entries(), "附件一.名單");
        requireNonEmpty(data.scheduleAttachment().flights(), "附件二.航班資訊");
        requireNonEmpty(data.scheduleAttachment().itinerary(), "附件二.行程表");
    }

    private String buildOutputFilename(OfficialDocumentData data) {
        String rawName = data.outputFileBaseName().trim() + ".docx";
        return rawName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private InputStream openTemplate() {
        InputStream templateStream = OfficialDocumentExporter.class.getResourceAsStream(TEMPLATE_RESOURCE);
        if (templateStream == null) {
            throw new IllegalStateException("Template not found on classpath: " + TEMPLATE_RESOURCE);
        }
        return templateStream;
    }

    private void requireSection(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private void requireNonEmpty(List<?> values, String fieldName) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }
}
