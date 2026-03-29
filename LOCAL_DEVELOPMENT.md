# Local Development Guide

Run the application locally with real AWS resources (S3 and DynamoDB).

## Prerequisites

1. **AWS Credentials**: Configure AWS CLI
   ```bash
   aws configure
   # Or set environment variables:
   export AWS_ACCESS_KEY_ID=your_access_key
   export AWS_SECRET_ACCESS_KEY=your_secret_key
   export AWS_REGION=us-east-1
   ```

2. **Deploy Stack**: Deploy the CloudFormation stack first
   ```bash
   sam deploy \
     --template-file infra/app-only.yaml \
     --stack-name official-doc-generator-app-test \
     --parameter-overrides \
       Stage=test \
       AllowedEmailSenders="shortyliu@gmail.com" \
       HostedZoneId=Z08801051G2GR30IAD1A4 \
     --capabilities CAPABILITY_IAM \
     --resolve-s3
   ```

3. **Node.js & Python**: Ensure installed
   ```bash
   node --version  # v18+
   python3 --version  # 3.9+
   ```

## Quick Start

### Option 1: Run Both Servers (Recommended)

```bash
./scripts/run-local-dev.sh
```

This starts:
- **Backend**: http://localhost:3001
- **Frontend**: http://localhost:5173

Press `Ctrl+C` to stop both.

### Option 2: Run Separately

**Terminal 1 - Backend:**
```bash
./scripts/run-local-backend.sh
```

**Terminal 2 - Frontend:**
```bash
./scripts/run-local-frontend.sh
```

## What Runs Locally vs AWS

### Local:
- ✅ Frontend dev server (Vite)
- ✅ Backend API server (Flask wrapper around Lambda)

### AWS (Real Resources):
- ☁️ DynamoDB `ProjectsTable` - email projects
- ☁️ S3 Bucket - email storage
- ☁️ SES - email receiving

## API Endpoints

**Backend** (http://localhost:3001):
```bash
# List all projects
GET http://localhost:3001/api/v1/projects

# Filter by status
GET http://localhost:3001/api/v1/projects?status=in_progress
GET http://localhost:3001/api/v1/projects?status=finished
```

**Test with curl:**
```bash
curl http://localhost:3001/api/v1/projects
```

## Frontend Pages

- **http://localhost:5173/** - 公文產生器 (main chat)
- **http://localhost:5173/documents** - 範例管理 (document upload)
- **http://localhost:5173/projects** - 郵件專案 (email projects list)

## Testing the Email Flow

1. **Send test email** to `ai@gongwengpt.click` from `shortyliu@gmail.com`

2. **Email arrives in S3**:
   ```bash
   aws s3 ls s3://<bucket>/emails/test/ --region us-east-1
   ```

3. **SQS triggers Lambda** → Creates project in DynamoDB

4. **View in UI**: http://localhost:5173/projects

5. **Check DynamoDB directly**:
   ```bash
   aws dynamodb scan --table-name email-projects-test --region us-east-1
   ```

## Troubleshooting

### Backend won't start

**Error: "Could not find ProjectsTableName"**
- Deploy the stack first: `sam deploy ...`

**Error: "Access Denied" to DynamoDB**
- Check AWS credentials: `aws sts get-caller-identity`
- Verify IAM permissions for DynamoDB read access

### Frontend won't start

**Error: "command not found: npm"**
- Install Node.js: https://nodejs.org/

**Dependencies missing:**
```bash
cd frontend
npm install
```

### API calls fail (CORS errors)

- Make sure backend is running on port 3001
- Check browser console for specific error
- Verify `.env.local` has correct backend URL

### No projects showing up

1. **Check if projects exist**:
   ```bash
   aws dynamodb scan --table-name email-projects-test --region us-east-1
   ```

2. **Check backend logs** for errors

3. **Send test email** to trigger project creation

## Environment Variables

### Backend
```bash
PROJECTS_TABLE=email-projects-test  # Auto-fetched from CloudFormation
AWS_REGION=us-east-1
```

### Frontend
```bash
VITE_API_BASE_URL=http://localhost:3001/api/v1  # Set in .env.local
```

## Project Structure

```
frontend/
  .env.local              # Local API endpoint config
  src/
    Projects.tsx          # Projects list page
    Projects.css          # Styles
    config.ts            # API config (reads VITE_API_BASE_URL)

lambda/
  projects-api/
    index.py             # Lambda handler
    local_server.py      # Flask wrapper (generated)

scripts/
  run-local-backend.sh   # Start backend
  run-local-frontend.sh  # Start frontend
  run-local-dev.sh       # Start both
```

## Clean Up

Stop all servers:
```bash
# Press Ctrl+C in terminal(s)

# Or kill processes:
pkill -f "python3 local_server.py"
pkill -f "npm run dev"
```

Remove virtual environment:
```bash
rm -rf lambda/projects-api/venv
rm lambda/projects-api/local_server.py
```
