import { useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import './Login.css'

export default function ChangePassword() {
  const { isAuthenticated, newPasswordRequired, completeNewPassword } = useAuth()
  const navigate = useNavigate()

  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [isLoading, setIsLoading] = useState(false)

  if (!isAuthenticated && !newPasswordRequired) {
    navigate('/login', { replace: true })
    return null
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')

    if (newPassword !== confirmPassword) {
      setError('新密碼與確認密碼不一致')
      return
    }
    if (newPassword.length < 8) {
      setError('新密碼至少需要 8 個字元')
      return
    }
    if (!/[A-Z]/.test(newPassword)) {
      setError('新密碼需包含至少一個大寫字母 (A-Z)')
      return
    }
    if (!/[a-z]/.test(newPassword)) {
      setError('新密碼需包含至少一個小寫字母 (a-z)')
      return
    }
    if (!/[0-9]/.test(newPassword)) {
      setError('新密碼需包含至少一個數字 (0-9)')
      return
    }
    if (!/[^A-Za-z0-9]/.test(newPassword)) {
      setError('新密碼需包含至少一個特殊符號 (例如 !@#$%)')
      return
    }

    setIsLoading(true)
    try {
      if (newPasswordRequired) {
        await completeNewPassword(newPassword)
        navigate('/', { replace: true })
        return
      }
      const { updatePassword } = await import('aws-amplify/auth')
      await updatePassword({ oldPassword: currentPassword, newPassword })
      setSuccess(true)
    } catch (err) {
      setError(err instanceof Error ? err.message : '密碼更新失敗，請確認目前密碼是否正確')
    } finally {
      setIsLoading(false)
    }
  }

  if (success) {
    return (
      <div className="login-container">
        <div className="login-card">
          <div className="login-header">
            <h1>密碼已更新</h1>
            <p className="login-subtitle">您的密碼已成功變更</p>
          </div>
          <button className="login-button" onClick={() => navigate('/')}>
            返回首頁
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h1>{newPasswordRequired ? '請設定新密碼' : '變更密碼'}</h1>
          <p className="login-subtitle">
            {newPasswordRequired
              ? '您的帳號需要設定新密碼才能繼續'
              : '請輸入目前密碼及新密碼'}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="login-form">
          {!newPasswordRequired && (
            <div className="form-group">
              <label htmlFor="current-password">目前密碼</label>
              <input
                id="current-password"
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                required
                autoComplete="current-password"
                placeholder="••••••••"
                className="form-input"
              />
            </div>
          )}

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
            <ul style={{ margin: '6px 0 0', paddingLeft: '18px', fontSize: '12px', color: 'var(--text-muted, #888)', lineHeight: '1.6' }}>
              <li>至少 8 個字元</li>
              <li>包含大寫字母 (A-Z)</li>
              <li>包含小寫字母 (a-z)</li>
              <li>包含數字 (0-9)</li>
              <li>包含特殊符號，例如 !@#$%^&*</li>
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

          {error && (
            <div className="error-message">
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
                <circle cx="8" cy="8" r="7" stroke="currentColor" strokeWidth="1.5"/>
                <path d="M8 4v5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round"/>
                <circle cx="8" cy="11" r="0.75" fill="currentColor"/>
              </svg>
              {error}
            </div>
          )}

          <button type="submit" disabled={isLoading} className="login-button">
            {isLoading ? (
              <>
                <span className="spinner"></span>
                更新中...
              </>
            ) : (
              '更新密碼'
            )}
          </button>

          {!newPasswordRequired && (
            <button
              type="button"
              onClick={() => navigate(-1)}
              style={{
                marginTop: '8px',
                padding: '10px',
                width: '100%',
                background: 'transparent',
                border: '1px solid var(--border-color, #ccc)',
                borderRadius: '6px',
                cursor: 'pointer',
                fontSize: '14px',
                color: 'inherit',
              }}
            >
              取消
            </button>
          )}
        </form>
      </div>
    </div>
  )
}
