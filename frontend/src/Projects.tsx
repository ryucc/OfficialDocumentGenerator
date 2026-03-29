import { useState, useEffect } from 'react'
import { API_BASE_URL } from './config'
import './Projects.css'

interface Project {
  projectId: string
  name: string
  status: string
  emailS3Key: string
  emailS3Bucket: string
  generatedDocumentS3Key?: string
  createdAt: string
  updatedAt: string
}

function Projects() {
  const [projects, setProjects] = useState<Project[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>('all')

  useEffect(() => {
    fetchProjects()
  }, [statusFilter])

  const fetchProjects = async () => {
    try {
      setIsLoading(true)
      setError(null)

      const url = statusFilter === 'all'
        ? `${API_BASE_URL}/projects`
        : `${API_BASE_URL}/projects?status=${statusFilter}`

      const response = await fetch(url)

      if (!response.ok) {
        throw new Error(`API Error: ${response.status} ${response.statusText}`)
      }

      const data = await response.json()
      setProjects(data.items || [])
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入專案時發生錯誤')
      console.error('Error fetching projects:', err)
    } finally {
      setIsLoading(false)
    }
  }

  const formatDate = (dateString: string) => {
    try {
      const date = new Date(dateString)
      const year = date.getFullYear()
      const month = String(date.getMonth() + 1).padStart(2, '0')
      const day = String(date.getDate()).padStart(2, '0')
      const hour = date.getHours()
      const minute = String(date.getMinutes()).padStart(2, '0')
      const period = hour >= 12 ? '下午' : '上午'
      const hour12 = hour % 12 || 12
      const hourStr = String(hour12).padStart(2, '0')

      return `${year}/${month}/${day} ${period}${hourStr}:${minute}`
    } catch {
      return dateString
    }
  }

  const getStatusBadge = (status: string) => {
    const statusMap: Record<string, { text: string; className: string }> = {
      'in_progress': { text: '處理中', className: 'status-in-progress' },
      'finished': { text: '已完成', className: 'status-finished' },
    }

    const statusInfo = statusMap[status] || { text: status, className: 'status-unknown' }

    return (
      <span className={`status-badge ${statusInfo.className}`}>
        {statusInfo.text}
      </span>
    )
  }

  const getEmailFileName = (s3Key: string) => {
    // Extract filename from S3 key like "emails/test/message-id"
    const parts = s3Key.split('/')
    return parts[parts.length - 1] || s3Key
  }

  if (isLoading) {
    return (
      <div className="projects-container">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>載入專案中...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="projects-container">
        <div className="error-state">
          <h2>載入失敗</h2>
          <p>{error}</p>
          <button onClick={fetchProjects} className="retry-btn">重試</button>
        </div>
      </div>
    )
  }

  return (
    <div className="projects-container">
      <div className="projects-header">
        <h1>郵件專案</h1>
        <div className="filter-controls">
          <label>狀態篩選：</label>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="status-filter"
          >
            <option value="all">全部</option>
            <option value="in_progress">處理中</option>
            <option value="finished">已完成</option>
          </select>
        </div>
      </div>

      {projects.length === 0 ? (
        <div className="empty-state">
          <p>目前沒有郵件專案</p>
          <p className="empty-hint">當有新郵件寄送至 ai@gongwengpt.click 時，將自動建立專案</p>
        </div>
      ) : (
        <table className="projects-table">
          <thead>
            <tr>
              <th>郵件標題</th>
              <th>生成公文</th>
              <th>狀態</th>
              <th>建立時間</th>
              <th>更新時間</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => (
              <tr key={project.projectId}>
                <td className="project-email">
                  <span className="email-subject" title={project.emailS3Key}>
                    {project.name || getEmailFileName(project.emailS3Key)}
                  </span>
                </td>
                <td className="project-document">
                  {project.generatedDocumentS3Key ? (
                    <span className="has-document" title={project.generatedDocumentS3Key}>
                      ✓ 已生成
                    </span>
                  ) : (
                    <span className="no-document">-</span>
                  )}
                </td>
                <td className="project-status">
                  {getStatusBadge(project.status)}
                </td>
                <td className="project-date">
                  {formatDate(project.createdAt)}
                </td>
                <td className="project-date">
                  {formatDate(project.updatedAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  )
}

export default Projects
