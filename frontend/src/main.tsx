import { StrictMode, useState, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes, Link, useLocation } from 'react-router-dom'
import './index.css'
import AuthPage from './AuthPage.tsx'
import Documents from './Documents.tsx'
import DocumentDetail from './DocumentDetail.tsx'
import Projects from './Projects.tsx'
import { AuthProvider, useAuth } from './auth.tsx'

function Navigation({ theme, setTheme }: { theme: 'dark' | 'light', setTheme: (theme: 'dark' | 'light') => void }) {
  const location = useLocation()
  const { status, user, logout } = useAuth()
  const isDocumentsRoute = location.pathname.startsWith('/documents')

  return (
    <nav className="top-nav">
      <div className="nav-links">
        <Link
          to="/"
          className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}
        >
          郵件專案
        </Link>
        <Link
          to="/documents"
          className={`nav-link ${isDocumentsRoute ? 'active' : ''}`}
        >
          範例管理
        </Link>
      </div>
      <div className="nav-actions">
        {status === 'authenticated' && user ? (
          <>
            <span className="nav-user">{user.email || user.userId}</span>
            <button
              className="nav-action"
              onClick={() => {
                void logout()
              }}
              type="button"
            >
              登出
            </button>
          </>
        ) : (
          <Link
            to="/auth"
            className={`nav-link ${location.pathname === '/auth' ? 'active' : ''}`}
          >
            登入
          </Link>
        )}
        <button
          className="theme-toggle-nav"
          onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
          type="button"
        >
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
      </div>
    </nav>
  )
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return (
      <div className="route-loading">
        <div className="route-loading-spinner" />
        <p>正在驗證登入狀態...</p>
      </div>
    )
  }

  if (status !== 'authenticated') {
    return (
      <Navigate
        replace
        to={`/auth?next=${encodeURIComponent(location.pathname + location.search)}`}
      />
    )
  }

  return children
}

function AppRouter() {
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    return (localStorage.getItem('theme') as 'dark' | 'light') || 'light'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('theme', theme)
  }, [theme])

  return (
    <>
      <Navigation theme={theme} setTheme={setTheme} />
      <Routes>
        <Route path="/" element={<Projects />} />
        <Route path="/auth" element={<AuthPage />} />
        <Route
          path="/documents"
          element={(
            <ProtectedRoute>
              <Documents />
            </ProtectedRoute>
          )}
        />
        <Route
          path="/documents/:id"
          element={(
            <ProtectedRoute>
              <DocumentDetail />
            </ProtectedRoute>
          )}
        />
      </Routes>
    </>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <BrowserRouter>
        <AppRouter />
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>,
)
