package com.officialpapers.export;

import java.nio.file.Path;

final class OfficialDocumentExportCliSupport {

    static final String INPUT_JSON_PROPERTY = "officialDocument.inputJson";
    static final String OUTPUT_DIR_PROPERTY = "officialDocument.outputDir";
    static final String DEFAULT_OUTPUT_DIRECTORY = "build/official-documents";

    private OfficialDocumentExportCliSupport() {
    }

    static String requireProperty(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required system property: " + propertyName
                            + ". Pass -PinputJson=/path/to/document.json to Gradle."
            );
        }
        return value;
    }

    static Path resolvePath(Path projectRoot, String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : projectRoot.resolve(path).normalize();
    }
}
