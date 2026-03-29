#!/bin/bash

# Deploy the full stack with email pipeline
# Usage: ./scripts/deploy-with-email.sh [stage]

set -e

STAGE=${1:-test}
STACK_NAME="official-doc-generator-app-${STAGE}"
REGION=${AWS_REGION:-us-east-1}

echo "=================================================="
echo "Deploying Official Document Generator with Email"
echo "=================================================="
echo "Stack Name: ${STACK_NAME}"
echo "Stage: ${STAGE}"
echo "Region: ${REGION}"
echo "=================================================="

# Build the application first
echo "🔨 Building application..."
cd "$(dirname "$0")/.."
./gradlew clean build shadowJar

# Create deployment package
echo "📦 Creating deployment package..."
mkdir -p build/distributions
cp build/libs/*-all.jar build/distributions/lambda.zip

# Deploy the CloudFormation stack
echo "🚀 Deploying stack..."
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name ${STACK_NAME} \
  --parameter-overrides \
    Stage=${STAGE} \
    AllowedEmailSenders="shortyliu@gmail.com" \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --region ${REGION} \
  --resolve-s3

echo "✅ Stack deployed successfully!"

# Get stack outputs
echo ""
echo "=================================================="
echo "Stack Outputs:"
echo "=================================================="
aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
  --output table

# Get the rule set name
RULE_SET_NAME=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[?OutputKey==`EmailReceiptRuleSetName`].OutputValue' \
  --output text)

echo ""
echo "=================================================="
echo "⚠️  IMPORTANT: Email Setup Steps"
echo "=================================================="
echo ""
echo "1. Verify domain in SES:"
echo "   aws ses verify-domain-identity --domain gongwengpt.click --region ${REGION}"
echo ""
echo "2. Add DNS records to Route 53 for gongwengpt.click:"
echo ""
echo "   MX Record:"
echo "   - Name: @ (or blank for root domain)"
echo "   - Type: MX"
echo "   - Priority: 10"
echo "   - Value: inbound-smtp.${REGION}.amazonaws.com"
echo ""
echo "   Get SES verification TXT record:"
echo "   aws ses verify-domain-identity --domain gongwengpt.click --region ${REGION}"
echo ""
echo "3. Activate the SES receipt rule set:"
echo "   aws ses set-active-receipt-rule-set --rule-set-name ${RULE_SET_NAME} --region ${REGION}"
echo ""
echo "4. Current email allowlist: shortyliu@gmail.com"
echo "   To update: ./scripts/update-allowlist.sh \"email1@example.com,email2@example.com\""
echo ""
echo "=================================================="
echo "📧 Test by sending email to: ai@gongwengpt.click"
echo "=================================================="
