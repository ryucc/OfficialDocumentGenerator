package com.officialpapers.export;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OfficialDocumentData(
        @JsonProperty("輸出檔名") String outputFileBaseName,
        @JsonProperty("標題") String title,
        @JsonProperty("申請表") ApplicationForm applicationForm,
        @JsonProperty("附件一") InviteeAttachment inviteeAttachment,
        @JsonProperty("附件二") ScheduleAttachment scheduleAttachment
) {

    public record ApplicationForm(
            @JsonProperty("申請單位") String applicationUnit,
            @JsonProperty("申請日期") String applicationDate,
            @JsonProperty("申請文號") String documentNumber,
            @JsonProperty("對象勾選列") String inviteeTypeLine,
            @JsonProperty("緣由") String reason,
            @JsonProperty("市場屬性") String marketAttribute,
            @JsonProperty("方式列") List<String> inviteMethodLines,
            @JsonProperty("人數") String headcountText,
            @JsonProperty("出發地") String departureLocation,
            @JsonProperty("時間") String timeRangeText,
            @JsonProperty("年度計畫") String annualPlanText,
            @JsonProperty("申請單位經費列") List<String> applicantFundingLines,
            @JsonProperty("申請單位預估金額列") List<String> applicantEstimateLines,
            @JsonProperty("申請單位分攤比例") String applicantShareRatio,
            @JsonProperty("其他來源列") List<String> otherFundingLines,
            @JsonProperty("其他來源預估金額列") List<String> otherEstimateLines,
            @JsonProperty("其他來源分攤比例") String otherShareRatio,
            @JsonProperty("本署支應列") List<String> requestedSupportLines,
            @JsonProperty("本署支應預估金額列") List<String> requestedEstimateLines,
            @JsonProperty("本署支應分攤比例") String requestedShareRatio,
            @JsonProperty("超過五成理由") String overHalfReason,
            @JsonProperty("預期效益列") List<String> expectedBenefitLines,
            @JsonProperty("預定核銷時間") String writeoffDate,
            @JsonProperty("預定成果報核時間") String resultReportDate,
            @JsonProperty("備註列") List<String> noteLines,
            @JsonProperty("附件資料列") List<String> attachmentLines
    ) {
    }

    public record InviteeAttachment(
            @JsonProperty("標題") String heading,
            @JsonProperty("名單") List<InviteeEntry> entries
    ) {
    }

    public record InviteeEntry(
            @JsonProperty("編號") String serialNumber,
            @JsonProperty("姓名") String name,
            @JsonProperty("性別") String gender,
            @JsonProperty("職稱") String jobTitle,
            @JsonProperty("單位名稱") String organizationName,
            @JsonProperty("邀訪紀錄") String inviteRecord,
            @JsonProperty("成效評估") String effectiveness,
            @JsonProperty("備註") String note
    ) {
    }

    public record ScheduleAttachment(
            @JsonProperty("標題") String heading,
            @JsonProperty("航班資訊") List<FlightInfo> flights,
            @JsonProperty("行程表") List<ItineraryRow> itinerary
    ) {
    }

    public record FlightInfo(
            @JsonProperty("航班資訊") String route,
            @JsonProperty("班機號碼") String flightNumber,
            @JsonProperty("出發時間") String departureTime,
            @JsonProperty("抵達時間") String arrivalTime
    ) {
    }

    public record ItineraryRow(
            @JsonProperty("日期列") List<String> dateLines,
            @JsonProperty("建議行程列") List<String> itineraryLines,
            @JsonProperty("住宿列") List<String> accommodationLines
    ) {
    }
}
