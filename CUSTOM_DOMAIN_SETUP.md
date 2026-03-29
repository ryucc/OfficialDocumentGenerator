# Custom Domain Setup Guide

This guide explains how to set up custom domains for your application:
- Frontend: `gongwengpt.click`
- Backend API: `api.gongwengpt.click`

## Prerequisites

- Domain `gongwengpt.click` already exists in Route 53 (Hosted Zone ID: Z08801051G2GR30IAD1A4)
- AWS CLI configured with appropriate permissions

## Deployment Steps

### Step 1: Deploy Frontend Certificate (us-east-1)

CloudFront/Amplify requires certificates in **us-east-1** region.

```bash
aws cloudformation create-stack \
  --region us-east-1 \
  --stack-name frontend-certificate \
  --template-body file://infra/certificate-us-east-1.yaml
```

Wait for the stack to complete:
```bash
aws cloudformation wait stack-create-complete \
  --region us-east-1 \
  --stack-name frontend-certificate
```

Get the certificate ARN:
```bash
aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name frontend-certificate \
  --query 'Stacks[0].Outputs[?OutputKey==`CertificateArn`].OutputValue' \
  --output text
```

**Note:** DNS validation records are automatically created in Route 53. Wait 5-10 minutes for certificate validation to complete.

Check certificate status:
```bash
CERT_ARN=$(aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name frontend-certificate \
  --query 'Stacks[0].Outputs[?OutputKey==`CertificateArn`].OutputValue' \
  --output text)

aws acm describe-certificate \
  --region us-east-1 \
  --certificate-arn $CERT_ARN \
  --query 'Certificate.Status'
```

Wait until status shows `ISSUED`.

### Step 2: Update Backend Stack (API Custom Domain)

This creates:
- ACM certificate for `api.gongwengpt.click` in ap-northeast-1
- API Gateway custom domain
- Route 53 A record

```bash
# The CodePipeline will automatically deploy this via git push
# Or manually trigger via SAM:
sam build -t infra/app-only.yaml
sam deploy \
  --template-file .aws-sam/build/template.yaml \
  --stack-name official-doc-generator-app-test \
  --capabilities CAPABILITY_IAM \
  --no-fail-on-empty-changeset
```

Wait for API certificate validation (5-10 minutes).

### Step 3: Update Frontend Stack (Amplify Custom Domain)

Get the certificate ARN from Step 1:
```bash
CERT_ARN=$(aws cloudformation describe-stacks \
  --region us-east-1 \
  --stack-name frontend-certificate \
  --query 'Stacks[0].Outputs[?OutputKey==`CertificateArn`].OutputValue' \
  --output text)

echo "Certificate ARN: $CERT_ARN"
```

Update the Amplify stack:
```bash
aws cloudformation update-stack \
  --region ap-northeast-1 \
  --stack-name official-doc-generator-frontend \
  --template-body file://infra/amplify-frontend.yaml \
  --parameters \
    ParameterKey=GitHubToken,UsePreviousValue=true \
    ParameterKey=FrontendCertificateArn,ParameterValue=$CERT_ARN
```

Wait for update:
```bash
aws cloudformation wait stack-update-complete \
  --region ap-northeast-1 \
  --stack-name official-doc-generator-frontend
```

### Step 4: Verify DNS Records

Check that DNS records are created:

```bash
# Check API domain
aws route53 list-resource-record-sets \
  --hosted-zone-id Z08801051G2GR30IAD1A4 \
  --query "ResourceRecordSets[?Name=='api.gongwengpt.click.']"

# Check frontend domain (created by Amplify)
aws route53 list-resource-record-sets \
  --hosted-zone-id Z08801051G2GR30IAD1A4 \
  --query "ResourceRecordSets[?Name=='gongwengpt.click.']"
```

### Step 5: Test the Domains

After DNS propagation (5-15 minutes):

```bash
# Test API
curl https://api.gongwengpt.click/api/v1/sample-documents

# Test Frontend
curl -I https://gongwengpt.click
```

## URLs After Setup

- **Frontend**: https://gongwengpt.click
- **Backend API**: https://api.gongwengpt.click/api/v1

## Troubleshooting

### Certificate Stuck in PENDING_VALIDATION

Check DNS validation records:
```bash
aws acm describe-certificate \
  --region us-east-1 \
  --certificate-arn $CERT_ARN \
  --query 'Certificate.DomainValidationOptions'
```

Verify CNAME records exist in Route 53.

### API Domain Not Working

1. Check API Gateway custom domain:
```bash
aws apigateway get-domain-name \
  --domain-name api.gongwengpt.click \
  --region ap-northeast-1
```

2. Check base path mapping:
```bash
aws apigateway get-base-path-mappings \
  --domain-name api.gongwengpt.click \
  --region ap-northeast-1
```

### Frontend Domain Not Working

Check Amplify domain status:
```bash
aws amplify get-domain-association \
  --app-id dfs110pfwxu8u \
  --domain-name gongwengpt.click \
  --region ap-northeast-1
```

## Rollback

To rollback to the original configuration:

1. Revert frontend environment variable to original API Gateway URL
2. Delete custom domain configurations
3. Frontend will continue to work with default Amplify URL

## Cost Implications

- ACM Certificates: **Free**
- Route 53 DNS queries: **~$0.40/month per hosted zone**
- API Gateway custom domain: **No additional cost**
- Amplify custom domain: **No additional cost**
