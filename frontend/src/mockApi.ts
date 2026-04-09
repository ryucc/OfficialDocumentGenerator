// Mock API interceptor - activated when VITE_MOCK=true
// Intercepts fetch calls to API_BASE_URL and returns mock data

import { API_BASE_URL } from './config'

const mockProjects = [
  {
    projectId: 'proj-001',
    name: '關於申請補助款項之函',
    status: 'finished',
    emailS3Key: 'emails/test/budget-request-001',
    emailS3Bucket: 'mock-bucket',
    generatedDocumentS3Key: 'documents/output/budget-request-001.docx',
    createdAt: '2026-04-05T10:30:00Z',
    updatedAt: '2026-04-05T10:35:00Z',
  },
  {
    projectId: 'proj-002',
    name: '有關場地借用事宜',
    status: 'in_progress',
    emailS3Key: 'emails/test/venue-booking-002',
    emailS3Bucket: 'mock-bucket',
    createdAt: '2026-04-06T14:00:00Z',
    updatedAt: '2026-04-06T14:00:00Z',
  },
  {
    projectId: 'proj-003',
    name: '人事異動通知',
    status: 'finished',
    emailS3Key: 'emails/test/personnel-change-003',
    emailS3Bucket: 'mock-bucket',
    generatedDocumentS3Key: 'documents/output/personnel-change-003.docx',
    createdAt: '2026-04-04T09:15:00Z',
    updatedAt: '2026-04-04T09:20:00Z',
  },
  {
    projectId: 'proj-004',
    name: '會議紀錄轉發',
    status: 'finished',
    emailS3Key: 'emails/test/meeting-notes-004',
    emailS3Bucket: 'mock-bucket',
    generatedDocumentS3Key: 'documents/output/meeting-notes-004.docx',
    createdAt: '2026-04-03T16:45:00Z',
    updatedAt: '2026-04-03T16:50:00Z',
  },
]

const mockDocuments = [
  {
    id: 'doc-001',
    filename: '公文範例_函.docx',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 45200,
    status: 'active',
    createdAt: '2026-03-20T08:00:00Z',
    updatedAt: '2026-03-20T08:00:00Z',
  },
  {
    id: 'doc-002',
    filename: '公文範例_令.pdf',
    contentType: 'application/pdf',
    sizeBytes: 128900,
    status: 'active',
    createdAt: '2026-03-18T11:30:00Z',
    updatedAt: '2026-03-18T11:30:00Z',
  },
  {
    id: 'doc-003',
    filename: '簽呈範本.docx',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 33100,
    status: 'active',
    createdAt: '2026-03-15T14:20:00Z',
    updatedAt: '2026-03-15T14:20:00Z',
  },
]

const mockSkills = [
  {
    skillId: 'official-document-v1',
    displayName: '邀訪案撰寫',
    name: '公文申請表',
    description: '政府公文申請表產生器，包含申請表、受邀人名單附件、行程表附件',
    owner: 'Hsieh Chang-Ming',
  },
]

const mockInstalledSkills = new Set<string>(['official-document-v1'])

type RouteHandler = (url: URL, options?: RequestInit) => Response

