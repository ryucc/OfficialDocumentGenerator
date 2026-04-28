import { Link } from 'react-router-dom'
import './Welcome.css'

export default function Welcome() {
  return (
    <div className="landing">
      <header className="landing-topbar">
        <div className="landing-brand">公文產生器</div>
        <Link to="/login" className="landing-login">登入</Link>
      </header>
      <section className="landing-hero">
        <h1 className="landing-hero-title">注重資訊安全的公文產生器</h1>
      </section>
      <section className="landing-how">
        <h2 className="landing-how-title">服務流程</h2>
        <p className="landing-how-subtitle">寄一封信，自動回覆符合格式的公文草稿</p>
        <div className="landing-how-flow">
          <div className="landing-how-step">
            <div className="landing-how-step-icon">1</div>
            <h3>寄信至 AI 信箱</h3>
            <p>將案件資訊寄至 <code>ai@gongwengpt.click</code></p>
          </div>
          <div className="landing-how-arrow">→</div>
          <div className="landing-how-step">
            <div className="landing-how-step-icon">2</div>
            <h3>收到附件回信</h3>
            <p>系統自動產出 <code>.docx</code> 公文草稿並寄回</p>
          </div>
        </div>
      </section>
      <section className="landing-how landing-how--privacy">
        <h2 className="landing-how-title">資料隱私</h2>
        <p className="landing-how-subtitle">關於您的郵件與生成內容如何被處理</p>
        <div className="landing-privacy-grid">
          <div className="landing-privacy-card">
            <div className="landing-privacy-icon">🚫</div>
            <h3>AI 不訓練您的資料</h3>
            <p>後端 AI 為 Anthropic Claude（透過 AWS Bedrock 呼叫）。依商用條款，您的郵件內容不會被留存或用於訓練。</p>
            <a href="https://www.anthropic.com/legal/commercial-terms" target="_blank" rel="noopener noreferrer">Anthropic 商用條款</a>
          </div>
          <div className="landing-privacy-card">
            <div className="landing-privacy-icon">🔐</div>
            <h3>不留於模型供應商</h3>
            <p>AWS Bedrock 不儲存提示內容、不轉交模型供應商，亦不用於改善基礎模型。</p>
            <a href="https://docs.aws.amazon.com/bedrock/latest/userguide/data-protection.html" target="_blank" rel="noopener noreferrer">Bedrock 資料保護</a>
          </div>
          <div className="landing-privacy-card">
            <div className="landing-privacy-icon">🗄️</div>
            <h3>儲存於 AWS 東京</h3>
            <p>郵件與生成的公文存放於 AWS 亞太（東京）區，底層雲端通過 ISO 27001、SOC 2 等國際資安認證。</p>
            <a href="https://aws.amazon.com/compliance/programs/" target="_blank" rel="noopener noreferrer">查看 AWS 認證</a>
          </div>
          <div className="landing-privacy-card">
            <div className="landing-privacy-icon">🗑️</div>
            <h3>一鍵刪除歷史資料</h3>
            <p>使用者可隨時清除過往專案、郵件與生成的公文；郵件預設於 90 天後自動刪除，符合單位資安政策。</p>
          </div>
        </div>
      </section>
    </div>
  )
}
