# Email Pipeline Setup Guide

This document describes the email receiving pipeline for `ai@gongwengpt.click`.

## Architecture

The pipeline receives emails at `ai@gongwengpt.click`, filters by sender allowlist, and stores emails in S3:

1. **AWS SES** receives emails to `ai@gongwengpt.click`
2. **Lambda Filter** checks if sender is in allowlist
3. **S3 Storage** saves allowed emails to `s3://<bucket>/emails/<stage>/`
4. **Lambda Metadata** extracts email metadata and stores in DynamoDB
5. **DynamoDB** indexes emails for quick lookup

## Current Configuration

- **Allowed Sender**: `shortyliu@gmail.com`
- **Email Address**: `ai@gongwengpt.click`
- **Domain**: `gongwengpt.click` (managed in Route 53)
- **Storage**: Shared S3 bucket with main application
- **Retention**: 90 days (automatic cleanup)

## Deployment

### 1. Deploy the Stack

```bash
./scripts/deploy-with-email.sh test
```

This deploys the complete stack including:
- API Gateway + Lambda functions (existing)
- Cognito user pool (existing)
- S3 bucket (existing, shared)
- Email filter Lambda
- Email metadata Lambda
- DynamoDB table for email metadata
- SES receipt rules

### 2. Configure AWS SES

#### Verify Domain

```bash
aws ses verify-domain-identity --domain gongwengpt.click --region us-east-1
```

This returns a verification token. Add it to Route 53:

```bash
# Get the verification token
TOKEN=$(aws ses verify-domain-identity --domain gongwengpt.click --region us-east-1 --query 'VerificationToken' --output text)

# Add TXT record to Route 53
./scripts/setup-ses-dns.sh
```

#### Configure Route 53 DNS Records

Add MX record to receive emails:

```bash
# MX record pointing to SES
# Name: @ (root domain)
# Type: MX
# Priority: 10
# Value: inbound-smtp.us-east-1.amazonaws.com
```

Or use the helper script:

```bash
./scripts/setup-ses-dns.sh
```

#### Activate SES Receipt Rule Set

```bash
# Get rule set name from stack outputs
RULE_SET_NAME=$(aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`EmailReceiptRuleSetName`].OutputValue' \
  --output text)

# Activate the rule set
aws ses set-active-receipt-rule-set --rule-set-name $RULE_SET_NAME --region us-east-1
```

### 3. (Optional) Move SES Out of Sandbox

By default, SES is in sandbox mode and can only receive from verified email addresses. To receive from any email:

1. Go to AWS SES Console
2. Click "Account Dashboard"
3. Click "Request production access"
4. Fill out the form explaining your use case

## Managing the Allowlist

### View Current Allowlist

```bash
./scripts/update-allowlist.sh
```

### Update Allowlist

```bash
./scripts/update-allowlist.sh "email1@example.com,email2@example.com,email3@example.com"
```

### Allow All Senders (Remove Filter)

```bash
./scripts/update-allowlist.sh ""
```

## Monitoring Received Emails

### List Recent Emails

```bash
# List last 20 emails
./scripts/list-received-emails.sh

# List last 50 emails
./scripts/list-received-emails.sh 50
```

### Query Email Metadata

```bash
# Query last 10 emails from DynamoDB
./scripts/query-email-metadata.sh

# Query last 50 emails
./scripts/query-email-metadata.sh 50
```

### Download an Email

```bash
# Get the S3 key from the list
S3_KEY="emails/test/abc123def456"

# Download the email
BUCKET=$(aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`UploadedDocumentBucketName`].OutputValue' \
  --output text)

aws s3 cp "s3://${BUCKET}/${S3_KEY}" email.eml

# View the email
cat email.eml
```

## Testing

### Send a Test Email

```bash
# From shortyliu@gmail.com, send an email to:
ai@gongwengpt.click
```

### Check if Email Was Received

```bash
# Check S3
./scripts/list-received-emails.sh

# Check DynamoDB
./scripts/query-email-metadata.sh

# Check Lambda logs
aws logs tail /aws/lambda/email-filter-test --follow
aws logs tail /aws/lambda/email-metadata-test --follow
```

## Troubleshooting

### Email Not Received

1. **Check SES domain verification**
   ```bash
   aws ses get-identity-verification-attributes --identities gongwengpt.click
   ```

2. **Check MX record**
   ```bash
   dig MX gongwengpt.click
   ```

3. **Check if rule set is active**
   ```bash
   aws ses describe-active-receipt-rule-set
   ```

4. **Check Lambda logs**
   ```bash
   aws logs tail /aws/lambda/email-filter-test --follow
   ```

### Email Rejected by Allowlist

1. **Check current allowlist**
   ```bash
   aws lambda get-function-configuration --function-name email-filter-test \
     --query 'Environment.Variables.ALLOWED_SENDERS'
   ```

2. **Update allowlist**
   ```bash
   ./scripts/update-allowlist.sh "correct@email.com"
   ```

### SES Sandbox Mode

If you're in SES sandbox mode, you can only receive from:
- Verified email addresses
- Verified domains

To verify an email address:
```bash
aws ses verify-email-identity --email-address sender@example.com
```

## Cost Estimation

- **SES**: $0.10 per 1,000 emails received
- **Lambda**: Free tier covers most usage
- **S3**: ~$0.023 per GB per month
- **DynamoDB**: Free tier covers most usage

Example: 1,000 emails/month with 1MB each = ~$0.12/month
