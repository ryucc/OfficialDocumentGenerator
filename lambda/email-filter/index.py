import json
import os
import boto3

cognito_client = boto3.client('cognito-idp')


def get_cognito_user_emails(user_pool_id):
    """Fetch all registered user emails from Cognito"""
    emails = set()
    pagination_token = None

    try:
        while True:
            if pagination_token:
                response = cognito_client.list_users(
                    UserPoolId=user_pool_id,
                    PaginationToken=pagination_token
                )
            else:
                response = cognito_client.list_users(UserPoolId=user_pool_id)

            for user in response.get('Users', []):
                for attr in user.get('Attributes', []):
                    if attr['Name'] == 'email':
                        emails.add(attr['Value'].lower())

            pagination_token = response.get('PaginationToken')
            if not pagination_token:
                break

    except Exception as e:
        print(f"Error fetching Cognito users: {str(e)}")
        raise

    return emails


def lambda_handler(event, context):
    print(f"Received event: {json.dumps(event)}")

    # Get Cognito User Pool ID from environment
    user_pool_id = os.environ.get('USER_POOL_ID')
    if not user_pool_id:
        print("ERROR: USER_POOL_ID not configured")
        return {'disposition': 'STOP_RULE'}

    # Parse SES event
    ses_notification = event['Records'][0]['ses']
    mail = ses_notification['mail']
    receipt = ses_notification['receipt']

    # Get sender email
    sender = mail['source'].lower()
    recipients = [r.lower() for r in mail['destination']]

    print(f"Email from: {sender}, to: {recipients}")

    # Verify sender authenticity (prevent spoofing)
    spf_verdict = receipt.get('spfVerdict', {}).get('status', 'NONE')
    dkim_verdict = receipt.get('dkimVerdict', {}).get('status', 'NONE')
    dmarc_verdict = receipt.get('dmarcVerdict', {}).get('status', 'NONE')

    print(f"Auth checks - SPF: {spf_verdict}, DKIM: {dkim_verdict}, DMARC: {dmarc_verdict}")

    # Require at least one authentication method to pass
    auth_passed = (
        dkim_verdict == 'PASS' or
        dmarc_verdict == 'PASS' or
        spf_verdict == 'PASS'
    )

    if not auth_passed:
        print(f"Authentication failed for {sender}, rejecting email")
        return {'disposition': 'STOP_RULE'}

    # Get allowed senders from Cognito
    allowed_emails = get_cognito_user_emails(user_pool_id)
    print(f"Cognito registered emails: {allowed_emails}")

    # Check if sender is registered in Cognito
    if sender not in allowed_emails:
        print(f"Sender {sender} not registered in Cognito, rejecting email")
        return {'disposition': 'STOP_RULE'}

    print(f"Sender {sender} is authenticated and registered in Cognito, continuing processing")
    return {'disposition': 'CONTINUE'}
