import { useState, useEffect } from 'react'
import { API_BASE_URL } from './config'
import './Projects.css'

interface Project {
  projectId: string
  status: string
  emailS3Key: string
  emailS3Bucket: string
  emailSubject?: string
  generatedDocumentKey?: string
  documentJsonKey?: string
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
      'pending_generation': { text: '等待生成', className: 'status-pending' },
      'processing': { text: '生成中', className: 'status-in-progress' },
      'completed': { text: '已完成', className: 'status-finished' },
      'failed': { text: '失敗', className: 'status-failed' },
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

  const handleDownload = async (projectId: string) => {
    try {
      const response = await fetch(`${API_BASE_URL}/projects/${projectId}/download-url`)

      if (!response.ok) {
        throw new Error('無法取得下載連結')
      }

      const data = await response.json()

      // Open download URL in new tab
      window.open(data.downloadUrl, '_blank')
    } catch (err) {
      alert(err instanceof Error ? err.message : '下載失敗')
      console.error('Error downloading document:', err)
    }
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
            <option value="pending_generation">等待生成</option>
            <option value="processing">生成中</option>
            <option value="completed">已完成</option>
            <option value="failed">失敗</option>
          </select>
          <button onClick={fetchProjects} className="refresh-btn">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
              <path d="M1 4v6h6M23 20v-6h-6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M20.49 9A9 9 0 005.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 013.51 15" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            重新整理
          </button>
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
              <th>專案 ID</th>
              <th>狀態</th>
              <th>郵件主旨</th>
              <th>建立時間</th>
              <th>更新時間</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {projects.map((project) => (
              <tr key={project.projectId}>
                <td className="project-id">
                  <code>{project.projectId.substring(0, 8)}...</code>
                </td>
                <td className="project-status">
                  {getStatusBadge(project.status)}
                </td>
                <td className="project-email">
                  <div className="email-info">
                    <span className="email-subject" title={project.emailSubject || project.emailS3Key}>
                      {project.emailSubject || getEmailFileName(project.emailS3Key)}
                    </span>
                  </div>
                </td>
                <td className="project-date">
                  {formatDate(project.createdAt)}
                </td>
                <td className="project-date">
                  {formatDate(project.updatedAt)}
                </td>
                <td className="project-actions">
                  {project.generatedDocumentKey && project.status === 'completed' ? (
                    <button
                      onClick={() => handleDownload(project.projectId)}
                      className="download-btn"
                      title="下載生成的文件"
                    >
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none">
                        <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <polyline points="7 10 12 15 17 10" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                        <line x1="12" y1="15" x2="12" y2="3" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                      </svg>
                      下載
                    </button>
                  ) : (
                    <span className="no-action">-</span>
                  )}
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
