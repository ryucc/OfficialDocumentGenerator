import { useState, useRef, useEffect } from 'react'
import { listInstructions, createInstruction, deleteInstruction, type DocumentInstruction } from './api'
import RichEditor from './RichEditor'
import './App.css'

type View = 'chat' | 'instructions'

interface Message {
  role: 'user' | 'assistant'
  content: string
}

function App() {
  const [view, setView] = useState<View>('chat')
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [theme, setTheme] = useState<'dark' | 'light'>(() => {
    return (localStorage.getItem('theme') as 'dark' | 'light') || 'dark'
  })

  // Instructions state
  const [instructions, setInstructions] = useState<DocumentInstruction[]>([])
  const [instructionsLoading, setInstructionsLoading] = useState(false)
  const [instructionsError, setInstructionsError] = useState('')
  const [newTitle, setNewTitle] = useState('')
  const [newContent, setNewContent] = useState('')
  const [creating, setCreating] = useState(false)

  const messagesEndRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    localStorage.setItem('theme', theme)
  }, [theme])

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto'
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 200) + 'px'
    }
  }, [input])

  useEffect(() => {
    if (view === 'instructions') {
      fetchInstructions()
    }
  }, [view])

  async function fetchInstructions() {
    setInstructionsLoading(true)
    setInstructionsError('')
    try {
      const items = await listInstructions()
      setInstructions(items)
    } catch {
      setInstructionsError('無法載入指令，請確認後端服務是否運行中。')
    } finally {
      setInstructionsLoading(false)
    }
  }

  async function handleCreateInstruction(e: React.FormEvent) {
    e.preventDefault()
    if (!newTitle.trim() || !newContent.trim()) return
    setCreating(true)
    try {
      const created = await createInstruction(newTitle.trim(), newContent.trim())
      setInstructions(prev => [created, ...prev])
      setNewTitle('')
      setNewContent('')
    } catch {
      setInstructionsError('建立失敗，請稍後再試。')
    } finally {
      setCreating(false)
    }
  }

  async function handleDeleteInstruction(id: string) {
    try {
      await deleteInstruction(id)
      setInstructions(prev => prev.filter(i => i.id !== id))
    } catch {
      setInstructionsError('刪除失敗，請稍後再試。')
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!input.trim() || isLoading) return

    const userMessage: Message = { role: 'user', content: input.trim() }
    setMessages(prev => [...prev, userMessage])
    setInput('')
    setIsLoading(true)

    // TODO: replace with actual API call
    setTimeout(() => {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '這是預設回覆。請串接 API 以從電子郵件產生公文。'
      }])
      setIsLoading(false)
    }, 1000)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit(e)
    }
  }

  return (
    <div className="app">
      {sidebarOpen && <div className="sidebar-overlay" onClick={() => setSidebarOpen(false)} />}
      <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
        <button className="new-chat-btn" onClick={() => { setView('chat'); setMessages([]); setSidebarOpen(false) }}>+ 新對話</button>
        <nav className="sidebar-nav">
          <button className={`nav-item ${view === 'chat' ? 'active' : ''}`} onClick={() => { setView('chat'); setSidebarOpen(false) }}>
            對話
          </button>
          <button className={`nav-item ${view === 'instructions' ? 'active' : ''}`} onClick={() => { setView('instructions'); setSidebarOpen(false) }}>
            公文規則
          </button>
        </nav>
        <div className="sidebar-history">
          <div className="history-item">先前的對話</div>
        </div>
        <button className="theme-toggle" onClick={() => setTheme(t => t === 'dark' ? 'light' : 'dark')}>
          {theme === 'dark' ? '淺色模式' : '深色模式'}
        </button>
      </aside>

      <main className="main">
        <button className="menu-btn" onClick={() => setSidebarOpen(true)} aria-label="開啟選單">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/>
          </svg>
        </button>

        {view === 'chat' ? (
          <>
            <div className="messages">
              {messages.length === 0 ? (
                <div className="empty-state">
                  <h1>公文產生器</h1>
                  <p>貼上電子郵件，我將為您產生正式公文。</p>
                  <div className="suggestions">
                    <button className="suggestion" onClick={() => setInput('請根據以下郵件產生正式回覆：\n\n')}>
                      產生正式回覆
                    </button>
                    <button className="suggestion" onClick={() => setInput('請根據以下內容建立公文：\n\n')}>
                      建立公文
                    </button>
                    <button className="suggestion" onClick={() => setInput('請為以下內容撰寫摘要：\n\n')}>
                      撰寫文件摘要
                    </button>
                  </div>
                </div>
              ) : (
                messages.map((msg, i) => (
                  <div key={i} className={`message ${msg.role}`}>
                    <div className="message-icon">
                      {msg.role === 'user' ? 'U' : 'AI'}
                    </div>
                    <div className="message-content">
                      {msg.content}
                    </div>
                  </div>
                ))
              )}
              {isLoading && (
                <div className="message assistant">
                  <div className="message-icon">AI</div>
                  <div className="message-content">
                    <div className="typing-indicator">
                      <span></span><span></span><span></span>
                    </div>
                  </div>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            <form className="input-area" onSubmit={handleSubmit}>
              <div className="input-container">
                <textarea
                  ref={textareaRef}
                  value={input}
                  onChange={e => setInput(e.target.value)}
                  onKeyDown={handleKeyDown}
                  placeholder="貼上電子郵件或描述您需要的公文..."
                  rows={1}
                />
                <button type="submit" className="send-btn" disabled={!input.trim() || isLoading} aria-label="送出">
                  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M7 11L12 6L17 11M12 18V7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
                  </svg>
                </button>
              </div>
              <p className="disclaimer">AI 產生的公文請在發送前詳細檢閱。</p>
            </form>
          </>
        ) : (
          <div className="instructions-view">
            <h2>公文規則</h2>

            <form className="create-form" onSubmit={handleCreateInstruction}>
              <input
                type="text"
                className="create-input"
                placeholder="規則標題"
                value={newTitle}
                onChange={e => setNewTitle(e.target.value)}
              />
              <RichEditor
                content={newContent}
                onChange={setNewContent}
                placeholder="規則內容"
              />
              <button type="submit" className="create-btn" disabled={creating || !newTitle.trim() || !newContent.trim()}>
                {creating ? '建立中...' : '新增規則'}
              </button>
            </form>

            {instructionsError && <p className="error-msg">{instructionsError}</p>}

            {instructionsLoading ? (
              <div className="instructions-loading">載入中...</div>
            ) : instructions.length === 0 ? (
              <div className="instructions-empty">尚無公文規則。</div>
            ) : (
              <div className="instructions-list">
                {instructions.map(inst => (
                  <div key={inst.id} className="instruction-card">
                    <div className="instruction-header">
                      <h3>{inst.title}</h3>
                      <button className="delete-btn" onClick={() => handleDeleteInstruction(inst.id)} aria-label="刪除">
                        &times;
                      </button>
                    </div>
                    <div className="instruction-content" dangerouslySetInnerHTML={{ __html: inst.content }} />
                    <p className="instruction-date">
                      {new Date(inst.createdAt).toLocaleString('zh-TW')}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  )
}

export default App
