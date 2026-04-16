package com.officialpapers.export;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
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
        setParagraphText(headings.title(), config.documentTitle());
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
        setInviteeTypeCell(table.getRow(2).getCell(2), form.inviteeTypeLine());
        setCellLines(table.getRow(3).getCell(2), List.of(safeText(form.reason())));
        setCellLines(table.getRow(4).getCell(2), List.of(safeText(form.marketAttribute())));
        setInviteMethodCell(table.getRow(5).getCell(2), form.inviteMethod());
        setCellLines(table.getRow(6).getCell(2), List.of(safeText(form.headcountText())));
        setCellLines(table.getRow(6).getCell(4), List.of(safeText(form.departureLocation())));
        setCellLines(table.getRow(7).getCell(2), List.of(safeText(form.timeRangeText())));
        setCellLines(table.getRow(8).getCell(2), List.of(safeText(form.annualPlanText())));
        String applicantMarker = form.applicantFunding() ? "★" : "□";
        setCellLines(table.getRow(10).getCell(1), List.of(
                applicantMarker + " 申請單位經費", "（附件三：經費概算表）", "預算科目：邀訪預算"));
        setCellLines(table.getRow(10).getCell(2), formatEstimateLines(form.applicantEstimateLines()));
        setCellLines(table.getRow(10).getCell(3), List.of(safeText(form.applicantShareRatio())));
        String otherMarker = form.otherFunding() ? "★" : "□";
        setCellLines(table.getRow(11).getCell(1), List.of(
                otherMarker + " 其他來源：", "預算科目："));
        setCellLines(table.getRow(11).getCell(2), formatEstimateLines(form.otherEstimateLines()));
        setCellLines(table.getRow(11).getCell(3), List.of(safeText(form.otherShareRatio())));
        setCellLines(table.getRow(12).getCell(1), renderSupportLines(form.requestedSupportLines()));
        setCellLines(table.getRow(12).getCell(2), formatEstimateLines(form.requestedEstimateLines()));
        setCellLines(table.getRow(12).getCell(3), List.of(safeText(form.requestedShareRatio())));
        setCellLines(table.getRow(13).getCell(2), List.of(safeText(form.overHalfReason())));
        setExpectedBenefitCell(table.getRow(14).getCell(1), form.expectedBenefit());
        setCellLines(table.getRow(15).getCell(1), List.of(safeText(form.writeoffDate())));
        setCellLines(table.getRow(15).getCell(3), List.of(safeText(form.resultReportDate())));
        setCellLines(table.getRow(16).getCell(1), safeLines(form.noteLines()));
        setCellLines(table.getRow(17).getCell(1), prefixLines("★ ", form.attachmentLines()));
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
            cloneRow(table, templateRow);
        }
    }

    // XWPFTable.addRow(row) deep-copies the CTRow into the document tree, leaving
    // the wrapper's cells pointing at the orphaned copy — subsequent setCellLines
    // calls then write to detached XML. Insert the CTRow into the tree first, then
    // wrap the in-tree CTRow so cell edits land in the document.
    private XWPFTableRow cloneRow(XWPFTable table, XWPFTableRow templateRow) {
        CTRow inTreeCtRow = table.getCTTbl().addNewTr();
        inTreeCtRow.set(templateRow.getCtRow().copy());
        XWPFTableRow newRow = new XWPFTableRow(inTreeCtRow, table);
        internalTableRows(table).add(newRow);
        return newRow;
    }

    @SuppressWarnings("unchecked")
    private List<XWPFTableRow> internalTableRows(XWPFTable table) {
        try {
            Field tableRowsField = XWPFTable.class.getDeclaredField("tableRows");
            tableRowsField.setAccessible(true);
            return (List<XWPFTableRow>) tableRowsField.get(table);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to access XWPFTable.tableRows", exception);
        }
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

    private static final List<String> SUPPORT_ITEM_OPTIONS = List.of(
            "在台交通費", "住宿費", "膳雜費", "英文導遊費", "機票款");

    private List<String> renderSupportLines(List<String> selected) {
        List<String> selectedSet = selected == null ? List.of() : selected;
        boolean hasAny = !selectedSet.isEmpty();
        String headerMarker = hasAny ? "★" : "□";
        List<String> lines = new ArrayList<>();
        lines.add(headerMarker + " 擬請求本署經費支應項目：");
        for (int i = 0; i < SUPPORT_ITEM_OPTIONS.size(); i += 2) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < Math.min(i + 2, SUPPORT_ITEM_OPTIONS.size()); j++) {
                String option = SUPPORT_ITEM_OPTIONS.get(j);
                String marker = selectedSet.contains(option) ? "★" : "□";
                if (j > i) sb.append(" ");
                sb.append(marker).append(" ").append(option);
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    private List<String> formatEstimateLines(List<String> lines) {
        String foreign = "";
        String twd = "";
        if (lines != null) {
            for (String line : lines) {
                if (line == null) continue;
                String trimmed = line.trim();
                if (trimmed.startsWith("約合新台幣")) {
                    twd = trimmed.substring("約合新台幣".length()).trim();
                } else if (!trimmed.isEmpty() && foreign.isEmpty()) {
                    foreign = trimmed;
                }
            }
        }
        return List.of("外幣：" + foreign, "約合新台幣：" + twd);
    }

    private void setExpectedBenefitCell(XWPFTableCell cell, OfficialDocumentData.ExpectedBenefit benefit) {
        String scope = benefit == null ? "" : safeText(benefit.scope());
        String audience = benefit == null ? "" : safeText(benefit.audience());
        String tourists = benefit == null ? "" : safeText(benefit.estimatedTourists());
        String other = benefit == null ? "" : safeText(benefit.otherBenefits());

        boolean isAll = scope.startsWith("全轄區");
        String scopePayload = scope;
        if (isAll) {
            scopePayload = scope.substring("全轄區".length()).replaceAll("^[：:_\\s-]+", "").trim();
        } else {
            scopePayload = scope.replaceAll("^特定範圍[：:_\\s-]*", "").trim();
        }

        CTRPr templateRunProperties = copyRunProperties(cell);

        List<XWPFParagraph> existing = new ArrayList<>(cell.getParagraphs());
        CTPPr templateParagraphProperties = existing.isEmpty()
                ? null
                : copyParagraphProperties(existing.get(0));

        int targetLines = 4;
        while (cell.getParagraphs().size() > targetLines) {
            cell.removeParagraph(cell.getParagraphs().size() - 1);
        }
        while (cell.getParagraphs().size() < targetLines) {
            XWPFParagraph p = cell.addParagraph();
            if (templateParagraphProperties != null) {
                p.getCTP().setPPr((CTPPr) templateParagraphProperties.copy());
            }
        }
        for (XWPFParagraph p : cell.getParagraphs()) {
            if (templateParagraphProperties != null && !p.getCTP().isSetPPr()) {
                p.getCTP().setPPr((CTPPr) templateParagraphProperties.copy());
            }
            clearRuns(p);
        }

        XWPFParagraph p0 = cell.getParagraphs().get(0);
        String scopeMarkerAll = isAll ? "★" : "□";
        String scopeMarkerSpecific = isAll ? "□" : "★";
        addStyledRun(p0, templateRunProperties, "影響範圍：" + scopeMarkerAll + " 全轄區  " + scopeMarkerSpecific + " 特定範圍", false);
        String scopeNote = scopePayload.isEmpty() ? "　　　　　" : scopePayload;
        addStyledRun(p0, templateRunProperties, scopeNote, true);

        XWPFParagraph p1 = cell.getParagraphs().get(1);
        addStyledRun(p1, templateRunProperties, "影響層面：", false);
        String audienceNote = audience.isEmpty() ? "　　　　　" : audience;
        addStyledRun(p1, templateRunProperties, audienceNote, true);

        XWPFParagraph p2 = cell.getParagraphs().get(2);
        addStyledRun(p2, templateRunProperties, "預估達成旅遊人數：", false);
        String touristsNote = tourists.isEmpty() ? "　　　　　" : tourists;
        addStyledRun(p2, templateRunProperties, touristsNote, true);

        XWPFParagraph p3 = cell.getParagraphs().get(3);
        addStyledRun(p3, templateRunProperties, "其他效益：" + other, false);
    }

    private List<String> prefixLines(String prefix, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("");
        }
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            result.add(prefix + safeText(line));
        }
        return result;
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

    private static final List<String> INVITEE_TYPE_OPTIONS = List.of("業者", "媒體", "其他");

    private void setInviteeTypeCell(XWPFTableCell cell, String selected) {
        String raw = selected == null ? "" : selected.trim();
        String selectedBase = raw.replaceAll("[（(：:_].*", "").trim();
        String otherNote = "";
        if (selectedBase.equals("其他")) {
            otherNote = raw.substring("其他".length())
                    .replaceAll("^[（(：:_\\s-]+", "")
                    .replaceAll("[）)\\s]+$", "")
                    .trim();
        }

        CTRPr templateRunProperties = copyRunProperties(cell);
        XWPFParagraph paragraph = prepareSingleParagraph(cell);

        for (int index = 0; index < INVITEE_TYPE_OPTIONS.size(); index++) {
            String option = INVITEE_TYPE_OPTIONS.get(index);
            String marker = option.equals(selectedBase) ? "★" : "□";
            String prefix = (index == 0 ? "" : "  ") + marker + " " + option;
            addStyledRun(paragraph, templateRunProperties, prefix, false);
            if (option.equals("其他")) {
                String note = otherNote.isEmpty() ? "　　　　　" : otherNote;
                addStyledRun(paragraph, templateRunProperties, note, true);
            }
        }
    }

    private static final List<String> INVITE_METHOD_OPTIONS = List.of("自行邀訪", "合作邀訪");

    private void setInviteMethodCell(XWPFTableCell cell, String selected) {
        String raw = selected == null ? "" : selected.trim();
        String selectedBase = raw.replaceAll("[，,（(：:_].*", "").trim();
        String partner = "";
        if (selectedBase.equals("合作邀訪")) {
            partner = raw.substring("合作邀訪".length())
                    .replaceAll("^[，,（(：:_\\s-]*(合作單位)?[（(：:_\\s-]*", "")
                    .replaceAll("[）)\\s]+$", "")
                    .trim();
        }

        CTRPr templateRunProperties = copyRunProperties(cell);
        XWPFParagraph paragraph = prepareSingleParagraph(cell);

        for (int index = 0; index < INVITE_METHOD_OPTIONS.size(); index++) {
            String option = INVITE_METHOD_OPTIONS.get(index);
            String marker = option.equals(selectedBase) ? "★" : "□";
            String prefix = (index == 0 ? "" : "  ") + marker + " " + option;
            addStyledRun(paragraph, templateRunProperties, prefix, false);
            if (option.equals("合作邀訪")) {
                addStyledRun(paragraph, templateRunProperties, "，合作單位：", false);
                String note = partner.isEmpty() ? "　　　　　" : partner;
                addStyledRun(paragraph, templateRunProperties, note, true);
            }
        }
    }

    private XWPFParagraph prepareSingleParagraph(XWPFTableCell cell) {
        List<XWPFParagraph> paragraphs = new ArrayList<>(cell.getParagraphs());
        CTPPr templateParagraphProperties = paragraphs.isEmpty()
                ? null
                : copyParagraphProperties(paragraphs.get(0));
        while (cell.getParagraphs().size() > 1) {
            cell.removeParagraph(cell.getParagraphs().size() - 1);
        }
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        if (templateParagraphProperties != null) {
            paragraph.getCTP().setPPr((CTPPr) templateParagraphProperties.copy());
        }
        clearRuns(paragraph);
        return paragraph;
    }

    private static final String UNDERLINE_PAD = "  ";

    private void addStyledRun(XWPFParagraph paragraph, CTRPr templateRunProperties, String text, boolean underline) {
        XWPFRun run = paragraph.createRun();
        if (templateRunProperties != null) {
            run.getCTR().setRPr((CTRPr) templateRunProperties.copy());
        }
        if (underline) {
            run.setUnderline(UnderlinePatterns.SINGLE);
            run.setText(UNDERLINE_PAD + text + UNDERLINE_PAD);
        } else {
            run.setText(text);
        }
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
