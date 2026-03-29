#!/bin/bash

# Run backend API locally with real AWS resources
# Usage: ./scripts/run-local-backend.sh

set -e

STACK_NAME="official-doc-generator-app-test"
REGION=${AWS_REGION:-ap-northeast-1}

echo "=================================================="
echo "Starting Local Backend with AWS Resources"
echo "=================================================="

# Get resource names from CloudFormation
echo "Fetching resource names from stack: ${STACK_NAME}..."

PROJECTS_TABLE=$(aws cloudformation describe-stacks \
  --stack-name ${STACK_NAME} \
  --region ${REGION} \
  --query 'Stacks[0].Outputs[?OutputKey==`ProjectsTableName`].OutputValue' \
  --output text)

if [ -z "$PROJECTS_TABLE" ]; then
  echo "❌ Error: Could not find ProjectsTableName in stack outputs"
  echo "Make sure the stack is deployed with the projects table"
  exit 1
fi

echo "✓ Projects Table: ${PROJECTS_TABLE}"

# Set environment variables
export PROJECTS_TABLE="${PROJECTS_TABLE}"
export AWS_REGION="${REGION}"

echo ""
echo "=================================================="
echo "Environment:"
echo "  PROJECTS_TABLE=${PROJECTS_TABLE}"
echo "  AWS_REGION=${AWS_REGION}"
echo "=================================================="
echo ""

# Check if Python is installed
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is not installed"
    exit 1
fi

# Install dependencies if needed
if [ ! -d "lambda/projects-api/venv" ]; then
  echo "Creating virtual environment..."
  python3 -m venv lambda/projects-api/venv
  source lambda/projects-api/venv/bin/activate
  pip install boto3 flask flask-cors
else
  source lambda/projects-api/venv/bin/activate
fi

# Create Flask wrapper for the Lambda handler
cat > lambda/projects-api/local_server.py << 'EOF'
import os
import json
from flask import Flask, request, jsonify
from flask_cors import CORS
from index import lambda_handler

app = Flask(__name__)
CORS(app)

@app.route('/api/v1/projects', methods=['GET', 'OPTIONS'])
def projects():
    if request.method == 'OPTIONS':
        return '', 200

    # Convert Flask request to Lambda event
    event = {
        'httpMethod': request.method,
        'path': request.path,
        'queryStringParameters': dict(request.args) if request.args else None,
        'headers': dict(request.headers),
        'body': request.get_data(as_text=True) if request.data else None
    }

    # Call Lambda handler
    response = lambda_handler(event, None)

    # Convert Lambda response to Flask response
    return (
        response.get('body', ''),
        response.get('statusCode', 200),
        response.get('headers', {})
    )

if __name__ == '__main__':
    print(f"Projects API running on http://localhost:3001")
    print(f"Using DynamoDB table: {os.environ.get('PROJECTS_TABLE')}")
    app.run(host='0.0.0.0', port=3001, debug=True)
EOF

echo "🚀 Starting Flask server on http://localhost:3001"
echo ""
echo "Endpoints:"
echo "  GET  http://localhost:3001/api/v1/projects"
echo "  GET  http://localhost:3001/api/v1/projects?status=in_progress"
echo "  GET  http://localhost:3001/api/v1/projects?status=finished"
echo ""
echo "Press Ctrl+C to stop"
echo ""

cd lambda/projects-api
python3 local_server.py
