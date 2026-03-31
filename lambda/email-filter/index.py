import json
import os
import boto3

cognito_client = boto3.client('cognito-idp')


def is_cognito_user(user_pool_id, email):
    """Check if email is registered in Cognito"""
    try:
        response = cognito_client.list_users(
            UserPoolId=user_pool_id,
            Filter=f'email = "{email}"'
        )

        users = response.get('Users', [])
        return len(users) > 0

    except Exception as e:
        print(f"Error checking Cognito user: {str(e)}")
        raise


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

    # Check if sender is registered in Cognito
    if not is_cognito_user(user_pool_id, sender):
        print(f"Sender {sender} not registered in Cognito, rejecting email")
        return {'disposition': 'STOP_RULE'}

    print(f"Sender {sender} is authenticated and registered in Cognito, continuing processing")
    return {'disposition': 'CONTINUE'}
