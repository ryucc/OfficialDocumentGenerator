import { useState, useEffect, useRef } from 'react'
import { API_BASE_URL } from './config'
import './Documents.css'

interface Document {
  id: string
  filename: string
  contentType: string
  sizeBytes: number | null
  status: string
  createdAt: string
  updatedAt: string
}

function Documents() {
  const [documents, setDocuments] = useState<Document[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isUploading, setIsUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    fetchDocuments()
  }, [])

  const fetchDocuments = async () => {
    try {
      setIsLoading(true)
      setError(null)
      const response = await fetch(`${API_BASE_URL}/sample-documents`)

      if (!response.ok) {
        throw new Error(`API Error: ${response.status} ${response.statusText}`)
      }

      const data = await response.json()
      setDocuments(data.items || [])
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入文件時發生錯誤')
      console.error('Error fetching documents:', err)
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

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const isVisibleDocument = (
    doc: Document,
  ): doc is Document & { status: 'AVAILABLE'; sizeBytes: number } => {
    return doc.status === 'AVAILABLE'
      && typeof doc.sizeBytes === 'number'
      && Number.isFinite(doc.sizeBytes)
      && doc.sizeBytes > 0
  }

  const visibleDocuments = documents.filter(isVisibleDocument)

  const handleDownload = async (doc: Document) => {
    try {
      const response = await fetch(`${API_BASE_URL}/sample-documents/${doc.id}/download-url`)
      if (!response.ok) {
        throw new Error('無法取得下載連結')
      }

      const data = await response.json()
      const link = window.document.createElement('a')
      link.href = data.downloadUrl
      link.download = doc.filename
      window.document.body.appendChild(link)
      link.click()
      window.document.body.removeChild(link)
    } catch (err) {
      console.error('Download error:', err)
      alert('下載失敗，請稍後再試')
    }
  }

  const handleDelete = async (doc: Document) => {
    if (!confirm(`確定要刪除「${doc.filename}」嗎？此操作無法復原。`)) {
      return
    }

    try {
      const response = await fetch(`${API_BASE_URL}/sample-documents/${doc.id}`, {
        method: 'DELETE',
      })

      if (!response.ok) {
        throw new Error('刪除失敗')
      }

      // Refresh document list
      await fetchDocuments()
      alert('刪除成功')
    } catch (err) {
      console.error('Delete error:', err)
      alert('刪除失敗，請稍後再試')
    }
  }

  const handleUploadClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    // Validate file type
    const isDocx = file.name.endsWith('.docx')
    const isPdf = file.name.endsWith('.pdf')

    if (!isDocx && !isPdf) {
      alert('請選擇 .docx 或 .pdf 格式的文件')
      return
    }

    try {
      setIsUploading(true)

      // Determine content type
      let contentType = file.type
      if (!contentType) {
        contentType = isPdf
          ? 'application/pdf'
          : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      }

      // Step 1: Get presigned upload URL
      const initResponse = await fetch(`${API_BASE_URL}/sample-documents`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          filename: file.name,
          contentType: contentType,
          sizeBytes: file.size,
        }),
      })

      if (!initResponse.ok) {
        throw new Error('無法初始化上傳')
      }

      const { document: doc, upload } = await initResponse.json()

      // Step 2: Upload file to S3
      const uploadResponse = await fetch(upload.uploadUrl, {
        method: upload.uploadMethod,
        headers: upload.uploadHeaders,
        body: file,
      })

      if (!uploadResponse.ok) {
        throw new Error('檔案上傳失敗')
      }

      // Step 3: Mark as complete
      const completeResponse = await fetch(`${API_BASE_URL}/sample-documents/${doc.id}/complete`, {
        method: 'POST',
      })

      if (!completeResponse.ok) {
        throw new Error('檔案已上傳，但無法完成整理，請重新整理後再試')
      }

      // Refresh document list
      await fetchDocuments()

      alert('上傳成功！')
    } catch (err) {
      console.error('Upload error:', err)
      alert(err instanceof Error ? err.message : '上傳失敗，請稍後再試')
    } finally {
      setIsUploading(false)
      // Reset file input
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    }
  }

  if (isLoading) {
    return (
      <div className="documents-container">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>載入文件中...</p>
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="documents-container">
        <div className="error-state">
          <h2>載入失敗</h2>
          <p>{error}</p>
          <button onClick={fetchDocuments} className="retry-btn">重試</button>
        </div>
      </div>
    )
  }

  return (
    <div className="documents-container">
      <div className="documents-header">
        <h1>範例管理</h1>
        <button
          onClick={handleUploadClick}
          className="upload-btn"
          disabled={isUploading}
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          {isUploading ? '上傳中...' : '上傳文件'}
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept=".docx,.pdf"
          onChange={handleFileChange}
          style={{ display: 'none' }}
        />
      </div>

      {visibleDocuments.length === 0 ? (
        <div className="empty-state">
          <p>目前沒有文件</p>
        </div>
      ) : (
        <table className="documents-table">
          <thead>
            <tr>
              <th>文件名稱</th>
              <th>建立時間</th>
              <th>更新時間</th>
              <th>大小</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {visibleDocuments.map((doc) => (
              <tr key={doc.id}>
                <td className="doc-name">
                  <div className="doc-name-content">
                    <span className="filename">{doc.filename}</span>
                  </div>
                </td>
                <td className="doc-date">
                  {formatDate(doc.createdAt)}
                </td>
                <td className="doc-date">
                  {formatDate(doc.updatedAt)}
                </td>
                <td className="doc-size">
                  {formatFileSize(doc.sizeBytes)}
                </td>
                <td className="doc-actions">
                  <button
                    className="action-btn download-btn"
                    onClick={() => handleDownload(doc)}
                  >
                    下載
                  </button>
                  <button
                    className="action-btn delete-btn"
                    onClick={() => handleDelete(doc)}
                  >
                    刪除
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

export default Documents
