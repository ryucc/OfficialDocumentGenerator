import json
import os
import boto3
from decimal import Decimal

dynamodb = boto3.resource('dynamodb')


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
    """
    http_method = event.get('httpMethod', 'GET')
    query_parameters = event.get('queryStringParameters') or {}

    table_name = os.environ['PROJECTS_TABLE']
    table = dynamodb.Table(table_name)

    try:
        if http_method == 'GET':
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


def cors_headers():
    """Return CORS headers for API responses"""
    return {
        'Content-Type': 'application/json',
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Content-Type',
        'Access-Control-Allow-Methods': 'GET,OPTIONS'
    }
