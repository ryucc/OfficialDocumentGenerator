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

function InstalledSkills() {
  const { user } = useAuth()
  const [skills, setSkills] = useState<Skill[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [loadingSkillId, setLoadingSkillId] = useState<string | null>(null)

  useEffect(() => {
    fetchData()
  }, [])

  const fetchData = async () => {
    if (!user) return
    try {
      setIsLoading(true)
      setError(null)

      const [allSkillsRes, installedRes] = await Promise.all([
        authenticatedFetch(`${API_BASE_URL}/skills`),
        authenticatedFetch(`${API_BASE_URL}/users/${user.userId}/skills`),
      ])

      if (!allSkillsRes.ok) throw new Error(`API Error: ${allSkillsRes.status}`)
      if (!installedRes.ok) throw new Error(`API Error: ${installedRes.status}`)

      const allSkillsData = await allSkillsRes.json()
      const installedData = await installedRes.json()
      const installedIds = new Set<string>(installedData.installedSkills || [])

      const allSkills: Skill[] = allSkillsData.items || []
      setSkills(allSkills.filter(s => installedIds.has(s.skillId)))
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入失敗')
    } finally {
      setIsLoading(false)
    }
  }

  const uninstallSkill = async (skillId: string) => {
    if (!user) return
    setLoadingSkillId(skillId)
    try {
      const res = await authenticatedFetch(
        `${API_BASE_URL}/users/${user.userId}/skills/${skillId}`,
        { method: 'DELETE' }
      )
      if (!res.ok) throw new Error(`API Error: ${res.status}`)
      setSkills(prev => prev.filter(s => s.skillId !== skillId))
    } catch (err) {
      console.error('Error uninstalling skill:', err)
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
        <h1>已安裝技能</h1>
      </div>

      {skills.length === 0 ? (
        <div className="empty-state">
          <p>尚未安裝任何技能</p>
          <p className="empty-hint">前往技能市集探索可用的技能</p>
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
            {skills.map((skill) => (
              <tr key={skill.skillId}>
                <td className="skill-name">{skill.displayName || skill.name}</td>
                <td className="skill-owner">{skill.owner}</td>
                <td className="skill-description">{skill.description}</td>
                <td className="skill-action">
                  <button
                    className="skill-btn uninstall-btn"
                    onClick={() => uninstallSkill(skill.skillId)}
                    disabled={loadingSkillId === skill.skillId}
                  >
                    {loadingSkillId === skill.skillId ? '...' : '解除安裝'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export default InstalledSkills
