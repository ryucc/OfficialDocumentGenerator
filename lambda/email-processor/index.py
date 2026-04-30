import io
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
from botocore.config import Config
from datetime import datetime
from pathlib import Path
from uuid import uuid4

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
ses = boto3.client('ses')
cognito = boto3.client('cognito-idp')
textract = boto3.client('textract', region_name=os.environ.get('TEXTRACT_REGION', 'ap-southeast-1'))
bedrock = boto3.client(
    'bedrock-runtime',
    region_name=os.environ.get('BEDROCK_REGION', 'us-east-1'),
    config=Config(read_timeout=870, connect_timeout=10, retries={'max_attempts': 1}),
)

CLAUDE_MODEL_ID = os.environ.get('CLAUDE_MODEL_ID', 'anthropic.claude-sonnet-4-20250514-v1:0')


def load_prompt(name: str) -> str:
    path = Path(__file__).parent / 'prompts' / f'{name}.md'
    return path.read_text(encoding='utf-8')


def get_cognito_user_id(email):
    """Look up the Cognito userId (sub) for a given email."""
    user_pool_id = os.environ.get('USER_POOL_ID', '')
    if not user_pool_id:
        print("USER_POOL_ID not set, cannot look up user")
        return None
    try:
        response = cognito.list_users(
            UserPoolId=user_pool_id,
            Filter=f'email = "{email}"',
            Limit=1,
        )
        users = response.get('Users', [])
        if users:
            return users[0]['Username']
        return None
    except Exception as e:
        print(f"Error looking up Cognito user for {email}: {e}")
        return None


def get_installed_skills(user_id):
    """Get the list of installed skill IDs for a user."""
    users_table_name = os.environ.get('USERS_TABLE', '')
    if not users_table_name:
        print("USERS_TABLE not set")
        return []
    try:
        users_table = dynamodb.Table(users_table_name)
        response = users_table.get_item(
            Key={'userId': user_id},
            ProjectionExpression='installedSkills',
        )
        item = response.get('Item')
        if item and 'installedSkills' in item:
            return list(item['installedSkills'])
        return []
    except Exception as e:
        print(f"Error getting installed skills for {user_id}: {e}")
        return []


def load_skill_details(skill_ids):
    """Load skill metadata from DynamoDB and instructions from S3."""
    skills_table_name = os.environ.get('SKILLS_TABLE', '')
    bucket = os.environ.get('SKILLS_BUCKET', '')
    if not skills_table_name:
        print("SKILLS_TABLE not set")
        return []

    skills_table = dynamodb.Table(skills_table_name)
    skills = []
    for skill_id in skill_ids:
        try:
            response = skills_table.get_item(Key={'skillId': skill_id})
            item = response.get('Item')
            if not item:
                continue

            # Load instructions from S3
            instructions = ''
            if bucket:
                try:
                    obj = s3.get_object(
                        Bucket=bucket,
                        Key=f'skills/{skill_id}/instructions.md',
                    )
                    instructions = obj['Body'].read().decode('utf-8')
                except Exception as e:
                    print(f"Error loading instructions for {skill_id}: {e}")

            skills.append({
                'skillId': skill_id,
                'name': item.get('name', ''),
                'displayName': item.get('displayName', ''),
                'schemaJson': item.get('schemaJson', ''),
                'instructions': instructions,
            })
        except Exception as e:
            print(f"Error loading skill {skill_id}: {e}")

    return skills


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


