import { StrictMode, useState, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Link, useLocation, Navigate } from 'react-router-dom'
import './index.css'
import Documents from './Documents.tsx'
import Projects from './Projects.tsx'
import Marketplace from './Marketplace.tsx'
import InstalledSkills from './InstalledSkills.tsx'
import SkillDetail from './SkillDetail.tsx'
import Login from './Login.tsx'
import Welcome from './Welcome.tsx'
import ChangePassword from './ChangePassword.tsx'
import ProtectedRoute from './ProtectedRoute.tsx'
import { AuthProvider, useAuth } from './AuthContext.tsx'

if (import.meta.env.VITE_MOCK === 'true') {
  const { setupMockApi } = await import('./mockApi')
  setupMockApi()
}

function Navigation({ theme, setTheme }: { theme: 'dark' | 'light', setTheme: (theme: 'dark' | 'light') => void }) {
  const location = useLocation()
  const { isAuthenticated, logout, user } = useAuth()

  if (!isAuthenticated) {
    return null
  }

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
          className={`nav-link ${location.pathname === '/documents' ? 'active' : ''}`}
        >
          範例管理
        </Link>
        <Link
          to="/skills"
          className={`nav-link ${location.pathname === '/skills' ? 'active' : ''}`}
        >
          已安裝技能
        </Link>
        <Link
          to="/marketplace"
          className={`nav-link ${location.pathname === '/marketplace' ? 'active' : ''}`}
        >
          技能市集
        </Link>
      </div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <span style={{ fontSize: '14px', opacity: 0.8 }}>{user?.email}</span>
        <Link
          to="/change-password"
          style={{
            padding: '6px 12px',
            fontSize: '14px',
            border: '1px solid var(--border-color)',
            borderRadius: '4px',
            textDecoration: 'none',
            color: 'inherit',
          }}
        >
          變更密碼
        </Link>
        <button
          onClick={() => logout()}
          style={{
            padding: '6px 12px',
            fontSize: '14px',
            cursor: 'pointer',
            border: '1px solid var(--border-color)',
            borderRadius: '4px',
            backgroundColor: 'transparent',
          }}
        >
          登出
        </button>
        <button className="theme-toggle-nav" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? '☀️' : '🌙'}
        </button>
      </div>
    </nav>
  )
}

function AppRouter() {
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    return (localStorage.getItem('theme') as 'dark' | 'light') || 'light'
  })

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('theme', theme)
  }, [theme])

  const { isAuthenticated } = useAuth()

  return (
    <>
      <Navigation theme={theme} setTheme={setTheme} />
      <Routes>
        <Route
          path="/welcome"
          element={isAuthenticated ? <Navigate to="/" replace /> : <Welcome />}
        />
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/" replace /> : <Login />}
        />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Projects />
            </ProtectedRoute>
          }
        />
        <Route
          path="/documents"
          element={
            <ProtectedRoute>
              <Documents />
            </ProtectedRoute>
          }
        />
        <Route
          path="/skills"
          element={
            <ProtectedRoute>
              <InstalledSkills />
            </ProtectedRoute>
          }
        />
        <Route
          path="/skills/:skillId"
          element={
            <ProtectedRoute>
              <SkillDetail />
            </ProtectedRoute>
          }
        />
        <Route
          path="/marketplace"
          element={
            <ProtectedRoute>
              <Marketplace />
            </ProtectedRoute>
          }
        />
        <Route
          path="/change-password"
          element={<ChangePassword />}
        />
      </Routes>
    </>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <AppRouter />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
