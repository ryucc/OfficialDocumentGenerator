package com.officialpapers.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfficialDocumentExporterTest {

    private final OfficialDocumentExporter exporter = new OfficialDocumentExporter();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Path sampleJsonPath = resourcePath("exporter/sample-data/123義大利網紅邀訪案.json");

    @TempDir
    Path tempDir;

    @Test
    void loadParsesTemplateDrivenJsonSchema() throws Exception {
        OfficialDocumentData data = exporter.load(sampleJsonPath);

        assertEquals("123義大利網紅邀訪案", data.outputFileBaseName());
        assertEquals("交通部觀光署駐外辦事處重要人士邀訪申請表", data.title());
        assertEquals("駐法蘭克福辦事處", data.applicationForm().applicationUnit());
        assertEquals(2, data.inviteeAttachment().entries().size());
        assertEquals(2, data.scheduleAttachment().flights().size());
        assertEquals(6, data.scheduleAttachment().itinerary().size());
    }

    @Test
    void exportDocxCreatesDocxMatchingTemplateTableLayout() throws Exception {
        Path outputFile = exporter.exportDocx(sampleJsonPath, tempDir);

        assertEquals("123義大利網紅邀訪案.docx", outputFile.getFileName().toString());
        assertTrue(Files.exists(outputFile));

        try (
                XWPFDocument document = new XWPFDocument(Files.newInputStream(outputFile));
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)
        ) {
            assertEquals(4, document.getTables().size());
            assertEquals(
                    "交通部觀光署駐外辦事處重要人士邀訪申請表",
                    document.getParagraphs().get(0).getText()
            );
            assertEquals("駐法蘭克福辦事處", joinedCellText(document, 0, 0, 1));
            assertEquals("義大利米蘭", joinedCellText(document, 0, 6, 4));
            assertEquals("Costantino della Gherardesca", joinedCellText(document, 1, 2, 1));
            assertEquals("BR096", joinedCellText(document, 2, 1, 1));
            assertTrue(joinedCellText(document, 3, 2, 2).contains("The Lalu"));
            assertTrue(extractor.getText().contains("附件一：邀訪名單"));
            assertTrue(extractor.getText().contains("附件二：預定行程表"));
            assertTrue(!extractor.getText().contains("Dear Josephine,"));
        }

        assertTrue(paragraphStartsOnNewPage(outputFile, "附件一：邀訪名單"));
        assertTrue(paragraphStartsOnNewPage(outputFile, "附件二：預定行程表"));
        assertEquals("tbl", previousBodyElementTypeForParagraph(outputFile, "附件一：邀訪名單"));
        assertEquals("tbl", previousBodyElementTypeForParagraph(outputFile, "附件二：預定行程表"));
    }

    @Test
    void exportPdfWritesGeneratedPdfBytesToOutputDirectory() throws Exception {
        byte[] expectedPdfBytes = "%PDF-test".getBytes(StandardCharsets.UTF_8);
        OfficialDocumentExporter pdfExporter = new OfficialDocumentExporter(
                objectMapper,
                data -> new GeneratedFile(data.outputFileBaseName() + ".docx", new byte[] {1, 2, 3}),
                data -> new GeneratedFile(data.outputFileBaseName() + ".pdf", expectedPdfBytes)
        );

        Path outputFile = pdfExporter.exportPdf(sampleJsonPath, tempDir);

        assertEquals("123義大利網紅邀訪案.pdf", outputFile.getFileName().toString());
        assertArrayEquals(expectedPdfBytes, Files.readAllBytes(outputFile));
    }

    @Test
    void exportDocxRejectsMissingApplicationFormSection() throws Exception {
        Path inputJson = writeJson(root -> root.remove("申請表"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.exportDocx(inputJson, tempDir)
        );

        assertEquals("Missing required field: 申請表", exception.getMessage());
    }

    @Test
    void exportDocxRejectsBlankOutputFileName() throws Exception {
        Path inputJson = writeJson(root -> root.put("輸出檔名", ""));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.exportDocx(inputJson, tempDir)
        );

        assertEquals("Missing required field: 輸出檔名", exception.getMessage());
    }

    @Test
    void exportDocxRejectsEmptyInviteeAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件一");
            attachment.putArray("名單");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.exportDocx(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件一.名單", exception.getMessage());
    }

    @Test
    void exportDocxRejectsNullInviteeAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件一");
            attachment.putNull("名單");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.exportDocx(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件一.名單", exception.getMessage());
    }

    @Test
    void exportDocxRejectsEmptyItineraryAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件二");
            attachment.putArray("行程表");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.exportDocx(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件二.行程表", exception.getMessage());
    }

    @Test
    void docxCliRequiresExplicitInputJsonProperty() {
        assertCliRequiresInputJsonProperty(() -> OfficialDocumentDocxExporterCli.main(new String[0]));
    }

    @Test
    void pdfCliRequiresExplicitInputJsonProperty() {
        assertCliRequiresInputJsonProperty(() -> OfficialDocumentPdfExporterCli.main(new String[0]));
    }

    @Test
    void pdfCliRejectsMissingLibreOfficeCommand() {
        String originalInputJson = System.getProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY);
        String originalOutputDir = System.getProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY);
        String originalSofficePath = System.getProperty(LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY);

        System.setProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY, sampleJsonPath.toString());
        System.setProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY, tempDir.toString());
        System.setProperty(
                LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY,
                tempDir.resolve("missing-soffice").toString()
        );

        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> OfficialDocumentPdfExporterCli.main(new String[0])
            );

            assertTrue(exception.getMessage().contains("LibreOffice CLI not found"));
        } finally {
            restoreSystemProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY, originalInputJson);
            restoreSystemProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY, originalOutputDir);
            restoreSystemProperty(LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY, originalSofficePath);
        }
    }

    private void assertCliRequiresInputJsonProperty(ThrowingRunnable cliInvocation) {
        String originalInputJson = System.getProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY);
        String originalOutputDir = System.getProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY);
        String originalSofficePath = System.getProperty(LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY);

        System.clearProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY);
        System.clearProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY);
        System.clearProperty(LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY);

        try {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, cliInvocation::run);

            assertEquals(
                    "Missing required system property: officialDocument.inputJson. Pass -PinputJson=/path/to/document.json to Gradle.",
                    exception.getMessage()
            );
        } finally {
            restoreSystemProperty(OfficialDocumentExportCliSupport.INPUT_JSON_PROPERTY, originalInputJson);
            restoreSystemProperty(OfficialDocumentExportCliSupport.OUTPUT_DIR_PROPERTY, originalOutputDir);
            restoreSystemProperty(LibreOfficeDocxToPdfConverter.SOFFICE_PATH_PROPERTY, originalSofficePath);
        }
    }

    private String joinedCellText(XWPFDocument document, int tableIndex, int rowIndex, int cellIndex) {
        return String.join(
                " | ",
                document.getTables()
                        .get(tableIndex)
                        .getRow(rowIndex)
                        .getCell(cellIndex)
                        .getParagraphs()
                        .stream()
                        .map(paragraph -> paragraph.getText().trim())
                        .filter(text -> !text.isEmpty())
                        .toList()
        );
    }

    private Path writeJson(JsonMutator mutator) throws IOException {
        ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(sampleJsonPath));
        mutator.mutate(root);

        Path inputJson = tempDir.resolve("fixture.json");
        Files.writeString(inputJson, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        return inputJson;
    }

    private Path resourcePath(String resourcePath) {
        try {
            return Path.of(Objects.requireNonNull(
                    getClass().getClassLoader().getResource(resourcePath),
                    "Missing test resource: " + resourcePath
            ).toURI());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to resolve test resource: " + resourcePath, exception);
        }
    }

    private void restoreSystemProperty(String propertyName, String value) {
        if (value == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, value);
        }
    }

    private boolean paragraphStartsOnNewPage(Path docxPath, String marker) throws Exception {
        Document document = openWordDocumentXml(docxPath);
        NodeList paragraphs = document.getElementsByTagNameNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                "p"
        );
        for (int index = 0; index < paragraphs.getLength(); index++) {
            Element paragraph = (Element) paragraphs.item(index);
            String text = paragraph.getTextContent();
            if (text != null && text.contains(marker)) {
                NodeList pageBreakNodes = paragraph.getElementsByTagNameNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
                        "pageBreakBefore"
                );
                return pageBreakNodes.getLength() > 0;
            }
        }
        return false;
    }

    private String previousBodyElementTypeForParagraph(Path docxPath, String marker) throws Exception {
        Document document = openWordDocumentXml(docxPath);
        NodeList bodyChildren = document.getDocumentElement()
                .getElementsByTagNameNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "body")
                .item(0)
                .getChildNodes();

        for (int index = 0; index < bodyChildren.getLength(); index++) {
            if (!(bodyChildren.item(index) instanceof Element element)) {
                continue;
            }
            if (!"p".equals(element.getLocalName())) {
                continue;
            }
            String text = element.getTextContent();
            if (text == null || !text.contains(marker)) {
                continue;
            }
            for (int previousIndex = index - 1; previousIndex >= 0; previousIndex--) {
                if (bodyChildren.item(previousIndex) instanceof Element previousElement) {
                    return previousElement.getLocalName();
                }
            }
            return "";
        }
        return "";
    }

    private Document openWordDocumentXml(Path docxPath) throws Exception {
        try (ZipFile zipFile = new ZipFile(docxPath.toFile())) {
            byte[] xmlBytes = zipFile.getInputStream(zipFile.getEntry("word/document.xml")).readAllBytes();
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xmlBytes));
        }
    }

    @FunctionalInterface
    private interface JsonMutator {
        void mutate(ObjectNode root);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
