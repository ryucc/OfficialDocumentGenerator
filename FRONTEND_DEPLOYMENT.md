# Frontend Deployment with AWS Amplify

## One-Time Setup

### 1. Create GitHub Personal Access Token

1. Go to https://github.com/settings/tokens/new
2. Set note: "AWS Amplify - OfficialDocumentGenerator"
3. Select scopes:
   - ✅ `repo` (full control of private repositories)
4. Click "Generate token"
5. **Copy the token** (you won't see it again!)

### 2. Deploy Amplify Stack

```bash
# Set your GitHub token
export GITHUB_TOKEN="ghp_your_token_here"

# Deploy the Amplify frontend stack
aws cloudformation create-stack \
  --stack-name official-doc-generator-frontend \
  --template-body file://infra/amplify-frontend.yaml \
  --parameters \
    ParameterKey=GitHubOwner,ParameterValue=ryucc \
    ParameterKey=GitHubRepo,ParameterValue=OfficialDocumentGenerator \
    ParameterKey=GitHubBranch,ParameterValue=main \
    ParameterKey=GitHubToken,ParameterValue=$GITHUB_TOKEN \
  --region ap-northeast-1
```

### 3. Wait for Deployment

```bash
# Monitor stack creation
aws cloudformation wait stack-create-complete \
  --stack-name official-doc-generator-frontend \
  --region ap-northeast-1

# Get your frontend URL
aws cloudformation describe-stacks \
  --stack-name official-doc-generator-frontend \
  --region ap-northeast-1 \
  --query 'Stacks[0].Outputs[?OutputKey==`AmplifyAppURL`].OutputValue' \
  --output text
```

## Auto-Deployment

Once setup is complete, Amplify will **automatically deploy** when you:
- ✅ Push to the `main` branch
- ✅ Merge pull requests

You'll see builds at: https://console.aws.amazon.com/amplify

## Features Included

✅ **HTTPS** - Automatic SSL certificate
✅ **Global CDN** - Fast worldwide delivery
✅ **SPA Routing** - React Router works correctly
✅ **Auto Builds** - Deploys on git push
✅ **Build Cache** - Faster subsequent builds

## Custom Domain (Optional)

After deployment, you can add a custom domain:

1. Go to AWS Amplify Console
2. Select your app
3. Click "Domain management"
4. Add your domain
5. Follow DNS configuration steps

## Troubleshooting

### Build fails?
```bash
# Check build logs
aws amplify list-jobs \
  --app-id $(aws amplify list-apps --query 'apps[0].appId' --output text --region ap-northeast-1) \
  --branch-name main \
  --region ap-northeast-1
```

### Wrong API URL?
Frontend currently hardcodes the API URL. To make it configurable, add environment variable in the CloudFormation template.

## Cost Estimate

- **Build minutes**: ~2-3 minutes per build
- **Storage**: ~10 MB
- **Data transfer**: ~1 GB/month for light usage

**Expected cost**: $1-3/month (within free tier for first year)
