import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import './DocumentDetail.css'

interface Document {
  id: string
  filename: string
  contentType: string
  sizeBytes: number
  status: string
  createdAt: string
  updatedAt: string
}

const API_BASE_URL = 'https://b1qdg79vji.execute-api.ap-northeast-1.amazonaws.com/Prod/api/v1'

function DocumentDetail() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [document, setDocument] = useState<Document | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pdfUrl, setPdfUrl] = useState<string | null>(null)
  const [isLoadingPdf, setIsLoadingPdf] = useState(false)

  useEffect(() => {
    if (id) {
      fetchDocument(id)
    }
  }, [id])

  useEffect(() => {
    if (document && isPdfDocument(document)) {
      loadPdfPreview()
    }
  }, [document])

  const isPdfDocument = (doc: Document) => {
    return doc.contentType === 'application/pdf' || doc.filename.toLowerCase().endsWith('.pdf')
  }

  const loadPdfPreview = async () => {
    if (!document) return

    try {
      setIsLoadingPdf(true)
      const response = await fetch(`${API_BASE_URL}/sample-documents/${document.id}/download-url`)

      if (!response.ok) {
        throw new Error('無法取得 PDF 連結')
      }

      const data = await response.json()
      setPdfUrl(data.downloadUrl)
    } catch (err) {
      console.error('PDF preview error:', err)
      setError('PDF 預覽載入失敗')
    } finally {
      setIsLoadingPdf(false)
    }
  }

  const fetchDocument = async (docId: string) => {
    try {
      setIsLoading(true)
      setError(null)
      const response = await fetch(`${API_BASE_URL}/sample-documents/${docId}`)

      if (!response.ok) {
        throw new Error(`API Error: ${response.status} ${response.statusText}`)
      }

      const data = await response.json()
      setDocument(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : '載入文件時發生錯誤')
      console.error('Error fetching document:', err)
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
      const second = String(date.getSeconds()).padStart(2, '0')
      const period = hour >= 12 ? '下午' : '上午'
      const hour12 = hour % 12 || 12
      const hourStr = String(hour12).padStart(2, '0')

      return `${year}/${month}/${day} ${period}${hourStr}:${minute}:${second}`
    } catch {
      return dateString
    }
  }

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  }

  const handleDownload = async () => {
    if (!document) return

    try {
      const response = await fetch(`${API_BASE_URL}/sample-documents/${document.id}/download-url`)
      if (!response.ok) {
        throw new Error('無法取得下載連結')
      }

      const data = await response.json()
      const link = window.document.createElement('a')
      link.href = data.downloadUrl
      link.download = document.filename
      window.document.body.appendChild(link)
      link.click()
      window.document.body.removeChild(link)
    } catch (err) {
      console.error('Download error:', err)
      alert('下載失敗，請稍後再試')
    }
  }


  if (isLoading) {
    return (
      <div className="document-detail-container">
        <div className="loading-state">
          <div className="spinner"></div>
          <p>載入文件中...</p>
        </div>
      </div>
    )
  }

  if (error || !document) {
    return (
      <div className="document-detail-container">
        <div className="error-state">
          <h2>載入失敗</h2>
          <p>{error || '找不到該文件'}</p>
          <button onClick={() => navigate('/documents')} className="back-btn">
            返回列表
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="document-detail-container">
      <div className="detail-header">
        <button onClick={() => navigate('/documents')} className="back-btn">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
            <path d="M19 12H5M5 12L12 19M5 12L12 5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
          返回列表
        </button>
      </div>

      <div className="detail-content">
        <div className="detail-title">
          <h1>{document.filename}</h1>
        </div>

        <div className="preview-section">
          {isPdfDocument(document) ? (
            isLoadingPdf ? (
              <div className="preview-loading">
                <div className="spinner"></div>
                <p>載入 PDF 預覽中...</p>
              </div>
            ) : pdfUrl ? (
              <iframe
                src={pdfUrl}
                className="pdf-preview"
                title={document.filename}
              />
            ) : (
              <div className="preview-not-supported">
                <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                  <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" strokeLinecap="round" strokeLinejoin="round"/>
                </svg>
                <h3>PDF 載入失敗</h3>
                <p>請下載文件以查看完整內容</p>
              </div>
            )
          ) : (
            <div className="preview-not-supported">
              <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
                <path d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" strokeLinecap="round" strokeLinejoin="round"/>
              </svg>
              <h3>預覽不支援</h3>
              <p>請下載文件以查看完整內容</p>
            </div>
          )}
        </div>

        <div className="detail-actions">
          <button className="btn-primary" onClick={handleDownload}>下載文件</button>
          <button className="btn-danger">刪除</button>
        </div>
      </div>
    </div>
  )
}

export default DocumentDetail
