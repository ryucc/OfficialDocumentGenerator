package com.officialpapers.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OfficialDocumentData(
        @JsonProperty("輸出檔名") String outputFileBaseName,
        @JsonProperty("標題") String title,
        @JsonProperty("申請表") ApplicationForm applicationForm,
        @JsonProperty("附件一") InviteeAttachment inviteeAttachment,
        @JsonProperty("附件二") ScheduleAttachment scheduleAttachment,
        @JsonProperty("附件三") BudgetAttachment budgetAttachment
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApplicationForm(
            @JsonProperty("申請單位") String applicationUnit,
            @JsonProperty("申請日期") String applicationDate,
            @JsonProperty("申請文號") String documentNumber,
            @JsonProperty("對象勾選列") String inviteeTypeLine,
            @JsonProperty("緣由") String reason,
            @JsonProperty("市場屬性") String marketAttribute,
            @JsonProperty("方式") String inviteMethod,
            @JsonProperty("人數") String headcountText,
            @JsonProperty("出發地") String departureLocation,
            @JsonProperty("時間") String timeRangeText,
            @JsonProperty("年度計畫") String annualPlanText,
            @JsonProperty("超過五成理由") String overHalfReason,
            @JsonProperty("預期效益") ExpectedBenefit expectedBenefit,
            @JsonProperty("預定核銷時間") String writeoffDate,
            @JsonProperty("預定成果報核時間") String resultReportDate,
            @JsonProperty("備註列") List<String> noteLines,
            @JsonProperty("附件資料列") List<String> attachmentLines
    ) {
    }

    public record ExpectedBenefit(
            @JsonProperty("影響範圍") String scope,
            @JsonProperty("影響層面") String audience,
            @JsonProperty("預估達成旅遊人數") String estimatedTourists,
            @JsonProperty("其他效益") String otherBenefits
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

    public record BudgetAttachment(
            @JsonProperty("明細") List<BudgetItem> items
    ) {
    }

    public record BudgetItem(
            @JsonProperty("內容") String content,
            @JsonProperty("單價") long unitPrice,
            @JsonProperty("數量") long quantity,
            @JsonProperty("資金來源") String fundingSource,
            @JsonProperty("備註") String note
    ) {
    }
}
