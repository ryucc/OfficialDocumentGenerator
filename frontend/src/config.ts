// API Configuration
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'https://b1qdg79vji.execute-api.ap-northeast-1.amazonaws.com/Prod/api/v1'

// Cognito Configuration
export const COGNITO_CONFIG = {
  region: import.meta.env.VITE_COGNITO_REGION || 'ap-northeast-1',
  userPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID || 'ap-northeast-1_example',
  userPoolClientId: import.meta.env.VITE_COGNITO_CLIENT_ID || 'example-client-id',
}
