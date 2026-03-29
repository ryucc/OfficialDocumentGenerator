import { useState } from 'react'
import type { FormEvent } from 'react'
import { useAuth } from './AuthContext'
import { useNavigate } from 'react-router-dom'

export default function Login() {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setIsLoading(true)

    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Please check your credentials.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <div style={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      padding: '20px',
    }}>
      <div style={{
        width: '100%',
        maxWidth: '400px',
        padding: '40px',
        border: '1px solid var(--border-color)',
        borderRadius: '8px',
        backgroundColor: 'var(--bg-color)',
      }}>
        <h1 style={{ marginBottom: '30px', textAlign: 'center' }}>登入</h1>

        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '20px' }}>
            <label htmlFor="email" style={{ display: 'block', marginBottom: '8px' }}>
              電子郵件
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
              style={{
                width: '100%',
                padding: '10px',
                border: '1px solid var(--border-color)',
                borderRadius: '4px',
                fontSize: '16px',
              }}
            />
          </div>

          <div style={{ marginBottom: '20px' }}>
            <label htmlFor="password" style={{ display: 'block', marginBottom: '8px' }}>
              密碼
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
              style={{
                width: '100%',
                padding: '10px',
                border: '1px solid var(--border-color)',
                borderRadius: '4px',
                fontSize: '16px',
              }}
            />
          </div>

          {error && (
            <div style={{
              padding: '10px',
              marginBottom: '20px',
              backgroundColor: '#fee',
              color: '#c00',
              borderRadius: '4px',
              fontSize: '14px',
            }}>
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading}
            style={{
              width: '100%',
              padding: '12px',
              backgroundColor: 'var(--primary-color)',
              color: 'white',
              border: 'none',
              borderRadius: '4px',
              fontSize: '16px',
              cursor: isLoading ? 'not-allowed' : 'pointer',
              opacity: isLoading ? 0.6 : 1,
            }}
          >
            {isLoading ? '登入中...' : '登入'}
          </button>
        </form>
      </div>
    </div>
  )
}
