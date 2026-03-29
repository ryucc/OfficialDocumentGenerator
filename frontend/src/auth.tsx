import { createContext, useContext, useEffect, useRef, useState } from 'react'
import { API_BASE_URL, API_BASE_URL_ERROR } from './config'

export interface AuthTokens {
  accessToken: string
  idToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface AuthChallenge {
  challengeName: 'NEW_PASSWORD_REQUIRED'
  session: string
  email: string
}

export interface LoginResponse {
  tokens?: AuthTokens
  challenge?: AuthChallenge
}

export interface CurrentUser {
  userId: string
  email?: string
  emailVerified: boolean
}

export type LoginOutcome =
  | { type: 'authenticated' }
  | { type: 'challenge', challenge: AuthChallenge }

type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

interface AuthContextValue {
  status: AuthStatus
  user: CurrentUser | null
  tokens: AuthTokens | null
  login: (email: string, password: string) => Promise<LoginOutcome>
  forgotPassword: (email: string) => Promise<void>
  confirmForgotPassword: (
    email: string,
    confirmationCode: string,
    newPassword: string,
  ) => Promise<void>
  respondToNewPassword: (
    email: string,
    newPassword: string,
    session: string,
  ) => Promise<void>
  logout: () => Promise<void>
  refreshUser: () => Promise<void>
  authFetch: (path: string, init?: RequestInit) => Promise<Response>
}

interface ApiErrorBody {
  code?: string
  message?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.status = status
    this.code = code
  }
}

const AUTH_STORAGE_KEY = 'officialpapergpt.auth.tokens'
const AuthContext = createContext<AuthContextValue | null>(null)

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isAuthTokens(value: unknown): value is AuthTokens {
  return isRecord(value)
    && typeof value.accessToken === 'string'
    && typeof value.idToken === 'string'
    && typeof value.refreshToken === 'string'
    && typeof value.tokenType === 'string'
    && typeof value.expiresIn === 'number'
}

function isAuthChallenge(value: unknown): value is AuthChallenge {
  return isRecord(value)
    && value.challengeName === 'NEW_PASSWORD_REQUIRED'
    && typeof value.session === 'string'
    && typeof value.email === 'string'
}

function isLoginResponse(value: unknown): value is LoginResponse {
  return isRecord(value)
    && (value.tokens === undefined || isAuthTokens(value.tokens))
    && (value.challenge === undefined || isAuthChallenge(value.challenge))
    && (value.tokens !== undefined || value.challenge !== undefined)
}

function readStoredTokens(): AuthTokens | null {
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY)
    if (!raw) {
      return null
    }
    const parsed: unknown = JSON.parse(raw)
    return isAuthTokens(parsed) ? parsed : null
  } catch {
    return null
  }
}

function writeStoredTokens(tokens: AuthTokens | null) {
  if (tokens) {
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(tokens))
    return
  }
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}

async function readJsonBody<T>(response: Response): Promise<T | null> {
  const text = await response.text()
  if (!text) {
    return null
  }

  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}

export async function createApiError(
  response: Response,
  fallbackMessage: string,
): Promise<ApiError> {
  const body = await readJsonBody<ApiErrorBody>(response)
  const code = body?.code || `HTTP_${response.status}`
  const message = body?.message || fallbackMessage
  return new ApiError(response.status, code, message)
}

function buildApiUrl(path: string) {
  if (!API_BASE_URL) {
    throw new Error(API_BASE_URL_ERROR || '缺少 API 設定')
  }
  return `${API_BASE_URL}${path}`
}

function mergeHeaders(
  headers: HeadersInit | undefined,
  additions: Record<string, string>,
) {
  const merged = new Headers(headers)
  for (const [key, value] of Object.entries(additions)) {
    merged.set(key, value)
  }
  return merged
}

function toRequestError(error: unknown, fallbackMessage: string) {
  if (error instanceof ApiError) {
    return error
  }
  if (error instanceof Error && error.message === API_BASE_URL_ERROR) {
    return error
  }
  if (error instanceof TypeError) {
    return new Error(`${fallbackMessage}，無法連線到 API。請確認 VITE_API_BASE_URL、路由與 CORS 設定。`)
  }
  return error instanceof Error ? error : new Error(fallbackMessage)
}

async function requestJson<T>(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
): Promise<T> {
  try {
    const response = await fetch(buildApiUrl(path), init)
    if (!response.ok) {
      throw await createApiError(response, fallbackMessage)
    }

    const body = await readJsonBody<T>(response)
    if (body === null) {
      throw new Error('Server returned an empty response body')
    }
    return body
  } catch (error) {
    throw toRequestError(error, fallbackMessage)
  }
}

async function requestVoid(
  path: string,
  init: RequestInit,
  fallbackMessage: string,
) {
  try {
    const response = await fetch(buildApiUrl(path), init)
    if (!response.ok) {
      throw await createApiError(response, fallbackMessage)
    }
  } catch (error) {
    throw toRequestError(error, fallbackMessage)
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [tokens, setTokens] = useState<AuthTokens | null>(() => readStoredTokens())
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [status, setStatus] = useState<AuthStatus>(() => (
    readStoredTokens() ? 'loading' : 'unauthenticated'
  ))

  const tokensRef = useRef(tokens)
  const refreshPromiseRef = useRef<Promise<AuthTokens | null> | null>(null)

  useEffect(() => {
    tokensRef.current = tokens
  }, [tokens])

  const applyTokens = (nextTokens: AuthTokens | null) => {
    setTokens(nextTokens)
    tokensRef.current = nextTokens
    writeStoredTokens(nextTokens)

    if (!nextTokens) {
      setUser(null)
      setStatus('unauthenticated')
      return
    }

    setStatus('authenticated')
  }

  const refreshSession = async () => {
    const currentTokens = tokensRef.current
    if (!currentTokens?.refreshToken) {
      applyTokens(null)
      return null
    }

    if (refreshPromiseRef.current) {
      return refreshPromiseRef.current
    }

    refreshPromiseRef.current = (async () => {
      try {
        const nextTokens = await requestJson<AuthTokens>(
          '/auth/refresh',
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify({
              refreshToken: currentTokens.refreshToken,
            }),
          },
          '工作階段更新失敗',
        )

        applyTokens(nextTokens)
        return nextTokens
      } catch {
        applyTokens(null)
        return null
      } finally {
        refreshPromiseRef.current = null
      }
    })()

    return refreshPromiseRef.current
  }

  const authFetch = async (path: string, init: RequestInit = {}) => {
    const currentTokens = tokensRef.current
    if (!currentTokens?.accessToken) {
      throw new ApiError(401, 'UNAUTHORIZED', '請先登入')
    }

    const makeRequest = async (accessToken: string) => {
      try {
        return await fetch(buildApiUrl(path), {
          ...init,
          headers: mergeHeaders(init.headers, {
            Authorization: `Bearer ${accessToken}`,
          }),
        })
      } catch (error) {
        throw toRequestError(error, '受保護請求失敗')
      }
    }

    let response = await makeRequest(currentTokens.accessToken)
    if (response.status !== 401) {
      return response
    }

    const refreshedTokens = await refreshSession()
    if (!refreshedTokens?.accessToken) {
      return response
    }

    response = await makeRequest(refreshedTokens.accessToken)
    return response
  }

  const refreshUser = async () => {
    if (!tokensRef.current) {
      setUser(null)
      setStatus('unauthenticated')
      return
    }

    setStatus('loading')

    try {
      const response = await authFetch('/auth/me')
      if (!response.ok) {
        throw await createApiError(response, '載入目前使用者失敗')
      }

      const currentUser = await readJsonBody<CurrentUser>(response)
      if (currentUser === null) {
        throw new Error('Server returned an empty response body')
      }

      setUser(currentUser)
      setStatus('authenticated')
    } catch {
      applyTokens(null)
    }
  }

  useEffect(() => {
    if (!tokensRef.current) {
      setStatus('unauthenticated')
      return
    }

    void refreshUser()
  }, [])

  const forgotPassword = async (email: string) => {
    await requestVoid('/auth/forgot-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email }),
    }, '密碼重設請求失敗')
  }

  const confirmForgotPassword = async (
    email: string,
    confirmationCode: string,
    newPassword: string,
  ) => {
    await requestVoid('/auth/confirm-forgot-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, confirmationCode, newPassword }),
    }, '設定新密碼失敗')
  }

  const login = async (email: string, password: string): Promise<LoginOutcome> => {
    const response = await requestJson<LoginResponse>('/auth/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    }, '登入失敗')

    if (!isLoginResponse(response)) {
      throw new Error('登入回應格式不正確')
    }

    if (response.tokens && !response.challenge) {
      applyTokens(response.tokens)
      await refreshUser()
      return { type: 'authenticated' }
    }

    if (response.challenge && !response.tokens) {
      return { type: 'challenge', challenge: response.challenge }
    }

    throw new Error('登入回應格式不正確')
  }

  const respondToNewPassword = async (
    email: string,
    newPassword: string,
    session: string,
  ) => {
    const nextTokens = await requestJson<AuthTokens>('/auth/respond-to-new-password', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, newPassword, session }),
    }, '設定首次登入密碼失敗')

    applyTokens(nextTokens)
    await refreshUser()
  }

  const logout = async () => {
    const currentTokens = tokensRef.current

    if (currentTokens) {
      try {
        await fetch(buildApiUrl('/auth/logout'), {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${currentTokens.accessToken}`,
          },
          body: JSON.stringify({
            refreshToken: currentTokens.refreshToken,
          }),
        })
      } catch {
        // Local sign-out must still succeed if the backend request fails.
      }
    }

    applyTokens(null)
  }

  return (
    <AuthContext.Provider value={{
      status,
      user,
      tokens,
      login,
      forgotPassword,
      confirmForgotPassword,
      respondToNewPassword,
      logout,
      refreshUser,
      authFetch,
    }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return context
}
