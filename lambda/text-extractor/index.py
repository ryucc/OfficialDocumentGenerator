import json
import os
import boto3
import io
from urllib.parse import unquote_plus

s3 = boto3.client('s3')
textract = boto3.client('textract')
dynamodb = boto3.resource('dynamodb')
lambda_client = boto3.client('lambda')


def lambda_handler(event, context):
    """
    Extract text from uploaded documents and save as .txt files.
    Triggered by S3 ObjectCreated events for sample-documents.
    """
    bucket = event['Records'][0]['s3']['bucket']['name']
    key = unquote_plus(event['Records'][0]['s3']['object']['key'])

    # Only process files in sample-documents/ prefix, not the txt extracts
    if not key.startswith('sample-documents/') or key.endswith('.txt'):
        print(f"Skipping non-document file: {key}")
        return {'statusCode': 200, 'body': 'Skipped'}

    print(f"Processing document: s3://{bucket}/{key}")

    try:
        # Download the document
        response = s3.get_object(Bucket=bucket, Key=key)
        document_bytes = response['Body'].read()
        content_type = response.get('ContentType', '')

        # Extract text based on content type
        extracted_text = extract_text(document_bytes, content_type, bucket, key)

        if not extracted_text:
            print(f"No text extracted from {key}")
            return {'statusCode': 200, 'body': 'No text extracted'}

        # Generate text file key
        txt_key = key.rsplit('.', 1)[0] + '.txt'

        # Save extracted text to S3
        s3.put_object(
            Bucket=bucket,
            Key=txt_key,
            Body=extracted_text.encode('utf-8'),
            ContentType='text/plain'
        )

        print(f"Saved extracted text to: {txt_key}")

        # Update DynamoDB with textObjectKey
        update_metadata(key, txt_key)

        # Trigger rules compilation (async)
        trigger_rules_compilation()

        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Text extraction completed',
                'textKey': txt_key
            })
        }

    except Exception as e:
        print(f"Error processing {key}: {str(e)}")
        # Don't raise - we don't want to retry text extraction failures
        return {
            'statusCode': 500,
            'body': json.dumps({'error': str(e)})
        }


def extract_text(document_bytes, content_type, bucket, key):
    """Extract text from document based on content type."""

    # PDF documents
    if 'pdf' in content_type.lower():
        return extract_text_from_pdf_with_textract(document_bytes)

    # Word documents
    elif 'word' in content_type.lower() or 'officedocument' in content_type.lower():
        return extract_text_from_docx(document_bytes)

    # Images
    elif content_type.startswith('image/'):
        return extract_text_from_image_with_textract(document_bytes)

    # Plain text
    elif 'text' in content_type.lower():
        return document_bytes.decode('utf-8', errors='ignore')

    # For other document types, try Textract
    else:
        try:
            return extract_text_from_pdf_with_textract(document_bytes)
        except Exception as e:
            print(f"Failed to extract text with Textract: {str(e)}")
            return ""


def extract_text_from_pdf_with_textract(document_bytes):
    """Extract text from PDF using AWS Textract."""
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
        # Fallback to basic PDF parsing
        return extract_text_from_pdf_basic(document_bytes)


def extract_text_from_pdf_basic(document_bytes):
    """Basic PDF text extraction using pypdf."""
    try:
        import pypdf

        pdf_file = io.BytesIO(document_bytes)
        pdf_reader = pypdf.PdfReader(pdf_file)

        text_content = []
        for page in pdf_reader.pages:
            text_content.append(page.extract_text())

        return '\n\n'.join(text_content)
    except ImportError:
        print("pypdf not available, skipping basic PDF extraction")
        return ""
    except Exception as e:
        print(f"Basic PDF extraction error: {str(e)}")
        return ""


def extract_text_from_docx(document_bytes):
    """Extract text from DOCX files."""
    try:
        import docx

        docx_file = io.BytesIO(document_bytes)
        doc = docx.Document(docx_file)

        text_content = []
        for paragraph in doc.paragraphs:
            if paragraph.text.strip():
                text_content.append(paragraph.text)

        return '\n\n'.join(text_content)
    except ImportError:
        print("python-docx not available")
        return ""
    except Exception as e:
        print(f"DOCX extraction error: {str(e)}")
        return ""


def extract_text_from_image_with_textract(document_bytes):
    """Extract text from images using AWS Textract OCR."""
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
        print(f"Image OCR error: {str(e)}")
        return ""


def update_metadata(document_key, txt_key):
    """Update DynamoDB metadata with textObjectKey."""
    table_name = os.environ.get('UPLOADED_DOCUMENT_METADATA_TABLE')
    if not table_name:
        print("UPLOADED_DOCUMENT_METADATA_TABLE not set, skipping metadata update")
        return

    # Extract documentId from key (format: sample-documents/{documentId}/{filename})
    parts = document_key.split('/')
    if len(parts) < 3 or parts[0] != 'sample-documents':
        print(f"Invalid document key format: {document_key}")
        return

    document_id = parts[1]

    try:
        table = dynamodb.Table(table_name)
        table.update_item(
            Key={'documentId': document_id},
            UpdateExpression='SET textObjectKey = :txt_key',
            ExpressionAttributeValues={':txt_key': txt_key}
        )
        print(f"Updated metadata for document {document_id} with textObjectKey: {txt_key}")
    except Exception as e:
        print(f"Error updating metadata: {str(e)}")
        # Don't raise - metadata update failure shouldn't fail the whole process


def trigger_rules_compilation():
    """Trigger the rules compiler Lambda asynchronously."""
    function_name = os.environ.get('RULES_COMPILER_FUNCTION_NAME')
    if not function_name:
        print("RULES_COMPILER_FUNCTION_NAME not set, skipping rules compilation")
        return

    try:
        # Invoke asynchronously (Event invocation type)
        lambda_client.invoke(
            FunctionName=function_name,
            InvocationType='Event',  # Async invocation
            Payload=json.dumps({})
        )
        print(f"Triggered rules compilation: {function_name}")
    except Exception as e:
        print(f"Error triggering rules compilation: {str(e)}")
        # Don't raise - compilation trigger failure shouldn't fail the extraction
