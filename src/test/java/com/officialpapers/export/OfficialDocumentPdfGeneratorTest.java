package com.officialpapers.export;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfficialDocumentPdfGeneratorTest {

    private final OfficialDocumentData sampleData = new OfficialDocumentData("sample", "title", null, null, null);

    @Test
    void generateUsesDocxGeneratorAndConverterAndReplacesExtension() throws Exception {
        byte[] docxBytes = "docx-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] pdfBytes = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        AtomicReference<Path> observedDocxPath = new AtomicReference<>();
        AtomicReference<Path> observedWorkingDirectory = new AtomicReference<>();
        AtomicReference<byte[]> observedDocxBytes = new AtomicReference<>();

        OfficialDocumentPdfGenerator generator = new OfficialDocumentPdfGenerator(
                data -> new GeneratedFile("test-output.docx", docxBytes),
                (docxPath, outputDirectory) -> {
                    observedDocxPath.set(docxPath);
                    observedWorkingDirectory.set(outputDirectory);
                    observedDocxBytes.set(Files.readAllBytes(docxPath));

                    Path pdfPath = outputDirectory.resolve("converted.pdf");
                    Files.write(pdfPath, pdfBytes);
                    return pdfPath;
                }
        );

        GeneratedFile generatedPdf = generator.generate(sampleData);

        assertEquals("test-output.pdf", generatedPdf.fileName());
        assertArrayEquals(pdfBytes, generatedPdf.bytes());
        assertEquals("test-output.docx", observedDocxPath.get().getFileName().toString());
        assertArrayEquals(docxBytes, observedDocxBytes.get());
        assertFalse(Files.exists(observedWorkingDirectory.get()));
    }

    @Test
    void generatePropagatesConverterFailure() {
        OfficialDocumentPdfGenerator generator = new OfficialDocumentPdfGenerator(
                data -> new GeneratedFile("test-output.docx", new byte[] {1, 2, 3}),
                (docxPath, outputDirectory) -> {
                    throw new IllegalStateException("conversion failed");
                }
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> generator.generate(sampleData)
        );

        assertEquals("conversion failed", exception.getMessage());
    }
}
