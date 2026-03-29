# Cognito Login Setup

This document describes the authentication system implementation.

## Architecture

### Backend (Infrastructure)
- **Cognito User Pool**: Manages user accounts
- **Cognito User Pool Client**: Configured for password authentication
- **API Gateway Cognito Authorizer**: Validates JWT tokens automatically
- All API endpoints now require authentication

### Frontend
- **AWS Amplify**: Handles Cognito authentication
- **AuthContext**: React context for auth state management
- **Protected Routes**: Redirects unauthenticated users to login
- **API Helper**: Automatically attaches JWT tokens to API requests

## Setup Instructions

### 1. Deploy Infrastructure

The infrastructure changes will be deployed automatically through CodePipeline when merged to main.

Changes include:
- Cognito User Pool Client now supports `USER_PASSWORD_AUTH`
- API Gateway configured with Cognito Authorizer
- All endpoints protected by default

### 2. Create User Account

After deployment, create a user account manually:

```bash
# Get Cognito User Pool ID from CloudFormation outputs
aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`CognitoUserPoolId`].OutputValue' \
  --output text

# Create a user
aws cognito-idp admin-create-user \
  --user-pool-id <USER_POOL_ID> \
  --username user@example.com \
  --user-attributes Name=email,Value=user@example.com Name=email_verified,Value=true \
  --message-action SUPPRESS \
  --region ap-northeast-1

# Set permanent password
aws cognito-idp admin-set-user-password \
  --user-pool-id <USER_POOL_ID> \
  --username user@example.com \
  --password 'YourSecurePassword123!' \
  --permanent \
  --region ap-northeast-1
```

### 3. Configure Frontend Environment

Create `frontend/.env` file:

```bash
VITE_API_BASE_URL=https://your-api-id.execute-api.ap-northeast-1.amazonaws.com/Prod/api/v1
VITE_COGNITO_REGION=ap-northeast-1
VITE_COGNITO_USER_POOL_ID=ap-northeast-1_XXXXXXXXX
VITE_COGNITO_CLIENT_ID=xxxxxxxxxxxxxxxxxxxxxxxxxx
```

Get these values from CloudFormation outputs:

```bash
aws cloudformation describe-stacks \
  --stack-name official-doc-generator-app-test \
  --query 'Stacks[0].Outputs[?OutputKey==`CognitoUserPoolId` || OutputKey==`CognitoUserPoolClientId`]' \
  --region ap-northeast-1
```

## Features Implemented

### ✅ Login Flow
- Email/password authentication
- JWT token management (ID token, access token, refresh token)
- Automatic token refresh
- Session persistence

### ✅ Protected Routes
- `/` (Projects page) - requires authentication
- `/documents` (Documents page) - requires authentication
- `/login` - public, redirects to home if already authenticated

### ✅ API Security
- All API endpoints require valid Cognito JWT token
- Automatic token attachment to requests
- 401/403 error handling

### ✅ User Experience
- Login page with email/password form
- Logout button in navigation
- Loading states during authentication
- Error messages for failed login
- User email displayed in navigation

## What's NOT Included

- ❌ User sign-up (accounts are created manually by admin)
- ❌ Password reset/forgot password
- ❌ Email verification flow (emails marked as verified on creation)
- ❌ MFA/2FA

## Testing

1. Build and deploy infrastructure changes
2. Create a test user account (see instructions above)
3. Access the frontend application
4. You should be redirected to `/login`
5. Enter the credentials you created
6. Upon successful login, you'll be redirected to the home page
7. Try accessing API endpoints - they should now require authentication

## Security Notes

- All API endpoints are protected by Cognito authorizer
- JWT tokens are stored in browser memory (handled by AWS Amplify)
- Tokens automatically refresh before expiration
- CORS configured to allow Authorization header
- Password must meet Cognito's default policy requirements