const routes: Array<{ match: (url: URL) => boolean; handler: RouteHandler }> = [
  {
    match: (url) => url.pathname.endsWith('/projects'),
    handler: () => jsonResponse({ items: mockProjects }),
  },
  {
    match: (url) => url.pathname.endsWith('/sample-documents'),
    handler: (_url, options) => {
      if (options?.method === 'POST') {
        const body = JSON.parse(options.body as string)
        const newDoc = {
          id: 'doc-new-' + Date.now(),
          filename: body.filename,
          contentType: body.contentType,
          sizeBytes: 0,
          status: 'pending',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }
        return jsonResponse({
          document: newDoc,
          upload: {
            uploadUrl: 'https://mock-s3.example.com/upload',
            uploadMethod: 'PUT',
            uploadHeaders: { 'Content-Type': body.contentType },
          },
        })
      }
      return jsonResponse({ items: mockDocuments })
    },
  },
  {
    match: (url) => url.pathname.endsWith('/skills') && !url.pathname.includes('/users/'),
    handler: () => jsonResponse({ items: mockSkills, count: mockSkills.length }),
  },
  {
    match: (url) => /\/skills\/[^/]+$/.test(url.pathname) && !url.pathname.includes('/users/'),
    handler: (url) => {
      const skillId = url.pathname.split('/').pop()
      const skill = mockSkills.find((s) => s.skillId === skillId)
      if (!skill) return jsonResponse({ error: 'Not found' }, 404)
      return jsonResponse({
        ...skill,
        language: 'java',
        version: '1.0.0',
        schemaJson: '{"標題":"string","申請表":{"申請單位":"string","緣由":"string"}}',
        instructionsMd: '# 填寫說明\n\n請從郵件中擷取以下資訊：\n\n- **標題**：公文標題\n- **申請單位**：申請單位名稱\n- **緣由**：申請原因',
        instructionsHtml: '<h1>填寫說明</h1><p>請從郵件中擷取以下資訊：</p><ul><li><strong>標題</strong>：公文標題</li><li><strong>申請單位</strong>：申請單位名稱</li><li><strong>緣由</strong>：申請原因</li></ul>',
        createdAt: '2026-04-08T00:00:00Z',
        updatedAt: '2026-04-08T00:00:00Z',
      })
    },
  },
  {
    match: (url) => /\/users\/[^/]+\/skills\/[^/]+$/.test(url.pathname),
    handler: (_url, options) => {
      const parts = _url.pathname.split('/')
      const skillId = parts[parts.length - 1]
      if (options?.method === 'PUT') {
        mockInstalledSkills.add(skillId)
        return jsonResponse({ message: `Skill ${skillId} installed` })
      }
      if (options?.method === 'DELETE') {
        mockInstalledSkills.delete(skillId)
        return jsonResponse({ message: `Skill ${skillId} uninstalled` })
      }
      return jsonResponse({ message: 'Not found' }, 404)
    },
  },
  {
    match: (url) => /\/users\/[^/]+\/skills$/.test(url.pathname),
    handler: () => jsonResponse({ installedSkills: Array.from(mockInstalledSkills) }),
  },
  {
    match: (url) => /\/sample-documents\/[^/]+\/download-url$/.test(url.pathname),
    handler: () => jsonResponse({ downloadUrl: '#mock-download' }),
  },
  {
    match: (url) => /\/sample-documents\/[^/]+\/complete$/.test(url.pathname),
    handler: () => jsonResponse({ success: true }),
  },
  {
    match: (url) => /\/sample-documents\/[^/]+$/.test(url.pathname),
    handler: (_url, options) => {
      if (options?.method === 'DELETE') {
        return jsonResponse({ success: true })
      }
      const id = _url.pathname.split('/').pop()
      const doc = mockDocuments.find((d) => d.id === id) || mockDocuments[0]
      return jsonResponse(doc)
    },
  },
]

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function setupMockApi() {
  const originalFetch = window.fetch.bind(window)

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url)

    if (url.href.startsWith(API_BASE_URL)) {
      // Simulate network delay
      await new Promise((r) => setTimeout(r, 300))

      for (const route of routes) {
        if (route.match(url)) {
          console.log(`[Mock API] ${init?.method || 'GET'} ${url.pathname}`)
          return route.handler(url, init)
        }
      }

      console.warn(`[Mock API] No handler for ${url.pathname}`)
      return jsonResponse({ message: 'Not found' }, 404)
    }

    // Mock S3 upload
    if (url.href.includes('mock-s3.example.com')) {
      console.log('[Mock API] S3 upload (mocked)')
      return new Response(null, { status: 200 })
    }

    return originalFetch(input, init)
  }

  console.log('[Mock API] Mock API interceptor active')
}
