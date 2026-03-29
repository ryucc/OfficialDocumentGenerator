import json
import os
import boto3
from datetime import datetime
import anthropic

s3 = boto3.client('s3')
textract = boto3.client('textract')
dynamodb = boto3.resource('dynamodb')


def lambda_handler(event, context):
    """
    Generate official document DOCX from email project.
    Reads gongwen-instructions, email, and attachments, then uses Claude API
    to generate structured JSON for the document exporter.
    """
    project_id = event.get('projectId')
    if not project_id:
        print("No projectId provided")
        return {'statusCode': 400, 'body': 'Missing projectId'}

    print(f"Generating document for project: {project_id}")

    try:
        # Get project from DynamoDB
        project = get_project(project_id)
        if not project:
            print(f"Project not found: {project_id}")
            return {'statusCode': 404, 'body': 'Project not found'}

        # Update status to processing
        update_project_status(project_id, 'processing')

        # Read gongwen-instructions
        instructions = read_gongwen_instructions()

        # Extract text from attachments
        attachment_texts = extract_attachment_texts(
            project.get('emailS3Bucket'),
            project.get('attachmentKeys', [])
        )

        # Generate document data using Claude API
        document_json = generate_document_with_claude(
            instructions,
            project.get('emailSubject', ''),
            project.get('emailBody', ''),
            attachment_texts
        )

        if not document_json:
            update_project_status(project_id, 'failed', 'Failed to generate document JSON')
            return {'statusCode': 500, 'body': 'Failed to generate document'}

        # Save document JSON to S3
        json_key = save_document_json(project_id, document_json)

        # TODO: Generate DOCX using Java exporter (requires separate Lambda or layer)
        # For now, we'll just save the JSON
        docx_key = f"generated-documents/{project_id}/document.docx"

        # Update project with generated document keys
        update_project_with_document(project_id, json_key, docx_key)

        print(f"Document generation completed for project {project_id}")
        return {
            'statusCode': 200,
            'body': json.dumps({
                'projectId': project_id,
                'documentJsonKey': json_key,
                'documentKey': docx_key
            })
        }

    except Exception as e:
        print(f"Error generating document for project {project_id}: {str(e)}")
        update_project_status(project_id, 'failed', str(e))
        raise


def get_project(project_id):
    """Get project from DynamoDB."""
    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    response = table.get_item(Key={'projectId': project_id})
    return response.get('Item')


def update_project_status(project_id, status, error_message=None):
    """Update project status in DynamoDB."""
    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    update_expr = 'SET #status = :status, updatedAt = :updated'
    expr_values = {
        ':status': status,
        ':updated': datetime.utcnow().isoformat()
    }
    expr_names = {'#status': 'status'}

    if error_message:
        update_expr += ', errorMessage = :error'
        expr_values[':error'] = error_message

    table.update_item(
        Key={'projectId': project_id},
        UpdateExpression=update_expr,
        ExpressionAttributeValues=expr_values,
        ExpressionAttributeNames=expr_names
    )


def read_gongwen_instructions():
    """Read the latest gongwen-instructions from S3."""
    bucket = os.environ['UPLOADED_DOCUMENT_BUCKET']
    key = 'gongwen-instructions/gongwen-instructions-latest.txt'

    try:
        response = s3.get_object(Bucket=bucket, Key=key)
        instructions = response['Body'].read().decode('utf-8')
        print(f"Read gongwen-instructions: {len(instructions)} characters")
        return instructions
    except Exception as e:
        print(f"Error reading gongwen-instructions: {str(e)}")
        return ""


def extract_attachment_texts(bucket, attachment_keys):
    """Extract text from email attachments (only supported file types)."""
    attachment_texts = []

    # Supported file extensions
    SUPPORTED_EXTENSIONS = {'.pdf', '.doc', '.docx', '.jpg', '.jpeg', '.png'}

    for key in attachment_keys:
        # Check if file type is supported
        filename = key.split('/')[-1]
        file_ext = '.' + filename.lower().rsplit('.', 1)[-1] if '.' in filename else ''

        if file_ext not in SUPPORTED_EXTENSIONS:
            print(f"Skipping unsupported file type: {filename}")
            continue

        try:
            # Download attachment
            response = s3.get_object(Bucket=bucket, Key=key)
            attachment_bytes = response['Body'].read()
            content_type = response.get('ContentType', '')

            print(f"Extracting text from attachment: {key}")

            # Extract text based on file type
            if file_ext == '.pdf' or 'pdf' in content_type.lower():
                text = extract_text_from_pdf(attachment_bytes)
            elif file_ext in {'.jpg', '.jpeg', '.png'} or 'image' in content_type.lower():
                text = extract_with_textract(attachment_bytes)
            elif file_ext in {'.doc', '.docx'} or 'word' in content_type.lower():
                text = extract_text_from_word(attachment_bytes)
            else:
                # Fallback: try Textract
                text = extract_with_textract(attachment_bytes)

            if text:
                attachment_texts.append({
                    'filename': filename,
                    'text': text
                })

        except Exception as e:
            print(f"Error extracting text from {key}: {str(e)}")
            continue

    return attachment_texts


