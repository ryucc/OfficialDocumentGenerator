import { useState, useEffect } from 'react'
import { useParams, Link } from 'react-router-dom'
import { API_BASE_URL } from './config'
import { authenticatedFetch } from './api'
import { useAuth } from './AuthContext'
import './Skills.css'

interface SkillDetailData {
  skillId: string
  displayName: string
  name: string
  description: string
  owner: string
  language: string
  version: string
  schemaJson?: string
  instructionsMd: string
  instructionsHtml: string
  createdAt: string
  updatedAt: string
}

function SkillDetail() {
  const { skillId } = useParams<{ skillId: string }>()
  const { user } = useAuth()
  const [skill, setSkill] = useState<SkillDetailData | null>(null)
  const [installed, setInstalled] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    fetchData()
  }, [skillId])

  const fetchData = async () => {
    if (!skillId) return
    try {
      setIsLoading(true)
      setError(null)

      const [skillRes, installedRes] = await Promise.all([
        authenticatedFetch(`${API_BASE_URL}/skills/${skillId}`),
        user ? authenticatedFetch(`${API_BASE_URL}/users/${user.userId}/skills`) : null,
      ])

      if (!skillRes.ok) throw new Error(`API Error: ${skillRes.status}`)
      const skillData = await skillRes.json()
      setSkill(skillData)

      if (installedRes?.ok) {
        const installedData = await installedRes.json()
        const ids = new Set<string>(installedData.installedSkills || [])
        setInstalled(ids.has(skillId))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入失敗')
    } finally {
      setIsLoading(false)
    }
  }

  const toggleInstall = async () => {
    if (!user || !skillId) return
    setActionLoading(true)
    try {
      const method = installed ? 'DELETE' : 'PUT'
      const res = await authenticatedFetch(
        `${API_BASE_URL}/users/${user.userId}/skills/${skillId}`,
        { method }
      )
      if (!res.ok) throw new Error(`API Error: ${res.status}`)
      setInstalled(!installed)
    } catch (err) {
      console.error('Error toggling skill:', err)
    } finally {
      setActionLoading(false)
    }
  }

  if (isLoading) {
    return (
      <div className="skills-container">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>載入中...</p>
        </div>
      </div>
    )
  }

  if (error || !skill) {
    return (
      <div className="skills-container">
        <div className="error-state">
          <h2>載入失敗</h2>
          <p>{error || '找不到技能'}</p>
          <Link to="/marketplace" className="retry-btn">返回市集</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="skills-container">
      <div className="skill-detail-header">
        <div>
          <h1>{skill.displayName || skill.name}</h1>
          <div className="skill-meta">
            <span>作者：{skill.owner}</span>
            <span>語言：{skill.language}</span>
            <span>版本：{skill.version}</span>
          </div>
        </div>
        <button
          className={`skill-btn ${installed ? 'uninstall-btn' : 'install-btn'}`}
          onClick={toggleInstall}
          disabled={actionLoading}
        >
          {actionLoading ? '...' : installed ? '解除安裝' : '安裝'}
        </button>
      </div>

      <p className="skill-detail-description">{skill.description}</p>

      <div className="skill-detail-section">
        <h2>填寫說明</h2>
        <div
          className="skill-instructions"
          dangerouslySetInnerHTML={{ __html: skill.instructionsHtml }}
        />
      </div>

      {skill.schemaJson && (
        <div className="skill-detail-section">
          <h2>資料結構</h2>
          <pre className="skill-schema">
            {JSON.stringify(JSON.parse(skill.schemaJson), null, 2)}
          </pre>
        </div>
      )}
    </div>
  )
}

export default SkillDetail
