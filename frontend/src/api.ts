import { fetchAuthSession } from 'aws-amplify/auth'

export async function getAuthHeaders(): Promise<HeadersInit> {
  try {
    const session = await fetchAuthSession()
    const idToken = session.tokens?.idToken?.toString()

    if (idToken) {
      return {
        'Authorization': `Bearer ${idToken}`,
      }
    }
  } catch (error) {
    console.error('Failed to get auth token:', error)
  }

  return {}
}

export async function authenticatedFetch(url: string, options: RequestInit = {}): Promise<Response> {
  const authHeaders = await getAuthHeaders()

  return fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      ...authHeaders,
    },
  })
}
