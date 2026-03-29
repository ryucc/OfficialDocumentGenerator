package com.officialpapers.export;

import java.nio.file.Path;

public final class OfficialDocumentExporterCli {

    private OfficialDocumentExporterCli() {
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path inputJson = resolvePath(
                projectRoot,
                System.getProperty("officialDocument.inputJson", "sample data/123義大利網紅邀訪案.json")
        );
        Path outputDirectory = resolvePath(
                projectRoot,
                System.getProperty("officialDocument.outputDir", "writeTest")
        );

        try {
            Path outputPath = new OfficialDocumentExporter().export(inputJson, outputDirectory);
            System.out.println("Generated official document: " + outputPath.toAbsolutePath());
        } catch (Exception exception) {
            System.err.println("Failed to export official document: " + exception.getMessage());
            throw exception;
        }
    }

    private static Path resolvePath(Path projectRoot, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : projectRoot.resolve(path).normalize();
    }
}
