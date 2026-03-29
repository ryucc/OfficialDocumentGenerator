package com.officialpapers.api.handler;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

import java.util.Set;

public class EmailVerifier {

    private final Set<String> allowedSenders;
    private final LambdaLogger logger;

    public EmailVerifier(Set<String> allowedSenders, LambdaLogger logger) {
        this.allowedSenders = allowedSenders;
        this.logger = logger;
    }

    public boolean isAllowed(SesReceiptEvent.Record record) {
        if (record == null || record.getSes() == null) {
            logger.log("Invalid SES record structure, blocking");
            return false;
        }

        SesReceiptEvent.Ses ses = record.getSes();
        SesReceiptEvent.Mail mail = ses.getMail();
        SesReceiptEvent.Receipt receipt = ses.getReceipt();

        if (mail == null) {
            logger.log("No mail section in SES event, blocking");
            return false;
        }

        String source = mail.getSource();
        if (source == null || source.isEmpty()) {
            logger.log("No source address found, blocking");
            return false;
        }

        logger.log("Email from: " + source);

        // Check authentication verdicts
        if (receipt != null && !isAuthentic(source, receipt)) {
            return false;
        }

        // Check allowlist
        if (!allowedSenders.contains(source.toLowerCase())) {
            logger.log("Sender not in allowlist, blocking: " + source);
            return false;
        }

        logger.log("Sender allowed and verified: " + source);
        return true;
    }

    private boolean isAuthentic(String source, SesReceiptEvent.Receipt receipt) {
        String spfStatus = getVerdictStatus(receipt.getSpfVerdict());
        String dkimStatus = getVerdictStatus(receipt.getDkimVerdict());

        logger.log("SPF: " + spfStatus + ", DKIM: " + dkimStatus);

        // Block if both SPF and DKIM fail (likely spoofed)
        if ("FAIL".equals(spfStatus) && "FAIL".equals(dkimStatus)) {
            logger.log("Both SPF and DKIM failed, blocking potential spoofed email");
            return false;
        }

        // For Gmail, require DKIM to pass (Gmail signs all outgoing mail)
        if (source.toLowerCase().endsWith("@gmail.com")) {
            if (!"PASS".equals(dkimStatus)) {
                logger.log("Gmail sender without valid DKIM signature, blocking");
                return false;
            }
        }

        return true;
    }

    private String getVerdictStatus(SesReceiptEvent.Verdict verdict) {
        if (verdict == null || verdict.getStatus() == null) {
            return "UNKNOWN";
        }
        return verdict.getStatus();
    }
}
