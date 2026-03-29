package com.officialpapers.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Generates complete DOCX files from OfficialDocumentData.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Template resource management</li>
 *   <li>Document lifecycle (load → populate → serialize)</li>
 *   <li>I/O operations</li>
 *   <li>File metadata (filename generation)</li>
 *   <li>Orchestration (delegates data writing to Writer)</li>
 * </ul>
 *
 * <p>Does NOT handle:
 * <ul>
 *   <li>Document structure manipulation</li>
 *   <li>Content writing or formatting</li>
 *   <li>POI implementation details</li>
 * </ul>
 */
class OfficialDocumentDocxGenerator {

    private final OfficialDocumentGeneratorConfig config;
    private final OfficialDocumentDocxWriter writer;

    OfficialDocumentDocxGenerator(
            OfficialDocumentGeneratorConfig config,
            OfficialDocumentDocxWriter writer) {
        this.config = config;
        this.writer = writer;
    }

    /**
     * Generates a complete DOCX file from data.
     *
     * <p>Manages the full lifecycle:
     * <ol>
     *   <li>Load template from classpath</li>
     *   <li>Create XWPFDocument from template</li>
     *   <li>Delegate data writing to Writer</li>
     *   <li>Serialize document to byte array</li>
     *   <li>Build file metadata</li>
     *   <li>Return complete GeneratedFile</li>
     * </ol>
     *
     * @param data the data to generate a document from
     * @return a GeneratedFile containing filename and serialized bytes
     * @throws IOException if template loading or serialization fails
     */
    GeneratedFile generate(OfficialDocumentData data) throws IOException {
        // Step 1: Load template (I/O)
        try (InputStream templateStream = openTemplate()) {

            // Step 2: Create document from template
            try (XWPFDocument document = new XWPFDocument(templateStream);
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                // Step 3: Delegate data writing to Writer
                writer.write(document, data);

                // Step 4: Serialize to bytes (I/O)
                document.write(outputStream);

                // Step 5: Build file metadata
                String fileName = buildOutputFilename(data);

                // Step 6: Return complete file
                return new GeneratedFile(fileName, outputStream.toByteArray());
            }
        }
    }

    private InputStream openTemplate() {
        InputStream templateStream = OfficialDocumentDocxGenerator.class.getResourceAsStream(config.templateResourcePath());
        if (templateStream == null) {
            throw new IllegalStateException("Template not found on classpath: " + config.templateResourcePath());
        }
        return templateStream;
    }

    private String buildOutputFilename(OfficialDocumentData data) {
        String rawName = data.outputFileBaseName().trim() + ".docx";
        return rawName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
