import json
import os
import boto3
from datetime import datetime, timedelta
from email import policy
from email.parser import BytesParser

s3 = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')


def lambda_handler(event, context):
    table_name = os.environ['TABLE_NAME']
    table = dynamodb.Table(table_name)

    for record in event['Records']:
        bucket = record['s3']['bucket']['name']
        key = record['s3']['object']['key']

        print(f"Processing email: s3://{bucket}/{key}")

        try:
            # Get email from S3
            response = s3.get_object(Bucket=bucket, Key=key)
            email_content = response['Body'].read()

            # Parse email
            msg = BytesParser(policy=policy.default).parsebytes(email_content)

            # Extract metadata
            message_id = msg.get('Message-ID', key)
            subject = msg.get('Subject', '')
            sender = msg.get('From', '')
            recipients = msg.get('To', '')
            received_at = datetime.utcnow().isoformat()

            # Calculate TTL (90 days from now)
            ttl = int((datetime.utcnow() + timedelta(days=90)).timestamp())

            # Store metadata in DynamoDB
            table.put_item(
                Item={
                    'messageId': message_id,
                    'receivedAt': received_at,
                    'subject': subject,
                    'from': sender,
                    'to': recipients,
                    's3Bucket': bucket,
                    's3Key': key,
                    'ttl': ttl
                }
            )

            print(f"Stored metadata for message: {message_id}")

        except Exception as e:
            print(f"Error processing email: {str(e)}")
            raise

    return {'statusCode': 200}
