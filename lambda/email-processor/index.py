import json
import os
import boto3
import email
from email import policy
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from email.mime.application import MIMEApplication
from email.utils import parseaddr
from boto3.dynamodb.conditions import Key
from datetime import datetime
from pathlib import Path
from uuid import uuid4

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
ses = boto3.client('ses')
bedrock = boto3.client('bedrock-runtime', region_name=os.environ.get('BEDROCK_REGION', 'us-east-1'))

CLAUDE_MODEL_ID = os.environ.get('CLAUDE_MODEL_ID', 'anthropic.claude-sonnet-4-20250514-v1:0')


def load_prompt(name: str) -> str:
    path = Path(__file__).parent / 'prompts' / f'{name}.md'
    return path.read_text(encoding='utf-8')


def extract_email_metadata(bucket, key):
    """Fetch email from S3 and extract subject, sender, Message-ID, In-Reply-To, and body text."""
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

        # Extract body text
        body_text = ''
        if msg.is_multipart():
            for part in msg.walk():
                if part.get_content_type() == 'text/plain':
                    body_text = part.get_content()
                    break
                elif part.get_content_type() == 'text/html' and not body_text:
                    body_text = part.get_content()
        else:
            body_text = msg.get_content()

        return {
            'subject': subject,
            'sender': sender_email,
            'messageId': message_id,
            'inReplyTo': in_reply_to,
            'bodyText': body_text or '',
        }
    except Exception as e:
        print(f"Error extracting email metadata: {str(e)}")
        return {
            'subject': 'Untitled Email',
            'sender': '',
            'messageId': '',
            'inReplyTo': '',
            'bodyText': '',
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


def load_thread_emails(bucket, project):
    """Load all emails in a thread and return them chronologically."""
    messages = []

    # Original email
    email_key = project.get('emailS3Key')
    if email_key:
        meta = extract_email_metadata(bucket, email_key)
        messages.append({
            'timestamp': project.get('createdAt', ''),
            'sender': meta['sender'],
            'body': meta['bodyText'],
            'direction': 'inbound',
        })

    # Reply email (user's follow-up)
    reply_key = project.get('replyEmailS3Key')
    if reply_key:
        meta = extract_email_metadata(bucket, reply_key)
        messages.append({
            'timestamp': project.get('updatedAt', ''),
            'sender': meta['sender'],
            'body': meta['bodyText'],
            'direction': 'inbound',
        })

    messages.sort(key=lambda m: m['timestamp'])
    return messages


def format_thread_for_prompt(messages):
    """Format email messages into a readable thread for the AI prompt."""
    lines = []
    for msg in messages:
        direction = '寄件人' if msg['direction'] == 'inbound' else '系統回覆'
        lines.append(f"[{msg['timestamp']}] {direction} {msg['sender']}:")
        lines.append(msg['body'])
        lines.append('')
    return '\n'.join(lines)


def call_claude(thread_text):
    """Call Claude via Bedrock to extract fields from the email thread."""
    system_prompt = load_prompt('extract_fields')

    response = bedrock.invoke_model(
        modelId=CLAUDE_MODEL_ID,
        contentType='application/json',
        accept='application/json',
        body=json.dumps({
            'anthropic_version': 'bedrock-2023-05-31',
            'max_tokens': 4096,
            'system': system_prompt,
            'messages': [
                {
                    'role': 'user',
                    'content': f'以下是郵件對話串，請擷取公文所需資訊：\n\n{thread_text}',
                }
            ],
        }),
    )

    result = json.loads(response['body'].read())
    content = result['content'][0]['text']

    # Parse JSON from response (handle markdown code blocks)
    if '```json' in content:
        content = content.split('```json')[1].split('```')[0]
    elif '```' in content:
        content = content.split('```')[1].split('```')[0]

    return json.loads(content)


def generate_docx(fields_json, project_id):
    """Invoke the Java document generator Lambda to produce a .docx file."""
    lambda_client = boto3.client('lambda')
    generator_function = os.environ.get('DOCUMENT_GENERATOR_FUNCTION', '')

    if not generator_function:
        print("DOCUMENT_GENERATOR_FUNCTION not set, skipping docx generation")
        return None

    response = lambda_client.invoke(
        FunctionName=generator_function,
        InvocationType='RequestResponse',
        Payload=json.dumps({
            'projectId': project_id,
            'documentData': fields_json,
        }),
    )

    payload = json.loads(response['Payload'].read())
    if response.get('FunctionError'):
        print(f"Document generator error: {payload}")
        return None

    return payload


def send_reply(to_email, subject, body_text, original_message_id=None, attachment=None):
    """
    Send an email reply. Optionally attach a file.
    Returns the formatted SES Message-ID for thread tracking.
    """
    sender_email = os.environ.get('SENDER_EMAIL', 'ai@gongwengpt.click')

    try:
        if attachment:
            msg = MIMEMultipart()
            msg['From'] = sender_email
            msg['To'] = to_email
            msg['Subject'] = f'Re: {subject}'
            if original_message_id:
                msg['In-Reply-To'] = original_message_id
                msg['References'] = original_message_id

            msg.attach(MIMEText(body_text, 'plain', 'utf-8'))

            att = MIMEApplication(attachment['data'])
            att.add_header('Content-Disposition', 'attachment', filename=attachment['filename'])
            msg.attach(att)
        else:
            msg = MIMEText(body_text, 'plain', 'utf-8')
            msg['From'] = sender_email
            msg['To'] = to_email
            msg['Subject'] = f'Re: {subject}'
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
        print(f"Sent reply to {to_email}, MessageId: {ses_message_id}")
        return formatted_id
    except Exception as e:
        print(f"Error sending reply to {to_email}: {str(e)}")
        return None


def lambda_handler(event, context):
    """
    Process SQS messages triggered by S3 email uploads.
    - If reply to existing thread: load full thread, re-assess completeness
    - If new email: create project, assess completeness
    - If complete: generate docx, reply with attachment
    - If incomplete: reply asking for missing info
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
                    # Existing thread: link reply email to project
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
                    project = {**parent_project, 'replyEmailS3Key': key, 'updatedAt': timestamp}
                else:
                    # New email: create project
                    project_id = str(uuid4())
                    project = {
                        'projectId': project_id,
                        'name': email_subject,
                        'status': 'in_progress',
                        'emailS3Key': key,
                        'emailS3Bucket': bucket,
                        'createdAt': timestamp,
                        'updatedAt': timestamp,
                    }
                    table.put_item(Item=project)
                    print(f"Created project {project_id} for email {key}")

                # Load full thread and call AI
                messages = load_thread_emails(bucket, project)
                thread_text = format_thread_for_prompt(messages)
                print(f"Thread has {len(messages)} message(s)")

                ai_result = call_claude(thread_text)
                is_complete = ai_result.get('complete', False)
                print(f"AI assessment: complete={is_complete}, missing={ai_result.get('missing', [])}")

                if is_complete:
                    # Generate document
                    fields = ai_result['fields']
                    docx_result = generate_docx(fields, project_id)

                    reply_body = '您好，公文已根據您提供的資訊產生完成，請查收附件。'
                    attachment = None
                    if docx_result and docx_result.get('s3Key'):
                        # Download generated docx from S3
                        try:
                            docx_obj = s3.get_object(
                                Bucket=docx_result['bucket'],
                                Key=docx_result['s3Key']
                            )
                            attachment = {
                                'data': docx_obj['Body'].read(),
                                'filename': docx_result.get('filename', '公文.docx'),
                            }
                        except Exception as e:
                            print(f"Error downloading generated docx: {e}")
                            reply_body = '您好，公文已產生但附件下載失敗，請至系統中下載。'

                    ses_message_id = send_reply(
                        email_sender, email_subject, reply_body,
                        email_message_id, attachment
                    )

                    # Update project status
                    update_expr = 'SET #status = :status, updatedAt = :ts'
                    expr_values = {':status': 'finished', ':ts': timestamp}
                    if docx_result and docx_result.get('s3Key'):
                        update_expr += ', generatedDocumentS3Key = :docKey'
                        expr_values[':docKey'] = docx_result['s3Key']
                    if ses_message_id:
                        update_expr += ', sesMessageId = :mid'
                        expr_values[':mid'] = ses_message_id

                    table.update_item(
                        Key={'projectId': project_id},
                        UpdateExpression=update_expr,
                        ExpressionAttributeNames={'#status': 'status'},
                        ExpressionAttributeValues=expr_values,
                    )
                else:
                    # Reply asking for missing info
                    reply_text = ai_result.get('reply', '您好，請提供更多資訊以便產生公文。')
                    ses_message_id = send_reply(
                        email_sender, email_subject, reply_text,
                        email_message_id
                    )

                    if ses_message_id:
                        table.update_item(
                            Key={'projectId': project_id},
                            UpdateExpression='SET sesMessageId = :mid, updatedAt = :ts',
                            ExpressionAttributeValues={
                                ':mid': ses_message_id,
                                ':ts': timestamp,
                            }
                        )

        except Exception as e:
            print(f"Error processing message: {str(e)}")
            print(f"Message body: {record.get('body', 'N/A')}")
            raise

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed emails')
    }
