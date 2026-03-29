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
    receipt = ses_notification['receipt']

    # Get sender email
    sender = mail['source'].lower()
    recipients = [r.lower() for r in mail['destination']]

    print(f"Email from: {sender}, to: {recipients}")
    print(f"Allowed senders: {allowed_senders}")

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

    # Check if sender is in allowlist
    if sender not in allowed_senders:
        print(f"Sender {sender} not in allowlist, rejecting email")
        return {'disposition': 'STOP_RULE'}

    print(f"Sender {sender} is authenticated and allowed, continuing processing")
    return {'disposition': 'CONTINUE'}
