import json
import os
import boto3
from datetime import datetime
from uuid import uuid4
from email import message_from_bytes
from email.policy import default

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
lambda_client = boto3.client('lambda')


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

                # Parse email and extract metadata
                email_data = parse_email(bucket, key)

                # Generate project ID
                project_id = str(uuid4())
                timestamp = datetime.utcnow().isoformat()

                # Create project item in DynamoDB
                item = {
                    'projectId': project_id,
                    'status': 'pending_generation',
                    'emailS3Key': key,
                    'emailS3Bucket': bucket,
                    'emailSubject': email_data.get('subject', ''),
                    'emailFrom': email_data.get('from', ''),
                    'emailBody': email_data.get('body', ''),
                    'attachmentKeys': email_data.get('attachment_keys', []),
                    'createdAt': timestamp,
                    'updatedAt': timestamp
                }

                table.put_item(Item=item)

                print(f"Created project {project_id} for email {key}")
                print(f"Email subject: {email_data.get('subject')}")
                print(f"Attachments: {len(email_data.get('attachment_keys', []))}")

                # Trigger document generation Lambda
                trigger_document_generation(project_id)

        except Exception as e:
            print(f"Error processing message: {str(e)}")
            print(f"Message body: {record.get('body', 'N/A')}")
            # Re-raise to send message to DLQ
            raise

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed emails')
    }


def parse_email(bucket, key):
    """Parse email from S3 and extract attachments."""
    try:
        # Download email from S3
        response = s3.get_object(Bucket=bucket, Key=key)
        email_bytes = response['Body'].read()

        # Parse email
        msg = message_from_bytes(email_bytes, policy=default)

        # Extract metadata
        subject = msg.get('Subject', '')
        from_addr = msg.get('From', '')

        # Extract body
        body = extract_email_body(msg)

        # Extract and save attachments
        attachment_keys = extract_attachments(bucket, key, msg)

        return {
            'subject': subject,
            'from': from_addr,
            'body': body,
            'attachment_keys': attachment_keys
        }
    except Exception as e:
        print(f"Error parsing email {key}: {str(e)}")
        return {
            'subject': '',
            'from': '',
            'body': '',
            'attachment_keys': []
        }


def extract_email_body(msg):
    """Extract the text body from an email message."""
    body = ""

    if msg.is_multipart():
        for part in msg.walk():
            content_type = part.get_content_type()
            content_disposition = str(part.get('Content-Disposition', ''))

            # Skip attachments
            if 'attachment' in content_disposition:
                continue

            # Extract text/plain or text/html
            if content_type == 'text/plain':
                try:
                    body = part.get_content()
                    break
                except:
                    pass
            elif content_type == 'text/html' and not body:
                try:
                    body = part.get_content()
                except:
                    pass
    else:
        try:
            body = msg.get_content()
        except:
            pass

    return body.strip() if body else ""


def extract_attachments(bucket, email_key, msg):
    """Extract and save email attachments to S3 (only supported file types)."""
    attachment_keys = []

    # Supported file extensions
    SUPPORTED_EXTENSIONS = {'.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png'}

    # Generate attachment prefix from email key
    # email_key format: emails/{stage}/{messageId}
    # attachment prefix: email-attachments/{stage}/{messageId}/
    email_parts = email_key.split('/')
    if len(email_parts) >= 3:
        stage = email_parts[1]
        message_id = email_parts[2]
        attachment_prefix = f"email-attachments/{stage}/{message_id}/"
    else:
        attachment_prefix = f"email-attachments/{email_key}/"

    if not msg.is_multipart():
        return attachment_keys

    attachment_count = 0
    for part in msg.walk():
        content_disposition = str(part.get('Content-Disposition', ''))

        # Check if this part is an attachment
        if 'attachment' not in content_disposition:
            continue

        filename = part.get_filename()
        if not filename:
            attachment_count += 1
            filename = f"attachment_{attachment_count}"

        # Check if file type is supported
        file_ext = '.' + filename.lower().rsplit('.', 1)[-1] if '.' in filename else ''
        if file_ext not in SUPPORTED_EXTENSIONS:
            print(f"Skipping unsupported file type: {filename}")
            continue

        try:
            # Get attachment content
            attachment_data = part.get_content()
            if isinstance(attachment_data, str):
                attachment_data = attachment_data.encode()

            # Save to S3
            attachment_key = attachment_prefix + filename
            s3.put_object(
                Bucket=bucket,
                Key=attachment_key,
                Body=attachment_data,
                ContentType=part.get_content_type() or 'application/octet-stream'
            )

            attachment_keys.append(attachment_key)
            print(f"Saved attachment: {attachment_key}")

        except Exception as e:
            print(f"Error extracting attachment {filename}: {str(e)}")
            continue

    return attachment_keys


def trigger_document_generation(project_id):
    """Trigger the document generator Lambda asynchronously."""
    function_name = os.environ.get('DOCUMENT_GENERATOR_FUNCTION_NAME')
    if not function_name:
        print("DOCUMENT_GENERATOR_FUNCTION_NAME not set, skipping document generation")
        return

    try:
        # Invoke asynchronously (Event invocation type)
        lambda_client.invoke(
            FunctionName=function_name,
            InvocationType='Event',  # Async invocation
            Payload=json.dumps({'projectId': project_id})
        )
        print(f"Triggered document generation for project: {project_id}")
    except Exception as e:
        print(f"Error triggering document generation: {str(e)}")
        # Don't raise - generation trigger failure shouldn't fail the email processing
