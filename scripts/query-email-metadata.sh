#!/bin/bash

# Query email metadata from DynamoDB
# Usage: ./scripts/query-email-metadata.sh [limit] [stage]

set -e

LIMIT=${1:-10}
STAGE=${2:-test}
STACK_NAME="official-doc-generator-app-${STAGE}"
REGION=${AWS_REGION:-us-east-1}

# Get table name from stack output
TABLE_NAME=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[?OutputKey==`ReceivedEmailTableName`].OutputValue' \
  --output text)

echo "=================================================="
echo "Recent Emails (from DynamoDB)"
echo "=================================================="

aws dynamodb scan \
  --table-name ${TABLE_NAME} \
  --region ${REGION} \
  --max-items ${LIMIT} \
  --query 'Items[*].[receivedAt.S, from.S, subject.S, s3Key.S]' \
  --output table

echo ""
echo "For full details, query the table directly:"
echo "  aws dynamodb scan --table-name ${TABLE_NAME}"
