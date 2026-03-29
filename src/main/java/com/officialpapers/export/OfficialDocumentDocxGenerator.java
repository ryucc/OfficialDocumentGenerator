package com.officialpapers.export;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates complete DOCX files from OfficialDocumentData.
 *
 * <p>Manages the full document lifecycle: template loading, data population,
 * serialization, and file generation.
 */
class OfficialDocumentDocxGenerator {

    private final OfficialDocumentGeneratorConfig config;

    OfficialDocumentDocxGenerator(OfficialDocumentGeneratorConfig config) {
        this.config = config;
    }

    /**
     * Generates a complete DOCX file from data.
     *
     * @param data the data to generate a document from
     * @return a GeneratedFile containing filename and serialized bytes
     * @throws IOException if template loading or serialization fails
     */
    GeneratedFile generate(OfficialDocumentData data) throws IOException {
        try (XWPFDocument document = openDocument()) {
            writeDocument(document, data);
            return new GeneratedFile(buildOutputFilename(data), serializeDocument(document));
        }
    }

    private XWPFDocument openDocument() throws IOException {
        try (InputStream templateStream = openTemplate()) {
            return new XWPFDocument(templateStream);
        }
    }

    private void writeDocument(XWPFDocument document, OfficialDocumentData data) {
        prepareTemplate(document);
        HeadingSections headings = locateHeadings(document);
        TableSections tables = locateTables(document);

        writeHeadings(headings, data);
        writeTables(tables, data);
    }

    private void prepareTemplate(XWPFDocument document) {
        trimTemplate(document);
        removeBlankParagraphsBeforeHeading(document, config.attachmentOneMarker());
        removeBlankParagraphsBeforeHeading(document, config.attachmentTwoMarker());
    }

    private HeadingSections locateHeadings(XWPFDocument document) {
        return new HeadingSections(
                requireParagraph(document, 0),
                requireParagraphContaining(document, config.attachmentOneMarker()),
                requireParagraphContaining(document, config.attachmentTwoMarker())
        );
    }

    private void writeHeadings(HeadingSections headings, OfficialDocumentData data) {
        setParagraphText(headings.title(), safeText(data.title()));
        setPageBreakBefore(headings.attachmentOne());
        setParagraphText(
                headings.attachmentOne(),
                valueOrDefault(data.inviteeAttachment().heading(), config.defaultAttachmentOneHeading())
        );
        setPageBreakBefore(headings.attachmentTwo());
        setParagraphText(
                headings.attachmentTwo(),
                valueOrDefault(data.scheduleAttachment().heading(), config.defaultAttachmentTwoHeading())
        );
    }

    private TableSections locateTables(XWPFDocument document) {
        return new TableSections(
                requireTable(document, 0),
                requireTable(document, 1),
                requireTable(document, 2),
                requireTable(document, 3)
        );
    }

    private void writeTables(TableSections tables, OfficialDocumentData data) {
        fillMainApplicationTable(tables.application(), data.applicationForm());
        fillInviteeTable(tables.invitees(), data.inviteeAttachment().entries());
        fillFlightTable(tables.flights(), data.scheduleAttachment().flights());
        fillItineraryTable(tables.itinerary(), data.scheduleAttachment().itinerary());
    }

    private byte[] serializeDocument(XWPFDocument document) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.write(outputStream);
            return outputStream.toByteArray();
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

    private void trimTemplate(XWPFDocument document) {
        while (document.getBodyElements().size() > config.bodyElementsToKeep()) {
            document.removeBodyElement(document.getBodyElements().size() - 1);
        }
    }

    private void removeBlankParagraphsBeforeHeading(XWPFDocument document, String marker) {
        XWPFParagraph heading = requireParagraphContaining(document, marker);
        int headingIndex = document.getPosOfParagraph(heading);

        while (headingIndex > 0) {
            IBodyElement previousElement = document.getBodyElements().get(headingIndex - 1);
            if (!(previousElement instanceof XWPFParagraph previousParagraph)) {
                break;
            }
            if (!previousParagraph.getText().isBlank()) {
                break;
            }
            document.removeBodyElement(headingIndex - 1);
            headingIndex--;
        }
    }

