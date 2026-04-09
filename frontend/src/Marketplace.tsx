import { useState, useEffect } from 'react'
import { API_BASE_URL } from './config'
import { authenticatedFetch } from './api'
import { useAuth } from './AuthContext'
import './Skills.css'

interface Skill {
  skillId: string
  displayName: string
  name: string
  description: string
  owner: string
}

function Marketplace() {
  const { user } = useAuth()
  const [skills, setSkills] = useState<Skill[]>([])
  const [installedSkillIds, setInstalledSkillIds] = useState<Set<string>>(new Set())
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadingSkillId, setLoadingSkillId] = useState<string | null>(null)

  useEffect(() => {
    fetchData()
  }, [])

  const fetchData = async () => {
    try {
      setIsLoading(true)
      setError(null)

      const [skillsRes, installedRes] = await Promise.all([
        authenticatedFetch(`${API_BASE_URL}/skills`),
        user ? authenticatedFetch(`${API_BASE_URL}/users/${user.userId}/skills`) : null,
      ])

      if (!skillsRes.ok) throw new Error(`API Error: ${skillsRes.status}`)
      const skillsData = await skillsRes.json()
      setSkills(skillsData.items || [])

      if (installedRes?.ok) {
        const installedData = await installedRes.json()
        setInstalledSkillIds(new Set(installedData.installedSkills || []))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入失敗')
    } finally {
      setIsLoading(false)
    }
  }

  const toggleSkill = async (skillId: string, installed: boolean) => {
    if (!user) return
    setLoadingSkillId(skillId)
    try {
      const method = installed ? 'DELETE' : 'PUT'
      const res = await authenticatedFetch(
        `${API_BASE_URL}/users/${user.userId}/skills/${skillId}`,
        { method }
      )
      if (!res.ok) throw new Error(`API Error: ${res.status}`)

      setInstalledSkillIds(prev => {
        const next = new Set(prev)
        if (installed) next.delete(skillId)
        else next.add(skillId)
        return next
      })
    } catch (err) {
      console.error('Error toggling skill:', err)
    } finally {
      setLoadingSkillId(null)
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

  if (error) {
    return (
      <div className="skills-container">
        <div className="error-state">
          <h2>載入失敗</h2>
          <p>{error}</p>
          <button onClick={fetchData} className="retry-btn">重試</button>
        </div>
      </div>
    )
  }

  return (
    <div className="skills-container">
      <div className="skills-header">
        <h1>技能市集</h1>
      </div>

      {skills.length === 0 ? (
        <div className="empty-state">
          <p>目前沒有可用的技能</p>
        </div>
      ) : (
        <table className="skills-table">
          <thead>
            <tr>
              <th>名稱</th>
              <th>作者</th>
              <th>說明</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {skills.map((skill) => {
              const installed = installedSkillIds.has(skill.skillId)
              return (
                <tr key={skill.skillId}>
                  <td className="skill-name">{skill.displayName || skill.name}</td>
                  <td className="skill-owner">{skill.owner}</td>
                  <td className="skill-description">{skill.description}</td>
                  <td className="skill-action">
                    <button
                      className={`skill-btn ${installed ? 'uninstall-btn' : 'install-btn'}`}
                      onClick={() => toggleSkill(skill.skillId, installed)}
                      disabled={loadingSkillId === skill.skillId}
                    >
                      {loadingSkillId === skill.skillId
                        ? '...'
                        : installed ? '解除安裝' : '安裝'}
                    </button>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}

export default Marketplace