def extract_with_textract(document_bytes):
    """Extract text using AWS Textract."""
    try:
        response = textract.detect_document_text(
            Document={'Bytes': document_bytes}
        )

        text_lines = []
        for block in response.get('Blocks', []):
            if block['BlockType'] == 'LINE':
                text_lines.append(block['Text'])

        return '\n'.join(text_lines)
    except Exception as e:
        print(f"Textract error: {str(e)}")
        return ""


def extract_text_from_pdf(document_bytes):
    """Extract text from PDF using Textract."""
    return extract_with_textract(document_bytes)


def extract_text_from_word(document_bytes):
    """Extract text from Word documents using python-docx."""
    try:
        import io
        import docx

        docx_file = io.BytesIO(document_bytes)
        doc = docx.Document(docx_file)

        text_content = []
        for paragraph in doc.paragraphs:
            if paragraph.text.strip():
                text_content.append(paragraph.text)

        return '\n\n'.join(text_content)
    except ImportError:
        print("python-docx not available, using Textract fallback")
        return extract_with_textract(document_bytes)
    except Exception as e:
        print(f"Word extraction error: {str(e)}")
        return extract_with_textract(document_bytes)


def generate_document_with_claude(instructions, email_subject, email_body, attachment_texts):
    """Use Claude API to generate the official document JSON."""
    api_key = os.environ.get('ANTHROPIC_API_KEY')
    if not api_key:
        print("ANTHROPIC_API_KEY not set")
        return None

    client = anthropic.Anthropic(api_key=api_key)

    # Build context
    context_parts = [
        f"# Email Subject\n{email_subject}\n",
        f"# Email Body\n{email_body}\n"
    ]

    if attachment_texts:
        context_parts.append("# Email Attachments\n")
        for att in attachment_texts:
            context_parts.append(f"## {att['filename']}\n{att['text']}\n")

    context = '\n'.join(context_parts)

    # Build prompt
    prompt = f"""Based on the following gongwen (official document) instructions and the email content, generate a structured JSON document for the OfficialDocumentExporter.

# Gongwen Instructions
{instructions}

# Email Context
{context}

Please generate a complete JSON object that follows the OfficialDocumentData structure. The JSON should include:
- 輸出檔名 (output file basename)
- 標題 (title)
- 申請表 (application form with all required fields)
- 附件一 (invitee attachment with list of people)
- 附件二 (schedule attachment with flight info and itinerary)

Return ONLY the JSON object, no additional text or explanation."""

    try:
        message = client.messages.create(
            model="claude-3-5-sonnet-20241022",
            max_tokens=8000,
            messages=[{
                "role": "user",
                "content": prompt
            }]
        )

        response_text = message.content[0].text

        # Extract JSON from response (in case there's any wrapper text)
        if '```json' in response_text:
            json_start = response_text.find('```json') + 7
            json_end = response_text.find('```', json_start)
            response_text = response_text[json_start:json_end].strip()
        elif '```' in response_text:
            json_start = response_text.find('```') + 3
            json_end = response_text.find('```', json_start)
            response_text = response_text[json_start:json_end].strip()

        # Parse and validate JSON
        document_data = json.loads(response_text)
        print("Successfully generated document JSON with Claude")
        return document_data

    except Exception as e:
        print(f"Error calling Claude API: {str(e)}")
        return None


def save_document_json(project_id, document_json):
    """Save the generated document JSON to S3."""
    bucket = os.environ['UPLOADED_DOCUMENT_BUCKET']
    key = f"generated-documents/{project_id}/document.json"

    s3.put_object(
        Bucket=bucket,
        Key=key,
        Body=json.dumps(document_json, ensure_ascii=False, indent=2).encode('utf-8'),
        ContentType='application/json'
    )

    print(f"Saved document JSON: {key}")
    return key


def update_project_with_document(project_id, json_key, docx_key):
    """Update project with generated document keys."""
    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    table.update_item(
        Key={'projectId': project_id},
        UpdateExpression='SET #status = :status, documentJsonKey = :json_key, generatedDocumentKey = :docx_key, updatedAt = :updated',
        ExpressionAttributeValues={
            ':status': 'completed',
            ':json_key': json_key,
            ':docx_key': docx_key,
            ':updated': datetime.utcnow().isoformat()
        },
        ExpressionAttributeNames={
            '#status': 'status'
        }
    )

    print(f"Updated project {project_id} with document keys")
