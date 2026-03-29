package com.officialpapers.api.handler;

import java.util.List;
import java.util.Map;

public class SesReceiptEvent {
    private List<Record> Records;

    public List<Record> getRecords() {
        return Records;
    }

    public void setRecords(List<Record> Records) {
        this.Records = Records;
    }

    public static class Record {
        private String eventSource;
        private String eventVersion;
        private Ses ses;

        public String getEventSource() {
            return eventSource;
        }

        public void setEventSource(String eventSource) {
            this.eventSource = eventSource;
        }

        public String getEventVersion() {
            return eventVersion;
        }

        public void setEventVersion(String eventVersion) {
            this.eventVersion = eventVersion;
        }

        public Ses getSes() {
            return ses;
        }

        public void setSes(Ses ses) {
            this.ses = ses;
        }
    }

    public static class Ses {
        private Mail mail;
        private Receipt receipt;

        public Mail getMail() {
            return mail;
        }

        public void setMail(Mail mail) {
            this.mail = mail;
        }

        public Receipt getReceipt() {
            return receipt;
        }

        public void setReceipt(Receipt receipt) {
            this.receipt = receipt;
        }
    }

    public static class Mail {
        private String timestamp;
        private String source;
        private String messageId;
        private List<String> destination;
        private CommonHeaders commonHeaders;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getMessageId() {
            return messageId;
        }

        public void setMessageId(String messageId) {
            this.messageId = messageId;
        }

        public List<String> getDestination() {
            return destination;
        }

        public void setDestination(List<String> destination) {
            this.destination = destination;
        }

        public CommonHeaders getCommonHeaders() {
            return commonHeaders;
        }

        public void setCommonHeaders(CommonHeaders commonHeaders) {
            this.commonHeaders = commonHeaders;
        }
    }

    public static class CommonHeaders {
        private String from;
        private String subject;
        private List<String> to;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public List<String> getTo() {
            return to;
        }

        public void setTo(List<String> to) {
            this.to = to;
        }
    }

    public static class Receipt {
        private String timestamp;
        private List<String> recipients;
        private Verdict spfVerdict;
        private Verdict dkimVerdict;
        private Verdict virusVerdict;
        private Verdict spamVerdict;
        private Map<String, Object> action;

        public String getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }

        public Verdict getSpfVerdict() {
            return spfVerdict;
        }

        public void setSpfVerdict(Verdict spfVerdict) {
            this.spfVerdict = spfVerdict;
        }

        public Verdict getDkimVerdict() {
            return dkimVerdict;
        }

        public void setDkimVerdict(Verdict dkimVerdict) {
            this.dkimVerdict = dkimVerdict;
        }

        public Verdict getVirusVerdict() {
            return virusVerdict;
        }

        public void setVirusVerdict(Verdict virusVerdict) {
            this.virusVerdict = virusVerdict;
        }

        public Verdict getSpamVerdict() {
            return spamVerdict;
        }

        public void setSpamVerdict(Verdict spamVerdict) {
            this.spamVerdict = spamVerdict;
        }

        public Map<String, Object> getAction() {
            return action;
        }

        public void setAction(Map<String, Object> action) {
            this.action = action;
        }
    }

    public static class Verdict {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
