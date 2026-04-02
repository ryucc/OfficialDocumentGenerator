import json
import os
import boto3
import email
from email import policy
from email.mime.text import MIMEText
from email.utils import parseaddr
from boto3.dynamodb.conditions import Key
from datetime import datetime
from uuid import uuid4

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
ses = boto3.client('ses')


def extract_email_metadata(bucket, key):
    """
    Fetch email from S3 and extract subject, sender, Message-ID, and In-Reply-To.
    """
    try:
        response = s3.get_object(Bucket=bucket, Key=key)
        email_content = response['Body'].read()

        msg = email.message_from_bytes(email_content, policy=policy.default)
        subject = msg.get('Subject', 'Untitled Email')
        sender_header = msg.get('From', '')
        message_id = msg.get('Message-ID', '').strip()
        in_reply_to = msg.get('In-Reply-To', '').strip()

        if subject:
            subject = str(subject).strip()
        else:
            subject = 'Untitled Email'

        sender_name, sender_email = parseaddr(sender_header)
        sender_email = sender_email.strip() if sender_email else ''

        return {
            'subject': subject,
            'sender': sender_email,
            'messageId': message_id,
            'inReplyTo': in_reply_to,
        }
    except Exception as e:
        print(f"Error extracting email metadata: {str(e)}")
        return {
            'subject': 'Untitled Email',
            'sender': '',
            'messageId': '',
            'inReplyTo': '',
        }


def find_project_by_ses_message_id(table, ses_message_id):
    """Look up a project by the sesMessageId GSI."""
    try:
        response = table.query(
            IndexName='SesMessageIdIndex',
            KeyConditionExpression=Key('sesMessageId').eq(ses_message_id),
            Limit=1
        )
        items = response.get('Items', [])
        return items[0] if items else None
    except Exception as e:
        print(f"Error querying SesMessageIdIndex: {str(e)}")
        return None


def send_success_reply(to_email, original_subject, original_message_id=None):
    """
    Send a success confirmation reply.
    Sets In-Reply-To and References headers so Gmail groups it as a thread.
    Returns the formatted SES Message-ID for thread tracking.
    """
    sender_email = os.environ.get('SENDER_EMAIL', 'ai@gongwengpt.click')

    try:
        msg = MIMEText('Success', 'plain', 'utf-8')
        msg['From'] = sender_email
        msg['To'] = to_email
        msg['Subject'] = f'Re: {original_subject}'
        if original_message_id:
            msg['In-Reply-To'] = original_message_id
            msg['References'] = original_message_id

        response = ses.send_raw_email(
            Source=sender_email,
            Destinations=[to_email],
            RawMessage={'Data': msg.as_bytes()}
        )
        ses_message_id = response['MessageId']
        formatted_id = f'<{ses_message_id}@email.amazonses.com>'
        print(f"Sent success reply to {to_email}, MessageId: {ses_message_id}")
        return formatted_id
    except Exception as e:
        print(f"Error sending reply to {to_email}: {str(e)}")
        return None


def lambda_handler(event, context):
    """
    Process SQS messages triggered by S3 email uploads.
    - If the email is a reply to an existing thread, links it to the parent project.
    - Otherwise creates a new project and stores the outbound sesMessageId for future threading.
    """
    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    for record in event['Records']:
        try:
            message_body = json.loads(record['body'])

            if 'Message' in message_body:
                s3_event = json.loads(message_body['Message'])
            else:
                s3_event = message_body

            for s3_record in s3_event.get('Records', []):
                bucket = s3_record['s3']['bucket']['name']
                key = s3_record['s3']['object']['key']

                print(f"Processing email from S3: s3://{bucket}/{key}")

                email_metadata = extract_email_metadata(bucket, key)
                email_subject = email_metadata['subject']
                email_sender = email_metadata['sender']
                email_message_id = email_metadata['messageId']
                in_reply_to = email_metadata['inReplyTo']
                print(f"Email subject: {email_subject}, sender: {email_sender}, in_reply_to: {in_reply_to}")

                timestamp = datetime.utcnow().isoformat()

                # Check if this is a reply to an existing thread
                parent_project = None
                if in_reply_to:
                    parent_project = find_project_by_ses_message_id(table, in_reply_to)

                if parent_project:
                    project_id = parent_project['projectId']
                    print(f"Linked reply email {key} to existing project {project_id}")
                    table.update_item(
                        Key={'projectId': project_id},
                        UpdateExpression='SET replyEmailS3Key = :key, updatedAt = :ts',
                        ExpressionAttributeValues={
                            ':key': key,
                            ':ts': timestamp,
                        }
                    )
                else:
                    # New email: create a project
                    project_id = str(uuid4())
                    item = {
                        'projectId': project_id,
                        'name': email_subject,
                        'status': 'in_progress',
                        'emailS3Key': key,
                        'emailS3Bucket': bucket,
                        'createdAt': timestamp,
                        'updatedAt': timestamp,
                    }
                    table.put_item(Item=item)
                    print(f"Created project {project_id} for email {key}")

                    # Send reply and store its Message-ID for future thread matching
                    if email_sender:
                        ses_message_id = send_success_reply(email_sender, email_subject, email_message_id)
                        if ses_message_id:
                            table.update_item(
                                Key={'projectId': project_id},
                                UpdateExpression='SET sesMessageId = :mid',
                                ExpressionAttributeValues={':mid': ses_message_id}
                            )

        except Exception as e:
            print(f"Error processing message: {str(e)}")
            print(f"Message body: {record.get('body', 'N/A')}")
            raise

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed emails')
    }