    private void fillMainApplicationTable(XWPFTable table, OfficialDocumentData.ApplicationForm form) {
        setCellLines(table.getRow(0).getCell(1), List.of(safeText(form.applicationUnit())));
        setCellLines(table.getRow(0).getCell(3), List.of(safeText(form.applicationDate())));
        setCellLines(table.getRow(1).getCell(3), List.of(safeText(form.documentNumber())));
        setCellLines(table.getRow(2).getCell(2), List.of(safeText(form.inviteeTypeLine())));
        setCellLines(table.getRow(3).getCell(2), List.of(safeText(form.reason())));
        setCellLines(table.getRow(4).getCell(2), List.of(safeText(form.marketAttribute())));
        setCellLines(table.getRow(5).getCell(2), safeLines(form.inviteMethodLines()));
        setCellLines(table.getRow(6).getCell(2), List.of(safeText(form.headcountText())));
        setCellLines(table.getRow(6).getCell(4), List.of(safeText(form.departureLocation())));
        setCellLines(table.getRow(7).getCell(2), List.of(safeText(form.timeRangeText())));
        setCellLines(table.getRow(8).getCell(2), List.of(safeText(form.annualPlanText())));
        setCellLines(table.getRow(10).getCell(1), safeLines(form.applicantFundingLines()));
        setCellLines(table.getRow(10).getCell(2), safeLines(form.applicantEstimateLines()));
        setCellLines(table.getRow(10).getCell(3), List.of(safeText(form.applicantShareRatio())));
        setCellLines(table.getRow(11).getCell(1), safeLines(form.otherFundingLines()));
        setCellLines(table.getRow(11).getCell(2), safeLines(form.otherEstimateLines()));
        setCellLines(table.getRow(11).getCell(3), List.of(safeText(form.otherShareRatio())));
        setCellLines(table.getRow(12).getCell(1), safeLines(form.requestedSupportLines()));
        setCellLines(table.getRow(12).getCell(2), safeLines(form.requestedEstimateLines()));
        setCellLines(table.getRow(12).getCell(3), List.of(safeText(form.requestedShareRatio())));
        setCellLines(table.getRow(13).getCell(2), List.of(safeText(form.overHalfReason())));
        setCellLines(table.getRow(14).getCell(1), safeLines(form.expectedBenefitLines()));
        setCellLines(table.getRow(15).getCell(1), List.of(safeText(form.writeoffDate())));
        setCellLines(table.getRow(15).getCell(3), List.of(safeText(form.resultReportDate())));
        setCellLines(table.getRow(16).getCell(1), safeLines(form.noteLines()));
        setCellLines(table.getRow(17).getCell(1), safeLines(form.attachmentLines()));
    }

    private void fillInviteeTable(XWPFTable table, List<OfficialDocumentData.InviteeEntry> entries) {
        resizeDataRows(table, 2, entries.size(), 2);

        for (int index = 0; index < entries.size(); index++) {
            OfficialDocumentData.InviteeEntry entry = entries.get(index);
            XWPFTableRow row = table.getRow(index + 2);

            setCellLines(row.getCell(0), List.of(safeText(entry.serialNumber())));
            setCellLines(row.getCell(1), List.of(safeText(entry.name())));
            setCellLines(row.getCell(2), List.of(safeText(entry.gender())));
            setCellLines(row.getCell(3), List.of(safeText(entry.jobTitle())));
            setCellLines(row.getCell(4), List.of(safeText(entry.organizationName())));
            setCellLines(row.getCell(5), List.of("否".equals(entry.inviteRecord()) ? "ˇ" : ""));
            setCellLines(row.getCell(6), List.of("是".equals(entry.inviteRecord()) ? "ˇ" : ""));
            setCellLines(row.getCell(7), List.of(safeText(entry.effectiveness())));
            setCellLines(row.getCell(8), List.of(safeText(entry.note())));
        }
    }

    private void fillFlightTable(XWPFTable table, List<OfficialDocumentData.FlightInfo> flights) {
        resizeDataRows(table, 1, flights.size(), 1);

        for (int index = 0; index < flights.size(); index++) {
            OfficialDocumentData.FlightInfo flight = flights.get(index);
            XWPFTableRow row = table.getRow(index + 1);

            setCellLines(row.getCell(0), List.of(safeText(flight.route())));
            setCellLines(row.getCell(1), List.of(safeText(flight.flightNumber())));
            setCellLines(row.getCell(2), List.of(safeText(flight.departureTime())));
            setCellLines(row.getCell(3), List.of(safeText(flight.arrivalTime())));
        }
    }

    private void fillItineraryTable(XWPFTable table, List<OfficialDocumentData.ItineraryRow> itinerary) {
        resizeDataRows(table, 1, itinerary.size(), 1);

        for (int index = 0; index < itinerary.size(); index++) {
            OfficialDocumentData.ItineraryRow item = itinerary.get(index);
            XWPFTableRow row = table.getRow(index + 1);

            setCellLines(row.getCell(0), safeLines(item.dateLines()));
            setCellLines(row.getCell(1), safeLines(item.itineraryLines()));
            setCellLines(row.getCell(2), safeLines(item.accommodationLines()));
        }
    }

