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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipFile;

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
    void exportCreatesDocxMatchingTemplateTableLayout() throws Exception {
        Path outputFile = exporter.export(sampleJsonPath, tempDir);

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
    void exportWritesEveryInviteeAndFlightWhenCountExceedsTemplateRows() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode invitees = (ObjectNode) root.get("附件一");
            var nameList = invitees.putArray("名單");
            String[][] people = {
                    {"1", "Stella Lopez Frayle", "F", "Sales Agent", "Explorer Travel"},
                    {"2", "Toni Reisen", "M", "Manager Special Tours", "Enjoy Reisen"},
                    {"3", "Nils Hennes Over", "M", "Product Manager Asia", "Journaway"},
                    {"4", "Patricia Ketteler", "F", "Head of Marketing and PR", "Karawane Reisen"},
            };
            for (String[] p : people) {
                ObjectNode entry = nameList.addObject();
                entry.put("編號", p[0]);
                entry.put("姓名", p[1]);
                entry.put("性別", p[2]);
                entry.put("職稱", p[3]);
                entry.put("單位名稱", p[4]);
                entry.put("邀訪紀錄", "否");
                entry.put("成效評估", "");
                entry.put("備註", "");
            }

            ObjectNode schedule = (ObjectNode) root.get("附件二");
            var flightList = schedule.putArray("航班資訊");
            String[][] flights = {
                    {"FRA→TPE", "CI62", "2024.05.26 10:40", "2024.05.27 06:10"},
                    {"MUN→IST→TPE", "TK24", "2024.05.26 19:00", "2024.05.27 17:55"},
                    {"VIE→TPE", "CI64", "2024.05.26 08:50", "2024.05.27 06:10"},
            };
            for (String[] f : flights) {
                ObjectNode entry = flightList.addObject();
                entry.put("航班資訊", f[0]);
                entry.put("班機號碼", f[1]);
                entry.put("出發時間", f[2]);
                entry.put("抵達時間", f[3]);
            }
        });

        Path outputFile = exporter.export(inputJson, tempDir);

        try (XWPFDocument document = new XWPFDocument(Files.newInputStream(outputFile))) {
            assertEquals("Stella Lopez Frayle", joinedCellText(document, 1, 2, 1));
            assertEquals("Toni Reisen", joinedCellText(document, 1, 3, 1));
            assertEquals("Nils Hennes Over", joinedCellText(document, 1, 4, 1));
            assertEquals("Patricia Ketteler", joinedCellText(document, 1, 5, 1));

            assertEquals("CI62", joinedCellText(document, 2, 1, 1));
            assertEquals("TK24", joinedCellText(document, 2, 2, 1));
            assertEquals("CI64", joinedCellText(document, 2, 3, 1));
        }
    }

    @Test
    void exportRejectsMissingApplicationFormSection() throws Exception {
        Path inputJson = writeJson(root -> root.remove("申請表"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(inputJson, tempDir)
        );

        assertEquals("Missing required field: 申請表", exception.getMessage());
    }

    @Test
    void exportRejectsBlankOutputFileName() throws Exception {
        Path inputJson = writeJson(root -> root.put("輸出檔名", ""));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(inputJson, tempDir)
        );

        assertEquals("Missing required field: 輸出檔名", exception.getMessage());
    }

    @Test
    void exportRejectsEmptyInviteeAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件一");
            attachment.putArray("名單");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件一.名單", exception.getMessage());
    }

    @Test
    void exportRejectsNullInviteeAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件一");
            attachment.putNull("名單");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件一.名單", exception.getMessage());
    }

    @Test
    void exportRejectsEmptyItineraryAttachment() throws Exception {
        Path inputJson = writeJson(root -> {
            ObjectNode attachment = (ObjectNode) root.get("附件二");
            attachment.putArray("行程表");
        });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exporter.export(inputJson, tempDir)
        );

        assertEquals("Missing required field: 附件二.行程表", exception.getMessage());
    }

    @Test
    void cliRequiresExplicitInputJsonProperty() {
        String originalInputJson = System.getProperty("officialDocument.inputJson");
        String originalOutputDir = System.getProperty("officialDocument.outputDir");

        System.clearProperty("officialDocument.inputJson");
        System.clearProperty("officialDocument.outputDir");

        try {
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> OfficialDocumentExporterCli.main(new String[0])
            );

            assertEquals(
                    "Missing required system property: officialDocument.inputJson. Pass -PinputJson=/path/to/document.json to Gradle.",
                    exception.getMessage()
            );
        } finally {
            restoreSystemProperty("officialDocument.inputJson", originalInputJson);
            restoreSystemProperty("officialDocument.outputDir", originalOutputDir);
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
}
