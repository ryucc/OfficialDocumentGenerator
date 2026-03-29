const API_BASE = import.meta.env.VITE_API_URL || '/api/v1'

export interface DocumentInstruction {
  id: string
  title: string
  content: string
  createdAt: string
  updatedAt: string
}

export async function listInstructions(): Promise<DocumentInstruction[]> {
  const res = await fetch(`${API_BASE}/document-instructions`)
  if (!res.ok) throw new Error('Failed to fetch instructions')
  const data = await res.json()
  return data.items
}

export async function getInstruction(id: string): Promise<DocumentInstruction> {
  const res = await fetch(`${API_BASE}/document-instructions/${id}`)
  if (!res.ok) throw new Error('Instruction not found')
  return res.json()
}

export async function createInstruction(title: string, content: string): Promise<DocumentInstruction> {
  const res = await fetch(`${API_BASE}/document-instructions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ title, content }),
  })
  if (!res.ok) throw new Error('Failed to create instruction')
  return res.json()
}

export async function updateInstruction(id: string, title?: string, content?: string): Promise<DocumentInstruction> {
  const body: Record<string, string> = {}
  if (title !== undefined) body.title = title
  if (content !== undefined) body.content = content
  const res = await fetch(`${API_BASE}/document-instructions/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error('Failed to update instruction')
  return res.json()
}

export async function deleteInstruction(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/document-instructions/${id}`, {
    method: 'DELETE',
  })
  if (!res.ok) throw new Error('Failed to delete instruction')
}
