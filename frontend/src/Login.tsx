import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from './AuthContext'
import { useNavigate } from 'react-router-dom'
import './Login.css'

type Step = 'login' | 'forgot-request' | 'forgot-confirm'

const PASSWORD_RULES = [
  '至少 8 個字元',
  '包含大寫字母 (A-Z)',
  '包含小寫字母 (a-z)',
  '包含數字 (0-9)',
  '包含特殊符號，例如 !@#$%^&*',
]

function validatePassword(password: string): string | null {
  if (password.length < 8) return '密碼至少需要 8 個字元'
  if (!/[A-Z]/.test(password)) return '密碼需包含至少一個大寫字母 (A-Z)'
  if (!/[a-z]/.test(password)) return '密碼需包含至少一個小寫字母 (a-z)'
  if (!/[0-9]/.test(password)) return '密碼需包含至少一個數字 (0-9)'
  if (!/[^A-Za-z0-9]/.test(password)) return '密碼需包含至少一個特殊符號 (例如 !@#$%)'
  return null
}

function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="error-message">
      <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
        <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5"/>
        <path d="M8 4v5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
        <circle cx="8" cy="11" r="0.75" fill="currentColor"/>
      </svg>
      {message}
    </div>
  )
}

export default function Login() {
  const [step, setStep] = useState<Step>('login')

  // login fields
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  // forgot-confirm fields
  const [resetEmail, setResetEmail] = useState('')
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')

  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  const { login, requestPasswordReset, confirmPasswordReset } = useAuth()
  const navigate = useNavigate()

  async function handleLogin(e: FormEvent) {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      const result = await login(email, password)
      if (result.newPasswordRequired) {
        navigate('/change-password')
        return
      }
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : '登入失敗，請檢查您的帳號密碼')
    } finally {
      setIsLoading(false)
    }
  }

  async function handleForgotRequest(e: FormEvent) {
    e.preventDefault()
    setError('')
    setIsLoading(true)
    try {
      await requestPasswordReset(resetEmail)
      setStep('forgot-confirm')
    } catch (err) {
      setError(err instanceof Error ? err.message : '發送驗證碼失敗，請確認電子郵件是否正確')
    } finally {
      setIsLoading(false)
    }
  }

  async function handleForgotConfirm(e: FormEvent) {
    e.preventDefault()
    setError('')
    const pwError = validatePassword(newPassword)
    if (pwError) { setError(pwError); return }
    if (newPassword !== confirmPassword) { setError('新密碼與確認密碼不一致'); return }
    setIsLoading(true)
    try {
      await confirmPasswordReset(resetEmail, code, newPassword)
      setSuccessMessage('密碼已重設成功，請使用新密碼登入。')
      setStep('login')
      setCode('')
      setNewPassword('')
      setConfirmPassword('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '重設密碼失敗，請確認驗證碼是否正確')
    } finally {
      setIsLoading(false)
    }
  }

  function goToForgot() {
    setResetEmail(email)
    setError('')
    setSuccessMessage('')
    setStep('forgot-request')
  }

  function goToLogin() {
    setError('')
    setStep('login')
  }

  if (step === 'forgot-request') {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <h1>重設密碼</h1>
            <p className="login-subtitle">輸入您的電子郵件，我們將寄送驗證碼</p>
          </div>

          <form onSubmit={handleForgotRequest} className="login-form">
            <div className="form-group">
              <label htmlFor="reset-email">電子郵件</label>
              <input
                id="reset-email"
                type="email"
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                required
                autoComplete="email"
                placeholder="your@email.com"
                className="form-input"
                autoFocus
              />
            </div>

            {error && <ErrorMessage message={error} />}

            <button type="submit" disabled={isLoading} className="login-button">
              {isLoading ? <><span className="spinner"></span>傳送中...</> : '傳送驗證碼'}
            </button>

            <button type="button" onClick={goToLogin} className="forgot-back-link">
              返回登入
            </button>
          </form>
        </div>
      </div>
    )
  }

  if (step === 'forgot-confirm') {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <h1>輸入驗證碼</h1>
            <p className="login-subtitle">驗證碼已寄送至 {resetEmail}</p>
          </div>

          <form onSubmit={handleForgotConfirm} className="login-form">
            <div className="form-group">
              <label htmlFor="code">驗證碼</label>
              <input
                id="code"
                type="text"
                value={code}
                onChange={(e) => setCode(e.target.value)}
                required
                autoComplete="one-time-code"
                placeholder="123456"
                className="form-input"
                autoFocus
              />
            </div>

            <div className="form-group">
              <label htmlFor="new-password">新密碼</label>
              <input
                id="new-password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
                autoComplete="new-password"
                placeholder="••••••••"
                className="form-input"
              />
              <ul className="password-rules">
                {PASSWORD_RULES.map((r) => <li key={r}>{r}</li>)}
              </ul>
            </div>

            <div className="form-group">
              <label htmlFor="confirm-password">確認新密碼</label>
              <input
                id="confirm-password"
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
                autoComplete="new-password"
                placeholder="••••••••"
                className="form-input"
              />
            </div>

            {error && <ErrorMessage message={error} />}

            <button type="submit" disabled={isLoading} className="login-button">
              {isLoading ? <><span className="spinner"></span>重設中...</> : '確認重設密碼'}
            </button>

            <button type="button" onClick={() => setStep('forgot-request')} className="forgot-back-link">
              重新傳送驗證碼
            </button>
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>官方文件產生器</h1>
          <p className="login-subtitle">登入您的帳號</p>
        </div>

        <form onSubmit={handleLogin} className="login-form">
          <div className="form-group">
            <label htmlFor="email">電子郵件</label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              placeholder="your@email.com"
              className="form-input"
            />
          </div>

          <div className="form-group">
            <div className="password-label-row">
              <label htmlFor="password">密碼</label>
              <button type="button" onClick={goToForgot} className="forgot-password-link">
                忘記密碼？
              </button>
            </div>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              placeholder="••••••••"
              className="form-input"
            />
          </div>

          {successMessage && (
            <div className="success-message">{successMessage}</div>
          )}

          {error && <ErrorMessage message={error} />}

          <button type="submit" disabled={isLoading} className="login-button">
            {isLoading ? <><span className="spinner"></span>登入中...</> : '登入'}
          </button>
        </form>

        <div className="login-footer">
          <p>請使用管理員提供的帳號密碼登入</p>
        </div>
      </div>
    </div>
  )
}
