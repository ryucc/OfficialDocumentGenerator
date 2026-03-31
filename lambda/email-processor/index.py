import json
import os
import boto3
import email
from email import policy
from datetime import datetime
from uuid import uuid4

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
ses = boto3.client('ses')


def extract_email_metadata(bucket, key):
    """
    Fetch email from S3 and extract subject and sender.
    Returns a dict with subject and sender, or defaults if extraction fails.
    """
    try:
        response = s3.get_object(Bucket=bucket, Key=key)
        email_content = response['Body'].read()

        # Parse email
        msg = email.message_from_bytes(email_content, policy=policy.default)
        subject = msg.get('Subject', 'Untitled Email')
        sender = msg.get('From', '')

        # Decode subject if needed
        if subject:
            subject = str(subject).strip()
        else:
            subject = 'Untitled Email'

        return {
            'subject': subject,
            'sender': sender
        }
    except Exception as e:
        print(f"Error extracting email metadata: {str(e)}")
        return {
            'subject': 'Untitled Email',
            'sender': ''
        }


def send_success_reply(to_email, original_subject):
    """
    Send a success confirmation reply to the sender.
    """
    sender_email = os.environ.get('SENDER_EMAIL', 'ai@gongwengpt.click')

    try:
        response = ses.send_email(
            Source=sender_email,
            Destination={'ToAddresses': [to_email]},
            Message={
                'Subject': {
                    'Data': f'Re: {original_subject}',
                    'Charset': 'UTF-8'
                },
                'Body': {
                    'Text': {
                        'Data': 'Success',
                        'Charset': 'UTF-8'
                    }
                }
            }
        )
        print(f"Sent success reply to {to_email}, MessageId: {response['MessageId']}")
        return True
    except Exception as e:
        print(f"Error sending reply to {to_email}: {str(e)}")
        # Don't raise - we don't want to fail the entire processing if reply fails
        return False


def lambda_handler(event, context):
    """
    Process SQS messages triggered by S3 email uploads.
    Creates a project item in DynamoDB for each email.
    """
    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    for record in event['Records']:
        try:
            # Parse SQS message body (contains S3 event)
            message_body = json.loads(record['body'])

            # Handle SNS wrapped messages or direct S3 events
            if 'Message' in message_body:
                s3_event = json.loads(message_body['Message'])
            else:
                s3_event = message_body

            # Extract S3 event details
            for s3_record in s3_event.get('Records', []):
                bucket = s3_record['s3']['bucket']['name']
                key = s3_record['s3']['object']['key']

                print(f"Processing email from S3: s3://{bucket}/{key}")

                # Extract email metadata
                email_metadata = extract_email_metadata(bucket, key)
                email_subject = email_metadata['subject']
                email_sender = email_metadata['sender']
                print(f"Email subject: {email_subject}, sender: {email_sender}")

                # Generate project ID
                project_id = str(uuid4())
                timestamp = datetime.utcnow().isoformat()

                # Create project item in DynamoDB
                item = {
                    'projectId': project_id,
                    'name': email_subject,  # Initial name is email subject
                    'status': 'in_progress',
                    'emailS3Key': key,
                    'emailS3Bucket': bucket,
                    'createdAt': timestamp,
                    'updatedAt': timestamp
                }

                table.put_item(Item=item)

                print(f"Created project {project_id} for email {key}")

                # Send success reply
                if email_sender:
                    send_success_reply(email_sender, email_subject)

        except Exception as e:
            print(f"Error processing message: {str(e)}")
            print(f"Message body: {record.get('body', 'N/A')}")
            # Re-raise to send message to DLQ
            raise

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed emails')
    }
