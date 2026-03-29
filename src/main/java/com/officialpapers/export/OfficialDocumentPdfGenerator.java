package com.officialpapers.export;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

class OfficialDocumentPdfGenerator implements OfficialDocumentGenerator {

    private final OfficialDocumentGenerator docxGenerator;
    private final DocxToPdfConverter converter;

    OfficialDocumentPdfGenerator(OfficialDocumentGenerator docxGenerator, DocxToPdfConverter converter) {
        this.docxGenerator = docxGenerator;
        this.converter = converter;
    }

    @Override
    public GeneratedFile generate(OfficialDocumentData data) throws IOException {
        GeneratedFile generatedDocx = docxGenerator.generate(data);
        Path workingDirectory = Files.createTempDirectory("official-document-pdf-");

        try {
            Path docxPath = workingDirectory.resolve(generatedDocx.fileName());
            Files.write(docxPath, generatedDocx.bytes());

            Path generatedPdfPath = converter.convert(docxPath, workingDirectory);
            return new GeneratedFile(
                    replaceExtension(generatedDocx.fileName(), ".pdf"),
                    Files.readAllBytes(generatedPdfPath)
            );
        } finally {
            deleteRecursively(workingDirectory);
        }
    }

    private String replaceExtension(String fileName, String extension) {
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return fileName + extension;
        }
        return fileName.substring(0, extensionSeparator) + extension;
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }
}
