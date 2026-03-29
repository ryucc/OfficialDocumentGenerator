import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import type { AuthChallenge } from './auth'
import { useAuth } from './auth'
import './AuthPage.css'

type AuthView = 'login' | 'reset-password' | 'new-password-required'

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}

function AuthPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const {
    status,
    user,
    login,
    forgotPassword,
    confirmForgotPassword,
    respondToNewPassword,
  } = useAuth()

  const nextPath = searchParams.get('next') || '/documents'
  const [view, setView] = useState<AuthView>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmationCode, setConfirmationCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [challenge, setChallenge] = useState<AuthChallenge | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    if (status === 'authenticated') {
      navigate(nextPath, { replace: true })
    }
  }, [navigate, nextPath, status])

  const resetFeedback = () => {
    setError(null)
    setMessage(null)
  }

  const returnToLogin = () => {
    setView('login')
    setPassword('')
    setConfirmationCode('')
    setNewPassword('')
    setChallenge(null)
    resetFeedback()
  }

  const handleForgotPassword = async () => {
    const normalizedEmail = email.trim()
    if (!isValidEmail(normalizedEmail)) {
      setError('請先輸入有效的電子郵件，再申請密碼重設。')
      setMessage(null)
      return
    }

    setIsSubmitting(true)
    resetFeedback()

    try {
      await forgotPassword(normalizedEmail)
      setEmail(normalizedEmail)
      setView('reset-password')
      setPassword('')
      setConfirmationCode('')
      setNewPassword('')
      setMessage('重設驗證碼已寄出。請輸入驗證碼與新密碼。')
    } catch (submissionError) {
      setError(submissionError instanceof Error ? submissionError.message : '重設密碼流程啟動失敗')
    } finally {
      setIsSubmitting(false)
    }
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setIsSubmitting(true)
    resetFeedback()

    try {
      if (view === 'login') {
        const result = await login(email.trim(), password)
        if (result.type === 'challenge') {
          setChallenge(result.challenge)
          setEmail(result.challenge.email)
          setPassword('')
          setNewPassword('')
          setView('new-password-required')
          setMessage('首次登入需要先設定新密碼。')
        }
        return
      }

      if (view === 'reset-password') {
        await confirmForgotPassword(email.trim(), confirmationCode, newPassword)
        setMessage('密碼已更新，請使用新密碼登入。')
        setConfirmationCode('')
        setNewPassword('')
        setView('login')
        return
      }

      if (!challenge) {
        throw new Error('缺少登入挑戰資訊，請重新登入。')
      }

      await respondToNewPassword(challenge.email, newPassword, challenge.session)
    } catch (submissionError) {
      setError(submissionError instanceof Error ? submissionError.message : '操作失敗')
    } finally {
      setIsSubmitting(false)
    }
  }

  const heading = {
    login: '登入',
    'reset-password': '重設密碼',
    'new-password-required': '設定新密碼',
  }[view]

  const subtitle = {
    login: '帳號由管理者建立。輸入信箱與密碼後，前端會透過後端 `/auth/*` 端點完成登入。',
    'reset-password': '請輸入信箱收到的驗證碼與新密碼，完成密碼重設。',
    'new-password-required': '這個帳號目前仍是暫時密碼狀態。設定新密碼後才會完成登入。',
  }[view]

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <p className="auth-eyebrow">Cognito Access</p>
        <h1>單一路徑登入，直接對齊後端 Cognito 流程</h1>
        <p className="auth-hero-copy">
          這個前端不再提供自行註冊。帳號由管理端建立，登入、首次改密碼、
          忘記密碼與 token refresh 全都透過後端 API 轉接 Cognito。
        </p>
        <div className="auth-hero-points">
          <span>首次登入會 inline 要求新密碼</span>
          <span>忘記密碼會先驗證 email 再進入重設</span>
        </div>
        {user && (
          <div className="auth-session-note">
            目前登入為 {user.email || user.userId}
          </div>
        )}
      </section>

      <section className="auth-panel">
        <div className="auth-panel-header">
          <h2>{heading}</h2>
          <p>{subtitle}</p>
        </div>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            電子郵件
            <input
              autoComplete="email"
              onChange={(event) => setEmail(event.target.value)}
              placeholder="you@example.com"
              readOnly={view === 'new-password-required'}
              required
              type="email"
              value={email}
            />
          </label>

          {view === 'login' && (
            <label>
              密碼
              <input
                autoComplete="current-password"
                onChange={(event) => setPassword(event.target.value)}
                placeholder="輸入目前密碼"
                required
                type="password"
                value={password}
              />
            </label>
          )}

          {view === 'reset-password' && (
            <>
              <label>
                驗證碼
                <input
                  autoComplete="one-time-code"
                  onChange={(event) => setConfirmationCode(event.target.value)}
                  placeholder="輸入驗證碼"
                  required
                  type="text"
                  value={confirmationCode}
                />
              </label>
              <label>
                新密碼
                <input
                  autoComplete="new-password"
                  onChange={(event) => setNewPassword(event.target.value)}
                  placeholder="輸入新密碼"
                  required
                  type="password"
                  value={newPassword}
                />
              </label>
            </>
          )}

          {view === 'new-password-required' && (
            <label>
              新密碼
              <input
                autoComplete="new-password"
                onChange={(event) => setNewPassword(event.target.value)}
                placeholder="設定新的正式密碼"
                required
                type="password"
                value={newPassword}
              />
            </label>
          )}

          {error && <div className="auth-feedback error">{error}</div>}
          {message && <div className="auth-feedback success">{message}</div>}

          <button className="auth-submit" disabled={isSubmitting} type="submit">
            {isSubmitting ? '處理中...' : heading}
          </button>
        </form>

        {view === 'login' && (
          <button
            className="auth-secondary"
            disabled={isSubmitting}
            onClick={() => {
              void handleForgotPassword()
            }}
            type="button"
          >
            忘記密碼？
          </button>
        )}

        {(view === 'reset-password' || view === 'new-password-required') && (
          <button
            className="auth-secondary"
            disabled={isSubmitting}
            onClick={returnToLogin}
            type="button"
          >
            返回登入
          </button>
        )}

        <div className="auth-footer">
          <button onClick={() => navigate('/')} type="button">
            返回首頁
          </button>
        </div>
      </section>
    </div>
  )
}

export default AuthPage
