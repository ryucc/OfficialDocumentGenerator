package com.officialpapers.export;

public record OfficialDocumentGeneratorConfig(
        int bodyElementsToKeep,
        String attachmentOneMarker,
        String attachmentTwoMarker,
        String attachmentThreeMarker,
        String documentTitle,
        String defaultAttachmentOneHeading,
        String defaultAttachmentTwoHeading,
        String defaultAttachmentThreeHeading,
        String templateResourcePath,
        int eurToTwdRate
) {
    public static OfficialDocumentGeneratorConfig defaultConfig() {
        return new OfficialDocumentGeneratorConfig(
                31,
                "附件一",
                "附件二",
                "附件三",
                "交通部觀光署駐外辦事處重要人士邀訪申請表",
                "附件一：邀訪名單",
                "附件二：預定行程表",
                "附件三：經費概算表",
                "/templates/important-person-invitation-template.docx",
                36
        );
    }
}
