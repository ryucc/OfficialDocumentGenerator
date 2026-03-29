package com.officialpapers.export;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OfficialDocumentExporter {

    private final ObjectMapper objectMapper;
    private final OfficialDocumentGenerator docxGenerator;
    private final OfficialDocumentGenerator pdfGenerator;

    public OfficialDocumentExporter() {
        this(new ObjectMapper(), OfficialDocumentGeneratorConfig.defaultConfig());
    }

    private OfficialDocumentExporter(ObjectMapper objectMapper, OfficialDocumentGeneratorConfig config) {
        this(
                objectMapper,
                new OfficialDocumentDocxGenerator(config),
                new OfficialDocumentPdfGenerator(
                        new OfficialDocumentDocxGenerator(config),
                        new LibreOfficeDocxToPdfConverter()
                )
        );
    }

    OfficialDocumentExporter(
            ObjectMapper objectMapper,
            OfficialDocumentGenerator docxGenerator,
            OfficialDocumentGenerator pdfGenerator
    ) {
        this.objectMapper = objectMapper;
        this.docxGenerator = docxGenerator;
        this.pdfGenerator = pdfGenerator;
    }

    OfficialDocumentData load(Path inputJson) throws IOException {
        try (var inputStream = Files.newInputStream(inputJson)) {
            OfficialDocumentData data = objectMapper.readValue(inputStream, OfficialDocumentData.class);
            validate(data);
            return data;
        }
    }

    public Path exportDocx(Path inputJson, Path outputDirectory) throws IOException {
        return export(inputJson, outputDirectory, docxGenerator);
    }

    public Path exportPdf(Path inputJson, Path outputDirectory) throws IOException {
        return export(inputJson, outputDirectory, pdfGenerator);
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

    private void requireSection(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private Path export(Path inputJson, Path outputDirectory, OfficialDocumentGenerator generator) throws IOException {
        OfficialDocumentData data = load(inputJson);
        Files.createDirectories(outputDirectory);

        GeneratedFile generatedFile = generator.generate(data);
        Path outputPath = outputDirectory.resolve(generatedFile.fileName());
        Files.write(outputPath, generatedFile.bytes());

        return outputPath;
    }

    private void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }

    private void requireNonEmpty(List<?> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Missing required field: " + fieldName);
        }
    }
}
