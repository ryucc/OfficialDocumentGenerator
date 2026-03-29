import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import './index.css'
import App from './App.tsx'
import Documents from './Documents.tsx'
import Projects from './Projects.tsx'

function Navigation() {
  const location = useLocation()

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
        className={`nav-link ${location.pathname === '/documents' ? 'active' : ''}`}
      >
        範例管理
      </Link>
      <Link
        to="/projects"
        className={`nav-link ${location.pathname === '/projects' ? 'active' : ''}`}
      >
        郵件專案
      </Link>
    </nav>
  )
}

function AppRouter() {
  return (
    <>
      <Navigation />
      <Routes>
        <Route path="/" element={<App />} />
        <Route path="/documents" element={<Documents />} />
        <Route path="/projects" element={<Projects />} />
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
