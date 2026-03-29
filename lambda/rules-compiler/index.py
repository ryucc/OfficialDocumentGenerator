import json
import os
import boto3
from datetime import datetime

s3 = boto3.client('s3')
dynamodb = boto3.resource('dynamodb')
lambda_client = boto3.client('lambda')


def lambda_handler(event, context):
    """
    Compile all extracted text files into a versioned gongwen-instructions file.
    Can be invoked directly or triggered by events.
    """
    bucket = os.environ['UPLOADED_DOCUMENT_BUCKET']
    table_name = os.environ['UPLOADED_DOCUMENT_METADATA_TABLE']

    print("Starting rules compilation...")

    try:
        # Get all documents with text extractions
        table = dynamodb.Table(table_name)
        response = table.scan()
        documents = response.get('Items', [])

        # Filter documents that have textObjectKey and are AVAILABLE
        text_documents = [
            doc for doc in documents
            if doc.get('textObjectKey') and doc.get('status') == 'AVAILABLE'
        ]

        if not text_documents:
            print("No text documents found to compile")
            return {
                'statusCode': 200,
                'body': json.dumps({'message': 'No documents to compile'})
            }

        print(f"Found {len(text_documents)} documents with extracted text")

        # Compile all text content
        compiled_text = compile_texts(bucket, text_documents)

        # Generate version timestamp
        version = datetime.utcnow().strftime('%Y%m%dT%H%M%S')
        versioned_key = f"gongwen-instructions/gongwen-instructions-v{version}.txt"
        latest_key = "gongwen-instructions/gongwen-instructions-latest.txt"

        # Save versioned file
        s3.put_object(
            Bucket=bucket,
            Key=versioned_key,
            Body=compiled_text.encode('utf-8'),
            ContentType='text/plain',
            Metadata={
                'version': version,
                'document-count': str(len(text_documents)),
                'compiled-at': datetime.utcnow().isoformat()
            }
        )
        print(f"Saved versioned file: {versioned_key}")

        # Save latest file
        s3.put_object(
            Bucket=bucket,
            Key=latest_key,
            Body=compiled_text.encode('utf-8'),
            ContentType='text/plain',
            Metadata={
                'version': version,
                'document-count': str(len(text_documents)),
                'compiled-at': datetime.utcnow().isoformat(),
                'versioned-key': versioned_key
            }
        )
        print(f"Saved latest file: {latest_key}")

        return {
            'statusCode': 200,
            'body': json.dumps({
                'message': 'Rules compilation completed',
                'versionedKey': versioned_key,
                'latestKey': latest_key,
                'version': version,
                'documentCount': len(text_documents)
            })
        }

    except Exception as e:
        print(f"Error compiling rules: {str(e)}")
        raise


def compile_texts(bucket, documents):
    """
    Download and compile all text files into a single document.
    """
    compiled_parts = []

    # Add header
    compiled_parts.append("=" * 80)
    compiled_parts.append("GONGWEN INSTRUCTIONS - COMPILED DOCUMENT RULES")
    compiled_parts.append(f"Generated: {datetime.utcnow().isoformat()}Z")
    compiled_parts.append(f"Total Documents: {len(documents)}")
    compiled_parts.append("=" * 80)
    compiled_parts.append("")

    # Sort documents by createdAt to maintain consistent order
    sorted_docs = sorted(documents, key=lambda d: d.get('createdAt', ''))

    for idx, doc in enumerate(sorted_docs, 1):
        text_key = doc.get('textObjectKey')
        if not text_key:
            continue

        try:
            # Download text file
            response = s3.get_object(Bucket=bucket, Key=text_key)
            text_content = response['Body'].read().decode('utf-8', errors='ignore')

            # Add document section
            compiled_parts.append("-" * 80)
            compiled_parts.append(f"DOCUMENT {idx}: {doc.get('filename', 'Unknown')}")
            compiled_parts.append(f"ID: {doc.get('documentId', 'Unknown')}")
            compiled_parts.append(f"Created: {doc.get('createdAt', 'Unknown')}")
            compiled_parts.append(f"Content Type: {doc.get('contentType', 'Unknown')}")
            compiled_parts.append("-" * 80)
            compiled_parts.append("")
            compiled_parts.append(text_content.strip())
            compiled_parts.append("")
            compiled_parts.append("")

            print(f"Compiled document {idx}/{len(sorted_docs)}: {doc.get('filename')}")

        except Exception as e:
            print(f"Error reading text file {text_key}: {str(e)}")
            # Add error note but continue
            compiled_parts.append("-" * 80)
            compiled_parts.append(f"DOCUMENT {idx}: {doc.get('filename', 'Unknown')} [ERROR]")
            compiled_parts.append(f"Error: Could not read text file")
            compiled_parts.append("-" * 80)
            compiled_parts.append("")

    # Add footer
    compiled_parts.append("=" * 80)
    compiled_parts.append("END OF GONGWEN INSTRUCTIONS")
    compiled_parts.append("=" * 80)

    return '\n'.join(compiled_parts)
