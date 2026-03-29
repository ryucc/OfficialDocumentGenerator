import json
import boto3
import cfnresponse

ses = boto3.client('ses')


def lambda_handler(event, context):
    """
    Custom resource to handle SES domain verification and rule set activation
    """
    print(f"Event: {json.dumps(event)}")

    try:
        request_type = event['RequestType']
        properties = event['ResourceProperties']
        domain = properties.get('Domain')
        rule_set_name = properties.get('RuleSetName')

        response_data = {}

        if request_type in ['Create', 'Update']:
            # Verify domain identity and get verification token
            if domain:
                print(f"Verifying domain: {domain}")
                verify_response = ses.verify_domain_identity(Domain=domain)
                verification_token = verify_response['VerificationToken']
                response_data['VerificationToken'] = verification_token
                print(f"Verification token: {verification_token}")

            # Activate receipt rule set
            if rule_set_name:
                print(f"Activating receipt rule set: {rule_set_name}")
                ses.set_active_receipt_rule_set(RuleSetName=rule_set_name)
                response_data['ActiveRuleSet'] = rule_set_name
                print(f"Activated rule set: {rule_set_name}")

        elif request_type == 'Delete':
            # On delete, deactivate the rule set if it's still active
            if rule_set_name:
                try:
                    # Check if this rule set is active
                    active = ses.describe_active_receipt_rule_set()
                    if active.get('Metadata', {}).get('Name') == rule_set_name:
                        print(f"Deactivating rule set: {rule_set_name}")
                        ses.set_active_receipt_rule_set()  # No RuleSetName = deactivate
                except Exception as e:
                    print(f"Error deactivating rule set: {str(e)}")

        cfnresponse.send(event, context, cfnresponse.SUCCESS, response_data)

    except Exception as e:
        print(f"Error: {str(e)}")
        cfnresponse.send(event, context, cfnresponse.FAILED, {
            'Error': str(e)
        })
