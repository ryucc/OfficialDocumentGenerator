const IS_MOCK = import.meta.env.VITE_MOCK === 'true'

export async function getAuthHeaders(): Promise<HeadersInit> {
  if (IS_MOCK) return {}

  try {
    const { fetchAuthSession } = await import('aws-amplify/auth')
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
