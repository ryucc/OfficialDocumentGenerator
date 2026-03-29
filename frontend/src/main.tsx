import { StrictMode, useState, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import Documents from './Documents.tsx'
import Projects from './Projects.tsx'

function Navigation({ theme, setTheme }: { theme: 'dark' | 'light', setTheme: (theme: 'dark' | 'light') => void }) {
  const location = useLocation()

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
          to="/generator"
          className={`nav-link ${location.pathname === '/generator' ? 'active' : ''}`}
        >
          公文產生器
        </Link>
      </div>
      <button className="theme-toggle-nav" onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}>
        {theme === 'dark' ? '☀️' : '🌙'}
      </button>
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

  return (
    <>
      <Navigation theme={theme} setTheme={setTheme} />
      <Routes>
        <Route path="/" element={<Projects />} />
        <Route path="/documents" element={<Documents />} />
        <Route path="/generator" element={<App />} />
      </Routes>
    </>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AppRouter />
    </BrowserRouter>
  </StrictMode>,
)
