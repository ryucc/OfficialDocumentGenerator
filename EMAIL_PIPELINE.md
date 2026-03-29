# Email Pipeline Setup Guide

This document describes the email receiving pipeline for `ai@gongwengpt.click`.

## Architecture

The pipeline receives emails at `ai@gongwengpt.click`, filters by sender allowlist, and stores emails in S3:

1. **AWS SES** receives emails to `ai@gongwengpt.click`
2. **Lambda Filter** checks if sender is in allowlist and validates SPF/DKIM/DMARC
3. **S3 Storage** saves allowed emails to `s3://<bucket>/emails/<stage>/`

## Current Configuration

- **Email Address**: `ai@gongwengpt.click`
- **Domain**: `gongwengpt.click` (managed in Route 53)
- **Storage**: Shared S3 bucket with main application
- **Retention**: 90 days (automatic cleanup)

## Deployment

### 1. Get Route 53 Hosted Zone ID (Optional)

If you want CloudFormation to automatically setup DNS:

```bash
aws route53 list-hosted-zones --query "HostedZones[?Name=='gongwengpt.click.'].Id" --output text
```

### 2. Deploy the Stack

**With automatic DNS setup** (recommended):

```bash
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name official-doc-generator-app-test \
  --parameter-overrides \
    Stage=test \
    AllowedEmailSenders="shortyliu@gmail.com" \
    DomainName=gongwengpt.click \
    Route53HostedZoneId=<YOUR_HOSTED_ZONE_ID> \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --resolve-s3
```

**Without automatic DNS setup** (manual DNS configuration required):

```bash
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name official-doc-generator-app-test \
  --parameter-overrides \
    Stage=test \
    AllowedEmailSenders="shortyliu@gmail.com" \
    DomainName=gongwengpt.click \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --resolve-s3
```

If you don't provide `Route53HostedZoneId`, you'll need to manually add:
- MX record: `10 inbound-smtp.us-east-1.amazonaws.com`
- TXT record: `_amazonses.gongwengpt.click` = (get token from stack outputs)

### What Gets Deployed

CloudFormation will automatically:
- ✅ Deploy Lambda functions (email filter, SES setup)
- ✅ Create SES receipt rule set and rules
- ✅ Verify domain with SES (via custom resource)
- ✅ Activate SES receipt rule set (via custom resource)
- ✅ Configure Route 53 DNS records (if hosted zone ID provided)

No manual steps required!

## Configuration

### Update Email Allowlist

To change which senders are allowed:

```bash
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name official-doc-generator-app-test \
  --parameter-overrides \
    Stage=test \
    AllowedEmailSenders="email1@example.com,email2@example.com,email3@example.com" \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --no-fail-on-empty-changeset
```

### Allow All Senders (Remove Filter)

```bash
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name official-doc-generator-app-test \
  --parameter-overrides \
    Stage=test \
    AllowedEmailSenders="" \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --no-fail-on-empty-changeset
```

## Monitoring Received Emails

### List Recent Emails

```bash
# Get bucket name
BUCKET=$(aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`UploadedDocumentBucketName`].OutputValue' \
  --output text)

# List emails
aws s3 ls "s3://${BUCKET}/emails/" --recursive --human-readable | tail -20
```

Or use **AWS Console**: S3 → Navigate to bucket → `emails/test/` folder

### Download an Email

```bash
# Get bucket name
BUCKET=$(aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`UploadedDocumentBucketName`].OutputValue' \
  --output text)

# Download specific email
aws s3 cp "s3://${BUCKET}/emails/test/<message-id>" email.eml

# View the email
cat email.eml
```

## Lambda Functions

### email-filter/index.py
Filters incoming emails by:
1. Validating sender authentication (SPF/DKIM/DMARC)
2. Checking sender against allowlist

Returns:
- `CONTINUE` - Allow email processing (save to S3)
- `STOP_RULE` - Reject email (do not save)

### ses-setup/index.py
Custom CloudFormation resource that:
- Verifies domain with SES and returns verification token
- Activates SES receipt rule set
- Handles cleanup on stack deletion

## Testing

### Send a Test Email

From an allowed sender (e.g., `shortyliu@gmail.com`), send an email to:
```
ai@gongwengpt.click
```

### Check if Email Was Received

```bash
# Check S3
BUCKET=$(aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`UploadedDocumentBucketName`].OutputValue' \
  --output text)
aws s3 ls "s3://${BUCKET}/emails/" --recursive

# Check Lambda logs
aws logs tail /aws/lambda/email-filter-test --follow
```

## Troubleshooting

### Email Not Received

1. **Check SES domain verification**
   ```bash
   aws ses get-identity-verification-attributes --identities gongwengpt.click
   ```
   Status should be "Success"

2. **Check DNS records**
   ```bash
   dig MX gongwengpt.click
   dig TXT _amazonses.gongwengpt.click
   ```

3. **Check if rule set is active**
   ```bash
   aws ses describe-active-receipt-rule-set
   ```
   Should show `gongwengpt-email-rules-test`

4. **Check Lambda logs**
   ```bash
   aws logs tail /aws/lambda/email-filter-test --follow
   ```

### Email Rejected by Allowlist

1. **Check current allowlist**
   ```bash
   aws cloudformation describe-stacks \
     --stack-name official-doc-generator-app-test \
     --query 'Stacks[0].Parameters[?ParameterKey==`AllowedEmailSenders`].ParameterValue' \
     --output text
   ```

2. **Update allowlist** (see Configuration section above)

### SES Sandbox Mode

By default, SES is in sandbox mode and can only receive from verified email addresses or domains.

To verify an email address:
```bash
aws ses verify-email-identity --email-address sender@example.com
```

To move out of sandbox mode:
1. Go to AWS SES Console
2. Click "Account Dashboard"
3. Click "Request production access"

## Stack Outputs

After deployment, view outputs:

```bash
aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs' \
  --output table
```

Key outputs:
- `EmailAddress` - The configured email address (ai@gongwengpt.click)
- `UploadedDocumentBucketName` - S3 bucket where emails are stored
- `EmailReceiptRuleSetName` - SES receipt rule set name
- `EmailFilterFunctionName` - Lambda function for filtering emails
- `SESVerificationToken` - Domain verification token (if manual DNS needed)

## Cost Estimation

- **SES**: $0.10 per 1,000 emails received
- **Lambda**: Free tier covers most usage
- **S3**: ~$0.023 per GB per month
- **DynamoDB**: Free tier covers most usage

Example: 1,000 emails/month with 1MB each = ~$0.12/month
