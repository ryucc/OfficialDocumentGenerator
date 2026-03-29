import json
import os
import boto3
from decimal import Decimal

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')


class DecimalEncoder(json.JSONEncoder):
    """Helper class to convert DynamoDB Decimal to JSON"""
    def default(self, obj):
        if isinstance(obj, Decimal):
            return int(obj) if obj % 1 == 0 else float(obj)
        return super(DecimalEncoder, self).default(obj)


def lambda_handler(event, context):
    """
    API handler for project operations
    GET /projects - List all projects
    GET /projects/{projectId}/download-url - Get download URL for generated document
    """
    http_method = event.get('httpMethod', 'GET')
    path = event.get('path', '')
    path_parameters = event.get('pathParameters') or {}
    query_parameters = event.get('queryStringParameters') or {}

    table_name = os.environ['PROJECTS_TABLE']
    bucket_name = os.environ['UPLOADED_DOCUMENT_BUCKET']
    table = dynamodb.Table(table_name)

    try:
        if http_method == 'GET':
            # Check if requesting download URL
            if path_parameters.get('projectId') and path.endswith('/download-url'):
                return handle_download_url(table, bucket_name, path_parameters['projectId'])

            # List projects with optional status filter
            status_filter = query_parameters.get('status')

            if status_filter:
                # Query by status using GSI
                response = table.query(
                    IndexName='StatusIndex',
                    KeyConditionExpression='#status = :status',
                    ExpressionAttributeNames={'#status': 'status'},
                    ExpressionAttributeValues={':status': status_filter}
                )
            else:
                # Scan all projects
                response = table.scan()

            items = response.get('Items', [])

            # Sort by createdAt descending (newest first)
            items.sort(key=lambda x: x.get('createdAt', ''), reverse=True)

            return {
                'statusCode': 200,
                'headers': cors_headers(),
                'body': json.dumps({
                    'items': items,
                    'count': len(items)
                }, cls=DecimalEncoder)
            }

        elif http_method == 'OPTIONS':
            # Handle CORS preflight
            return {
                'statusCode': 200,
                'headers': cors_headers(),
                'body': ''
            }

        else:
            return {
                'statusCode': 405,
                'headers': cors_headers(),
                'body': json.dumps({'error': 'Method not allowed'})
            }

    except Exception as e:
        print(f"Error: {str(e)}")
        return {
            'statusCode': 500,
            'headers': cors_headers(),
            'body': json.dumps({'error': str(e)})
        }


def handle_download_url(table, bucket_name, project_id):
    """Generate presigned download URL for project's generated document."""
    # Get project from DynamoDB
    response = table.get_item(Key={'projectId': project_id})
    project = response.get('Item')

    if not project:
        return {
            'statusCode': 404,
            'headers': cors_headers(),
            'body': json.dumps({'error': 'Project not found'})
        }

    # Check if document was generated
    document_key = project.get('generatedDocumentKey')
    if not document_key:
        return {
            'statusCode': 404,
            'headers': cors_headers(),
            'body': json.dumps({'error': 'Generated document not found'})
        }

    # Generate presigned URL (valid for 15 minutes)
    try:
        download_url = s3.generate_presigned_url(
            'get_object',
            Params={
                'Bucket': bucket_name,
                'Key': document_key
            },
            ExpiresIn=900  # 15 minutes
        )

        return {
            'statusCode': 200,
            'headers': cors_headers(),
            'body': json.dumps({
                'downloadUrl': download_url,
                'documentKey': document_key,
                'projectId': project_id
            }, cls=DecimalEncoder)
        }
    except Exception as e:
        print(f"Error generating presigned URL: {str(e)}")
        return {
            'statusCode': 500,
            'headers': cors_headers(),
            'body': json.dumps({'error': 'Failed to generate download URL'})
        }


def cors_headers():
    """Return CORS headers for API responses"""
    return {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }
