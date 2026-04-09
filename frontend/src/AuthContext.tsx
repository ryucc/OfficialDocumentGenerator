import { createContext, useContext, useState, useEffect } from 'react'
import type { ReactNode } from 'react'
import { COGNITO_CONFIG } from './config'

const IS_MOCK = import.meta.env.VITE_MOCK === 'true'

if (!IS_MOCK) {
  // Only configure Amplify when not in mock mode
  import('aws-amplify').then(({ Amplify }) => {
    Amplify.configure({
      Auth: {
        Cognito: {
          userPoolId: COGNITO_CONFIG.userPoolId,
          userPoolClientId: COGNITO_CONFIG.userPoolClientId,
          loginWith: {
            email: true,
          },
        },
      },
    })
  })
}

interface AuthContextType {
  isAuthenticated: boolean
  isLoading: boolean
  user: { email: string; userId: string } | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  getIdToken: () => Promise<string | undefined>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(IS_MOCK)
  const [isLoading, setIsLoading] = useState(!IS_MOCK)
  const [user, setUser] = useState<{ email: string; userId: string } | null>(
    IS_MOCK ? { email: 'mock@example.com', userId: 'mock-user-id' } : null
  )

  useEffect(() => {
    if (!IS_MOCK) {
      checkAuth()
    }
  }, [])

  async function checkAuth() {
    try {
      const { getCurrentUser } = await import('aws-amplify/auth')
      const currentUser = await getCurrentUser()
      setUser({ email: currentUser.signInDetails?.loginId || '', userId: currentUser.userId })
      setIsAuthenticated(true)
    } catch {
      setIsAuthenticated(false)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  async function login(email: string, password: string) {
    if (IS_MOCK) {
      setUser({ email, userId: 'mock-user-id' })
      setIsAuthenticated(true)
      return
    }
    const { signIn } = await import('aws-amplify/auth')
    await signIn({ username: email, password })
    await checkAuth()
  }

  async function logout() {
    if (!IS_MOCK) {
      const { signOut } = await import('aws-amplify/auth')
      await signOut()
    }
    setIsAuthenticated(false)
    setUser(null)
  }

  async function getIdToken(): Promise<string | undefined> {
    if (IS_MOCK) return 'mock-token'
    try {
      const { fetchAuthSession } = await import('aws-amplify/auth')
      const session = await fetchAuthSession()
      return session.tokens?.idToken?.toString()
    } catch {
      return undefined
    }
  }

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        user,
        login,
        logout,
        getIdToken,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
