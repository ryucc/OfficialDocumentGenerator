#!/bin/bash

# Update the email sender allowlist
# Usage: ./scripts/update-allowlist.sh "email1@example.com,email2@example.com"

set -e

STAGE=${2:-test}
STACK_NAME="official-doc-generator-app-${STAGE}"
REGION=${AWS_REGION:-us-east-1}
ALLOWED_SENDERS=$1

if [ -z "$ALLOWED_SENDERS" ]; then
  echo "Usage: $0 \"email1@example.com,email2@example.com\" [stage]"
  echo ""
  echo "Current allowlist:"
  aws cloudformation describe-stacks \
    --stack-name ${STACK_NAME} \
    --region ${REGION} \
    --query 'Stacks[0].Parameters[?ParameterKey==`AllowedEmailSenders`].ParameterValue' \
    --output text
  exit 1
fi

echo "Updating allowlist to: ${ALLOWED_SENDERS}"
echo "Stack: ${STACK_NAME}"

# Get current parameter values
CURRENT_PARAMS=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Parameters' \
  --output json)

CURRENT_STAGE=$(echo $CURRENT_PARAMS | jq -r '.[] | select(.ParameterKey=="Stage") | .ParameterValue')

# Update the stack with new allowlist
sam deploy \
  --template-file infra/app-only.yaml \
  --stack-name ${STACK_NAME} \
  --parameter-overrides \
    Stage=${CURRENT_STAGE} \
    AllowedEmailSenders="${ALLOWED_SENDERS}" \
  --capabilities CAPABILITY_IAM CAPABILITY_AUTO_EXPAND \
  --region ${REGION} \
  --no-fail-on-empty-changeset

echo "✅ Allowlist updated successfully!"
