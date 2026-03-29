#!/bin/bash

# List received emails from S3
# Usage: ./scripts/list-received-emails.sh [limit] [stage]

set -e

LIMIT=${1:-20}
STAGE=${2:-test}
STACK_NAME="official-doc-generator-app-${STAGE}"
REGION=${AWS_REGION:-us-east-1}

# Get bucket name from stack output
BUCKET_NAME=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[?OutputKey==`UploadedDocumentBucketName`].OutputValue' \
  --output text)

echo "=================================================="
echo "Received Emails in S3: ${BUCKET_NAME}"
echo "=================================================="

aws s3 ls "s3://${BUCKET_NAME}/emails/" --recursive --human-readable | tail -n ${LIMIT}

echo ""
echo "To download an email:"
echo "  aws s3 cp s3://${BUCKET_NAME}/emails/[key] email.eml"
echo ""
echo "To view email metadata (DynamoDB):"
echo "  ./scripts/query-email-metadata.sh"
