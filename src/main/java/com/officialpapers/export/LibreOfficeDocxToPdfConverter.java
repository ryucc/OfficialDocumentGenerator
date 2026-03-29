package com.officialpapers.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class LibreOfficeDocxToPdfConverter implements DocxToPdfConverter {

    static final String SOFFICE_PATH_PROPERTY = "officialDocument.sofficePath";
    private static final String DEFAULT_SOFFICE_COMMAND = "soffice";

    private final String sofficeCommand;

    LibreOfficeDocxToPdfConverter() {
        this(System.getProperty(SOFFICE_PATH_PROPERTY, DEFAULT_SOFFICE_COMMAND));
    }

    LibreOfficeDocxToPdfConverter(String sofficeCommand) {
        this.sofficeCommand = sofficeCommand;
    }

    @Override
    public Path convert(Path docxPath, Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);

        String pdfFileName = replaceExtension(docxPath.getFileName().toString(), ".pdf");
        Path outputPdf = outputDirectory.resolve(pdfFileName);

        Process process;
        try {
            process = new ProcessBuilder(
                    List.of(
                            sofficeCommand,
                            "--headless",
                            "--convert-to",
                            "pdf",
                            "--outdir",
                            outputDirectory.toAbsolutePath().toString(),
                            docxPath.toAbsolutePath().toString()
                    )
            ).redirectErrorStream(true).start();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "LibreOffice CLI not found. Install LibreOffice and ensure 'soffice' is on PATH, "
                            + "or set -D" + SOFFICE_PATH_PROPERTY + "=/path/to/soffice.",
                    exception
            );
        }

        String processOutput;
        try {
            processOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IllegalStateException(
                        "LibreOffice PDF conversion failed with exit code " + exitCode
                                + (processOutput.isBlank() ? "" : ": " + processOutput.trim())
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for LibreOffice PDF conversion", exception);
        }

        if (!Files.exists(outputPdf)) {
            throw new IllegalStateException(
                    "LibreOffice PDF conversion did not produce output file: " + outputPdf.toAbsolutePath()
            );
        }

        return outputPdf;
    }

    private String replaceExtension(String fileName, String extension) {
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0) {
            return fileName + extension;
        }
        return fileName.substring(0, extensionSeparator) + extension;
    }
}
