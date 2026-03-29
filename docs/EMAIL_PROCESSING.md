# Email Processing Architecture

## Overview

The system receives emails at `*@gongwengpt.click`, extracts text from attachments (PDF, DOCX, PNG), and stores them as document instructions.

## How Attachments Are Processed

### Email Flow

```
Email sent to gongwengpt.click
         ↓
    AWS SES receives
         ↓
  Filter Lambda (allowlist check)
         ↓
    ┌─────────────┬─────────────┐
    ↓             ↓             ↓
Raw email     Metadata      Receipt
to S3         to SNS        actions
    ↓             ↓
S3 Bucket   SNS Topic
(MIME msg)       ↓
            SQS Queue
            (subscribed)
                 ↓
        Processor Lambda
                 ↓
           Parse email flow
```

### 1. **Email Storage in S3**

When SES receives an email:
- **Full raw MIME message** stored in S3
- Path: `s3://bucket/emails/<messageId>`
- Contains: headers, body, **all attachments embedded**

### 2. **SNS → SQS Notification**

SES publishes to SNS topic, which forwards to SQS. The SQS message contains:
```json
{
  "Message": "{\"mail\":{\"messageId\":\"...\",\"source\":\"sender@example.com\"},\"receipt\":{\"action\":{\"type\":\"S3\",\"bucketName\":\"official-doc-generator-test-...\",\"objectKey\":\"emails/abc123...\"}}}"
}
```

Note: SES Receipt Rules support S3Action, SNSAction, and LambdaAction, but **not** direct SQSAction. We use SNS → SQS subscription pattern.

### 3. **Lambda Processing**

The `EmailProcessorHandler` Lambda function:

1. **Reads SQS message** to get S3 location
2. **Downloads raw email** from S3
3. **Parses MIME** using JavaMail to extract:
   - Email headers (from, subject, date)
   - Plain text or HTML body
   - **All attachments** (filename, content-type, binary data)
4. **Processes each attachment**:
   - PDF → Extract text with Apache PDFBox
   - DOCX → Extract text with Apache POI
   - PNG/JPG → OCR with Tesseract (or Claude API vision)
5. **Stores extracted text** as document instructions in DynamoDB

## Attachment Handling Details

### MIME Multipart Structure

Raw email in S3 looks like:
```
MIME-Version: 1.0
Content-Type: multipart/mixed; boundary="boundary123"

--boundary123
Content-Type: text/plain

Email body text here

--boundary123
Content-Type: application/pdf
Content-Disposition: attachment; filename="document.pdf"
Content-Transfer-Encoding: base64

JVBERi0xLjQKJeLjz9MK... (base64 encoded PDF data)

--boundary123--
```

### Extraction Methods

| File Type | Library | Method |
|-----------|---------|--------|
| **PDF** | Apache PDFBox | `PDFTextStripper.getText()` |
| **DOCX** | Apache POI | `XWPFWordExtractor.getText()` |
| **PNG/JPG** | Tesseract OCR* | `Tesseract.doOCR()` |

*For images with AI instructions/screenshots, Claude API vision is recommended for better accuracy

## Current Implementation Status

✅ Email receiving infrastructure (SES → S3 + SQS)
✅ Lambda function skeleton
✅ MIME parsing (extract attachments)
✅ PDF text extraction
✅ DOCX text extraction
⚠️ Image OCR (placeholder - needs implementation)
⚠️ DynamoDB storage (commented TODO)

## Deployment Steps

### 1. Verify Domain in SES

```bash
aws ses verify-domain-identity \
  --domain gongwengpt.click \
  --region us-east-1
```

Add DNS records:
- **MX**: `10 inbound-smtp.us-east-1.amazonaws.com`
- **TXT**: `_amazonses.gongwengpt.click` → (verification token from command)

### 2. Build & Deploy

```bash
# Build Java Lambda
./gradlew build

# Deploy CloudFormation
sam build
sam deploy --guided
```

### 3. Activate SES Rule Set

```bash
aws ses set-active-receipt-rule-set \
  --rule-set-name gongwengpt-ruleset-test \
  --region us-east-1
```

### 4. Test Email Reception

Send test email:
```bash
echo "Test with attachment" | mail -s "Test" \
  -A /path/to/document.pdf \
  test@gongwengpt.click
```

Check processing:
```bash
# View SQS messages
aws sqs receive-message \
  --queue-url $(aws cloudformation describe-stacks \
    --stack-name official-doc-generator-app-test \
    --query 'Stacks[0].Outputs[?OutputKey==`IncomingEmailQueueUrl`].OutputValue' \
    --output text)

# View Lambda logs
sam logs -n EmailProcessorFunction --stack-name official-doc-generator-app-test --tail
```

## Next Steps

1. **Implement Image OCR**
   - Option A: Install Tesseract in Lambda layer
   - Option B: Use Claude API vision (recommended for screenshots)

2. **DynamoDB Storage**
   - Uncomment `createDocumentInstruction()` call
   - Store extracted text with metadata

3. **Error Handling**
   - Dead letter queue for failed messages
   - Retry logic for transient failures

4. **Monitoring**
   - CloudWatch metrics for email processing
   - Alerts for queue depth/failures

## Attachment Size Limits

- **SES email limit**: 40 MB (including all attachments)
- **Lambda payload**: 6 MB (synchronous), 256 KB (async event)
- **S3**: No practical limit (email stored in S3, not passed through Lambda)

Since attachments are stored in S3, the Lambda function downloads them directly from S3, avoiding Lambda payload limits.

## Cost Considerations

- **SES**: $0.10 per 1,000 emails received
- **S3**: $0.023 per GB stored
- **SQS**: $0.40 per million requests (first 1M free)
- **Lambda**: $0.20 per 1M requests + compute time
- **DynamoDB**: On-demand pricing (pay per request)

For low-volume use (<1000 emails/month): ~$1-2/month
