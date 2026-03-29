package com.officialpapers.export;

import java.nio.file.Path;

public class OfficialDocumentDocxExporterCli {

    private OfficialDocumentDocxExporterCli() {
    }

    public static void main(String[] args) throws Exception {
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        Path inputJson = OfficialDocumentExportCliSupport.resolvePath(
                projectRoot,
                OfficialDocumentExportCliSupport.requireProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY)
        );
        Path outputDirectory = OfficialDocumentExportCliSupport.resolvePath(
                projectRoot,
                System.getProperty(
                        OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY,
                        OfficialDocumentExportCliSupport.DEFAULT_OUTPUT_DIRECTORY
                )
        );

        try {
            Path outputPath = new OfficialDocumentExporter().exportDocx(inputJson, outputDirectory);
            System.out.println("Generated official DOCX document: " + outputPath.toAbsolutePath());
        } catch (Exception exception) {
            System.err.println("Failed to export official DOCX document: " + exception.getMessage());
            throw exception;
        }
    }
}
