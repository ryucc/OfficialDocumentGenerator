package com.officialpapers.export;

public record OfficialDocumentGeneratorConfig(
        int bodyElementsToKeep,
        String attachmentOneMarker,
        String attachmentTwoMarker,
        String defaultAttachmentOneHeading,
        String defaultAttachmentTwoHeading,
        String templateResourcePath
) {
    public static OfficialDocumentGeneratorConfig defaultConfig() {
        return new OfficialDocumentGeneratorConfig(
                23,
                "附件一",
                "附件二",
                "附件一：邀訪名單",
                "附件二：預定行程表",
                "/templates/important-person-invitation-template.docx"
        );
    }
}
