package com.officialpapers.export;

import java.nio.file.Path;

public final class OfficialDocumentExporterCli {

    private static final String INPUT_JSON_PROPERTY = "officialDocument.inputJson";
    private static final String OUTPUT_DIR_PROPERTY = "officialDocument.outputDir";
    private static final String DEFAULT_OUTPUT_DIRECTORY = "build/official-documents";

    private OfficialDocumentExporterCli() {
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path inputJson = resolvePath(projectRoot, requireProperty(INPUT_JSON_PROPERTY));
        Path outputDirectory = resolvePath(
                projectRoot,
                System.getProperty(OUTPUT_DIR_PROPERTY, DEFAULT_OUTPUT_DIRECTORY)
        );

        try {
            Path outputPath = new OfficialDocumentExporter().export(inputJson, outputDirectory);
            System.out.println("Generated official document: " + outputPath.toAbsolutePath());
        } catch (Exception exception) {
            System.err.println("Failed to export official document: " + exception.getMessage());
            throw exception;
        }
    }

    private static String requireProperty(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required system property: " + propertyName
                            + ". Pass -PinputJson=/path/to/document.json to Gradle."
            );
        }
        return value;
    }

    private static Path resolvePath(Path projectRoot, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : projectRoot.resolve(path).normalize();
    }
}
