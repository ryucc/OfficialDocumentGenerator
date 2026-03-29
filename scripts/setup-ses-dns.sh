#!/bin/bash

# Setup SES DNS records in Route 53
# Usage: ./scripts/setup-ses-dns.sh

set -e

DOMAIN="gongwengpt.click"
REGION=${AWS_REGION:-us-east-1}

echo "=================================================="
echo "Setting up SES DNS records for ${DOMAIN}"
echo "=================================================="

# Get the hosted zone ID for the domain
HOSTED_ZONE_ID=$(aws route53 list-hosted-zones \
  --query "HostedZones[?Name=='${DOMAIN}.'].Id" \
  --output text | cut -d'/' -f3)

if [ -z "$HOSTED_ZONE_ID" ]; then
  echo "❌ Error: Hosted zone not found for domain ${DOMAIN}"
  echo "Please ensure the domain is configured in Route 53"
  exit 1
fi

echo "Found hosted zone: ${HOSTED_ZONE_ID}"

# Get SES verification token
echo ""
echo "Getting SES verification token..."
VERIFICATION_TOKEN=$(aws ses verify-domain-identity \
  --domain ${DOMAIN} \
  --region ${REGION} \
  --query 'VerificationToken' \
  --output text)

echo "Verification token: ${VERIFICATION_TOKEN}"

# Create change batch JSON for Route 53
CHANGE_BATCH=$(cat <<EOF
{
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "_amazonses.${DOMAIN}",
        "Type": "TXT",
        "TTL": 300,
        "ResourceRecords": [
          {
            "Value": "\"${VERIFICATION_TOKEN}\""
          }
        ]
      }
    },
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${DOMAIN}",
        "Type": "MX",
        "TTL": 300,
        "ResourceRecords": [
          {
            "Value": "10 inbound-smtp.${REGION}.amazonaws.com"
          }
        ]
      }
    }
  ]
}
EOF
)

echo ""
echo "Creating DNS records in Route 53..."
CHANGE_ID=$(aws route53 change-resource-record-sets \
  --hosted-zone-id ${HOSTED_ZONE_ID} \
  --change-batch "$CHANGE_BATCH" \
  --query 'ChangeInfo.Id' \
  --output text)

echo "Change ID: ${CHANGE_ID}"
echo ""
echo "✅ DNS records created successfully!"
echo ""
echo "Records added:"
echo "  1. TXT record: _amazonses.${DOMAIN} = ${VERIFICATION_TOKEN}"
echo "  2. MX record: ${DOMAIN} = 10 inbound-smtp.${REGION}.amazonaws.com"
echo ""
echo "⏳ DNS propagation may take a few minutes..."
echo ""
echo "To check domain verification status:"
echo "  aws ses get-identity-verification-attributes --identities ${DOMAIN}"
echo ""
echo "To verify DNS records:"
echo "  dig TXT _amazonses.${DOMAIN}"
echo "  dig MX ${DOMAIN}"
