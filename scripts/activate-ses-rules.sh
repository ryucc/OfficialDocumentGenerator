#!/bin/bash

# Activate SES receipt rule set
# Usage: ./scripts/activate-ses-rules.sh [stage]

set -e

STAGE=${1:-test}
STACK_NAME="official-doc-generator-app-${STAGE}"
REGION=${AWS_REGION:-us-east-1}

echo "=================================================="
echo "Activating SES Receipt Rule Set"
echo "=================================================="

# Get rule set name from stack output
RULE_SET_NAME=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[?OutputKey==`EmailReceiptRuleSetName`].OutputValue' \
  --output text)

if [ -z "$RULE_SET_NAME" ]; then
  echo "❌ Error: Could not find rule set name in stack outputs"
  echo "Make sure the stack is deployed first: ./scripts/deploy-with-email.sh"
  exit 1
fi

echo "Rule set name: ${RULE_SET_NAME}"
echo ""

# Set as active
aws ses set-active-receipt-rule-set \
  --rule-set-name ${RULE_SET_NAME} \
  --region ${REGION}

echo "✅ Rule set activated successfully!"
echo ""

# Show current active rule set
echo "Current active rule set:"
aws ses describe-active-receipt-rule-set \
  --region ${REGION} \
  --query 'Metadata.Name' \
  --output text