    private void resizeDataRows(XWPFTable table, int startRowIndex, int desiredRowCount, int templateRowIndex) {
        while (table.getNumberOfRows() > startRowIndex + desiredRowCount) {
            table.removeRow(table.getNumberOfRows() - 1);
        }

        XWPFTableRow templateRow = table.getRow(templateRowIndex);
        while (table.getNumberOfRows() < startRowIndex + desiredRowCount) {
            table.addRow(cloneRow(table, templateRow));
        }
    }

    private XWPFTableRow cloneRow(XWPFTable table, XWPFTableRow templateRow) {
        return new XWPFTableRow((CTRow) templateRow.getCtRow().copy(), table);
    }

    private XWPFParagraph requireParagraph(XWPFDocument document, int bodyElementIndex) {
        IBodyElement bodyElement = document.getBodyElements().get(bodyElementIndex);
        if (bodyElement instanceof XWPFParagraph paragraph) {
            return paragraph;
        }
        throw new IllegalStateException("Expected paragraph at body element index " + bodyElementIndex);
    }

    private XWPFParagraph requireParagraphContaining(XWPFDocument document, String marker) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            if (paragraph.getText().contains(marker)) {
                return paragraph;
            }
        }
        throw new IllegalStateException("Expected paragraph containing marker: " + marker);
    }

    private XWPFTable requireTable(XWPFDocument document, int tableIndex) {
        if (document.getTables().size() <= tableIndex) {
            throw new IllegalStateException("Expected table at index " + tableIndex);
        }
        return document.getTables().get(tableIndex);
    }

    private void setParagraphText(XWPFParagraph paragraph, String text) {
        CTRPr templateRunProperties = copyRunProperties(paragraph);
        clearRuns(paragraph);

        XWPFRun run = paragraph.createRun();
        if (templateRunProperties != null) {
            run.getCTR().setRPr((CTRPr) templateRunProperties.copy());
        }
        run.setText(text);
    }

    private void setPageBreakBefore(XWPFParagraph paragraph) {
        paragraph.setPageBreak(true);
    }

    private void setCellLines(XWPFTableCell cell, List<String> lines) {
        List<String> resolvedLines = safeLines(lines);
        List<XWPFParagraph> paragraphs = new ArrayList<>(cell.getParagraphs());
        CTPPr templateParagraphProperties = paragraphs.isEmpty()
                ? null
                : copyParagraphProperties(paragraphs.get(0));
        CTRPr templateRunProperties = copyRunProperties(cell);

        while (cell.getParagraphs().size() > resolvedLines.size()) {
            cell.removeParagraph(cell.getParagraphs().size() - 1);
        }
        while (cell.getParagraphs().size() < resolvedLines.size()) {
            XWPFParagraph paragraph = cell.addParagraph();
            if (templateParagraphProperties != null) {
                paragraph.getCTP().setPPr((CTPPr) templateParagraphProperties.copy());
            }
        }

        for (int index = 0; index < resolvedLines.size(); index++) {
            XWPFParagraph paragraph = cell.getParagraphs().get(index);
            if (templateParagraphProperties != null && !paragraph.getCTP().isSetPPr()) {
                paragraph.getCTP().setPPr((CTPPr) templateParagraphProperties.copy());
            }
            clearRuns(paragraph);
            XWPFRun run = paragraph.createRun();
            if (templateRunProperties != null) {
                run.getCTR().setRPr((CTRPr) templateRunProperties.copy());
            }
            run.setText(resolvedLines.get(index));
        }
    }

    private void clearRuns(XWPFParagraph paragraph) {
        for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) {
            paragraph.removeRun(index);
        }
    }

    private CTRPr copyRunProperties(XWPFParagraph paragraph) {
        for (XWPFRun run : paragraph.getRuns()) {
            if (run.getCTR().isSetRPr()) {
                return (CTRPr) run.getCTR().getRPr().copy();
            }
        }
        return null;
    }

    private CTRPr copyRunProperties(XWPFTableCell cell) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            CTRPr paragraphProperties = copyRunProperties(paragraph);
            if (paragraphProperties != null) {
                return paragraphProperties;
            }
        }
        return null;
    }

    private CTPPr copyParagraphProperties(XWPFParagraph paragraph) {
        if (paragraph.getCTP().isSetPPr()) {
            return (CTPPr) paragraph.getCTP().getPPr().copy();
        }
        return null;
    }

    private List<String> safeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("");
        }
        List<String> resolved = new ArrayList<>();
        for (String line : lines) {
            resolved.add(safeText(line));
        }
        return resolved;
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String valueOrDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private record HeadingSections(
            XWPFParagraph title,
            XWPFParagraph attachmentOne,
            XWPFParagraph attachmentTwo
    ) {
    }

    private record TableSections(
            XWPFTable application,
            XWPFTable invitees,
            XWPFTable flights,
            XWPFTable itinerary
    ) {
    }
}
