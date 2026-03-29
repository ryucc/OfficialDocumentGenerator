import json
import os
import boto3
from datetime import datetime
from uuid import uuid4

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')


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

                # Generate project ID
                project_id = str(uuid4())
                timestamp = datetime.utcnow().isoformat()

                # Create project item in DynamoDB
                item = {
                    'projectId': project_id,
                    'status': 'in_progress',
                    'emailS3Key': key,
                    'emailS3Bucket': bucket,
                    'createdAt': timestamp,
                    'updatedAt': timestamp
                }

                table.put_item(Item=item)

                print(f"Created project {project_id} for email {key}")

        except Exception as e:
            print(f"Error processing message: {str(e)}")
            print(f"Message body: {record.get('body', 'N/A')}")
            # Re-raise to send message to DLQ
            raise

    return {
        'statusCode': 200,
        'body': json.dumps('Successfully processed emails')
    }
