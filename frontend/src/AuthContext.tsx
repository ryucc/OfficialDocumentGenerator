import { createContext, useContext, useState, useEffect } from 'react'
import type { ReactNode } from 'react'
import { Amplify } from 'aws-amplify'
import { signIn, signOut, getCurrentUser, fetchAuthSession } from 'aws-amplify/auth'
import { COGNITO_CONFIG } from './config'

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

interface AuthContextType {
  isAuthenticated: boolean
  isLoading: boolean
  user: { email: string } | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  getIdToken: () => Promise<string | undefined>
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [user, setUser] = useState<{ email: string } | null>(null)

  useEffect(() => {
    checkAuth()
  }, [])

  async function checkAuth() {
    try {
      const currentUser = await getCurrentUser()
      setUser({ email: currentUser.signInDetails?.loginId || '' })
      setIsAuthenticated(true)
    } catch {
      setIsAuthenticated(false)
      setUser(null)
    } finally {
      setIsLoading(false)
    }
  }

  async function login(email: string, password: string) {
    await signIn({ username: email, password })
    await checkAuth()
  }

  async function logout() {
    await signOut()
    setIsAuthenticated(false)
    setUser(null)
  }

  async function getIdToken(): Promise<string | undefined> {
    try {
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
