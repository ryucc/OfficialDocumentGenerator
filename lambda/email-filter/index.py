import json
import os


def lambda_handler(event, context):
    print(f"Received event: {json.dumps(event)}")

    # Get allowed senders from environment variable
    allowed_senders_str = os.environ.get('ALLOWED_SENDERS', '')
    allowed_senders = [s.strip().lower() for s in allowed_senders_str.split(',') if s.strip()]

    # If no allowlist configured, allow all
    if not allowed_senders:
        print("No allowlist configured, accepting all emails")
        return {'disposition': 'CONTINUE'}

    # Parse SES event
    ses_notification = event['Records'][0]['ses']
    mail = ses_notification['mail']

    # Get sender email
    sender = mail['source'].lower()
    recipients = [r.lower() for r in mail['destination']]

    print(f"Email from: {sender}, to: {recipients}")
    print(f"Allowed senders: {allowed_senders}")

    # Check if sender is in allowlist
    if sender not in allowed_senders:
        print(f"Sender {sender} not in allowlist, rejecting email")
        return {'disposition': 'STOP_RULE'}

    print(f"Sender {sender} is allowed, continuing processing")
    return {'disposition': 'CONTINUE'}
