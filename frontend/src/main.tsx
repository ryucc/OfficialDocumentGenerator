import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes, Link, useLocation } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import AuthPage from './AuthPage.tsx'
import Documents from './Documents.tsx'
import DocumentDetail from './DocumentDetail.tsx'
import { AuthProvider, useAuth } from './auth.tsx'

function Navigation() {
  const location = useLocation()
  const { status, user, logout } = useAuth()
  const isDocumentsRoute = location.pathname.startsWith('/documents')

  return (
    <nav className="top-nav">
      <Link
        to="/"
        className={`nav-link ${location.pathname === '/' ? 'active' : ''}`}
      >
        公文產生器
      </Link>
      <Link
        to="/documents"
        className={`nav-link ${isDocumentsRoute ? 'active' : ''}`}
      >
        範例管理
      </Link>
      <div className="nav-spacer" />
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
  return (
    <>
      <Navigation />
      <Routes>
        <Route path="/" element={<App />} />
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