def call_claude(thread_text, skill):
    """Call Claude via Bedrock to extract fields from the email thread using a specific skill."""
    # Use skill-specific instructions if available, fall back to default
    if skill and skill.get('instructions'):
        system_prompt = skill['instructions']
    else:
        system_prompt = load_prompt('extract_fields')

    response = bedrock.invoke_model(
        modelId=CLAUDE_MODEL_ID,
        contentType='application/json',
        accept='application/json',
        body=json.dumps({
            'anthropic_version': 'bedrock-2023-05-31',
            'max_tokens': 32768,
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
    usage = result.get('usage', {}) or {}

    # Parse JSON from response (handle markdown code blocks)
    if '```json' in content:
        content = content.split('```json')[1].split('```')[0]
    elif '```' in content:
        content = content.split('```')[1].split('```')[0]

    return json.loads(content), usage


# Bedrock pricing per 1M tokens (USD). Updated when models change.
MODEL_PRICES = {
    'sonnet-4-6': {'input': 3.0, 'output': 15.0, 'cache_read': 0.30, 'cache_write': 3.75},
    'sonnet-4-5': {'input': 3.0, 'output': 15.0, 'cache_read': 0.30, 'cache_write': 3.75},
    'haiku-4-5':  {'input': 1.0, 'output':  5.0, 'cache_read': 0.10, 'cache_write': 1.25},
}


def compute_cost_usd(usage: dict, model_id: str) -> float:
    """Compute USD cost for one Bedrock invoke_model call from its usage block."""
    prices = next((v for k, v in MODEL_PRICES.items() if k in model_id), MODEL_PRICES['sonnet-4-6'])
    in_tok = usage.get('input_tokens', 0)
    out_tok = usage.get('output_tokens', 0)
    cache_read = usage.get('cache_read_input_tokens', 0)
    cache_write = usage.get('cache_creation_input_tokens', 0)
    return (
        in_tok       * prices['input']       +
        out_tok      * prices['output']      +
        cache_read   * prices['cache_read']  +
        cache_write  * prices['cache_write']
    ) / 1_000_000


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


_TEXTRACT_SUPPORTED = {
    'application/pdf',
    'image/jpeg', 'image/jpg', 'image/png', 'image/tiff', 'image/bmp',
}

_DOCX_CONTENT_TYPES = {
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    # application/msword (.doc) is old binary format, not supported by python-docx
}


def extract_docx_text(data: bytes) -> str:
    from docx import Document
    doc = Document(io.BytesIO(data))
    return '\n'.join(p.text for p in doc.paragraphs if p.text.strip())


def ocr_bytes(data: bytes, filename: str) -> str:
    """OCR a document from raw bytes using Textract sync API. Supports PDF, JPEG, PNG, TIFF up to 10MB."""
    resp = textract.detect_document_text(Document={'Bytes': data})
    lines = [b['Text'] for b in resp['Blocks'] if b['BlockType'] == 'LINE']
    print(f"Textract sync succeeded for {filename}: {len(lines)} lines extracted")
    return '\n'.join(lines)


def extract_and_ocr_attachments(bucket, email_key):
    """Extract attachments from an S3-stored email, OCR them via Textract.

    Returns (ocr_text, skipped_filenames).
    """
    try:
        response = s3.get_object(Bucket=bucket, Key=email_key)
        msg = email.message_from_bytes(response['Body'].read(), policy=policy.default)
    except Exception as e:
        print(f"Error reading email for attachments: {e}")
        return '', []

    ocr_texts = []
    skipped = []
    for part in msg.walk():
        filename = part.get_filename()
        if not filename:
            continue
        content_type = part.get_content_type()
        if content_type not in _TEXTRACT_SUPPORTED and content_type not in _DOCX_CONTENT_TYPES:
            import mimetypes
            guessed, _ = mimetypes.guess_type(filename)
            if guessed:
                print(f"Attachment {filename}: email reported '{content_type}', overriding with '{guessed}' from extension")
                content_type = guessed
        print(f"Attachment found: {filename} ({content_type})")
        data = part.get_payload(decode=True)
        if not data:
            continue

        if content_type in _DOCX_CONTENT_TYPES:
            try:
                text = extract_docx_text(data)
                if text:
                    print(f"Extracted {len(text)} chars from docx attachment {filename}")
                    ocr_texts.append(f"[附件: {filename}]\n{text}")
            except Exception as e:
                print(f"Error extracting docx {filename}: {e}")
                skipped.append(filename)

        elif content_type in _TEXTRACT_SUPPORTED:
            try:
                print(f"OCR-ing attachment {filename} ({len(data)} bytes) via Textract sync API")
                text = ocr_bytes(data, filename)
                if text:
                    ocr_texts.append(f"[附件: {filename}]\n{text}")
            except Exception as e:
                print(f"Error OCR-ing attachment {filename}: {e}")
                skipped.append(filename)

        else:
            print(f"Skipping unsupported attachment: {filename} ({content_type})")
            skipped.append(filename)

    return '\n\n'.join(ocr_texts), skipped


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

                # Look up sender's installed skills
                user_id = get_cognito_user_id(email_sender)
                skills = []
                if user_id:
                    skill_ids = get_installed_skills(user_id)
                    print(f"User {email_sender} ({user_id}) has {len(skill_ids)} installed skill(s): {skill_ids}")
                    if skill_ids:
                        skills = load_skill_details(skill_ids)
                else:
                    print(f"No Cognito user found for {email_sender}")

                if not skills:
                    # No skills installed — reply and stop
                    print(f"No installed skills for {email_sender}, sending notice")
                    send_reply(
                        email_sender, email_subject,
                        '您好，您目前尚未安裝任何技能，請先至系統中安裝技能後再寄信。',
                        email_message_id,
                    )
                    continue

                # Load full thread
                messages = load_thread_emails(bucket, project)
                thread_text = format_thread_for_prompt(messages)
                print(f"Thread has {len(messages)} message(s)")

                # OCR any attachments across all emails in the thread
                attachment_texts = []
                skipped_attachments = []
                for email_s3_key in [project.get('emailS3Key'), project.get('replyEmailS3Key')]:
                    if email_s3_key:
                        text, skipped = extract_and_ocr_attachments(bucket, email_s3_key)
                        if text:
                            attachment_texts.append(text)
                        skipped_attachments.extend(skipped)
                if attachment_texts:
                    thread_text += '\n\n[附件內容]\n' + '\n\n'.join(attachment_texts)

                # Process each installed skill
                all_complete = True
                all_missing = []
                all_replies = []
                attachments = []
                total_cost_usd = 0.0

                for skill in skills:
                    print(f"Processing skill: {skill['skillId']} ({skill.get('displayName', skill['name'])})")
                    ai_result, usage = call_claude(thread_text, skill)
                    skill_cost = compute_cost_usd(usage, CLAUDE_MODEL_ID)
                    total_cost_usd += skill_cost
                    print(f"Skill {skill['skillId']}: usage={usage}, cost=${skill_cost:.4f}")
                    is_complete = ai_result.get('complete', False)
                    print(f"Skill {skill['skillId']}: complete={is_complete}, missing={ai_result.get('missing', [])}")

                    if is_complete:
                        fields = ai_result['fields']
                        docx_result = generate_docx(fields, project_id)

                        if docx_result and docx_result.get('s3Key'):
                            try:
                                docx_obj = s3.get_object(
                                    Bucket=docx_result['bucket'],
                                    Key=docx_result['s3Key']
                                )
                                attachments.append({
                                    'data': docx_obj['Body'].read(),
                                    'filename': docx_result.get('filename', '公文.docx'),
                                })
                            except Exception as e:
                                print(f"Error downloading generated docx for {skill['skillId']}: {e}")

                        if ai_result.get('reply'):
                            skill_label = skill.get('displayName') or skill['name']
                            all_replies.append(f"【{skill_label}】\n{ai_result['reply']}")
                    else:
                        all_complete = False
                        skill_label = skill.get('displayName') or skill['name']
                        missing = ai_result.get('missing', [])
                        all_missing.extend([f"[{skill_label}] {m}" for m in missing])
                        if ai_result.get('reply'):
                            all_replies.append(f"【{skill_label}】\n{ai_result['reply']}")

                skipped_notice = (
                    '\n\n注意：以下附件格式不支援，未納入處理：\n' +
                    '\n'.join(f'- {f}' for f in skipped_attachments)
                ) if skipped_attachments else ''

                if all_complete and attachments:
                    # All skills complete — reply with all attachments
                    if all_replies:
                        reply_body = '\n\n'.join(all_replies)
                    else:
                        reply_body = '您好，公文已根據您提供的資訊產生完成，請查收附件。'
                    reply_body += skipped_notice + f'\n\n---\n本次處理花費 US${total_cost_usd:.4f}'
                    ses_message_id = send_reply(
                        email_sender, email_subject, reply_body,
                        email_message_id, attachments[0] if len(attachments) == 1 else attachments[0]
                    )
                    # TODO: support multiple attachments

                    update_expr = 'SET #status = :status, updatedAt = :ts'
                    expr_values = {':status': 'finished', ':ts': timestamp}
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
                    # Some skills incomplete — reply asking for missing info
                    if all_replies:
                        reply_text = '\n\n'.join(all_replies)
                    else:
                        reply_text = '您好，請提供更多資訊以便產生公文。'
                    reply_text += skipped_notice + f'\n\n---\n本次處理花費 US${total_cost_usd:.4f}'

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
