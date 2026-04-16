package com.officialpapers.export;

public record OfficialDocumentGeneratorConfig(
        int bodyElementsToKeep,
        String attachmentOneMarker,
        String attachmentTwoMarker,
        String documentTitle,
        String defaultAttachmentOneHeading,
        String defaultAttachmentTwoHeading,
        String templateResourcePath
) {
    public static OfficialDocumentGeneratorConfig defaultConfig() {
        return new OfficialDocumentGeneratorConfig(
                23,
                "附件一",
                "附件二",
                "交通部觀光署駐外辦事處重要人士邀訪申請表",
                "附件一：邀訪名單",
                "附件二：預定行程表",
                "/templates/important-person-invitation-template.docx"
        );
    }
}
