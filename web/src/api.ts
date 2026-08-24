export const API_BASE = '/lumencs-api'

export type Page<T> = {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}

export type PageQuery = { pageNum?: number; pageSize?: number }

export type Citation = {
  id: string
  documentId?: number | null
  source: string
  score: number
  snippet: string
}

export type AgentStep = {
  agent: string
  status: string
  ts: number
  intent?: string
  confidence?: number
  clarify?: boolean
  hitCount?: number
  rankedCount?: number
  citations?: Citation[]
  ticketNo?: string
  passed?: boolean
  stage?: string
  violations?: string[]
  workflow?: string
  missing?: string[]
  tool?: string
  reviewId?: number
  hitl?: boolean
}

export type CardField = {
  name: string
  type: 'text' | 'choice' | string
  label: string
  required: boolean
  options: string[]
  value?: string
}

export type WorkflowCard = {
  cardId: string
  workflow: string
  title: string
  hint: string
  fields: CardField[]
}

export type ChatResult = {
  sessionId: string
  content: string
  intent: string
  compliancePassed: boolean
  citations: Citation[]
  ticketNo?: string
  waitingCard?: boolean
}

export type LoginResult = {
  token: string
  refreshToken: string
  username: string
}

type SseHandler = {
  onSession?: (sessionId: string) => void
  onStep?: (step: AgentStep) => void
  onCard?: (card: WorkflowCard) => void
  onToken?: (delta: string) => void
  onMessage?: (msg: ChatResult) => void
  onError?: (message: string) => void
}

const TOKEN_KEY = 'lumencs_token'
const REFRESH_KEY = 'lumencs_refresh'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

function setTokens(token: string, refreshToken?: string) {
  localStorage.setItem(TOKEN_KEY, token)
  if (refreshToken) {
    localStorage.setItem(REFRESH_KEY, refreshToken)
  }
}

function clearTokens() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
}

function unwrap<T>(json: { state?: number; code?: number; data?: T; msg?: string; message?: string }): T {
  if (json.state === 200 || json.code === 0) {
    return json.data as T
  }
  throw new Error(json.msg || json.message || '请求失败')
}

async function parseSseStream(res: Response, handler: SseHandler) {
  if (!res.body) {
    throw new Error('浏览器不支持流式读取')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      const eventMatch = block.match(/^event:(.+)$/m)
      const dataMatch = block.match(/^data:(.+)$/m)
      if (!eventMatch || !dataMatch) continue
      const event = eventMatch[1].trim()
      const data = JSON.parse(dataMatch[1])
      if (event === 'session') handler.onSession?.(data.sessionId)
      if (event === 'step') handler.onStep?.(data)
      if (event === 'card') handler.onCard?.(data)
      if (event === 'token') handler.onToken?.(data.delta || '')
      if (event === 'message') handler.onMessage?.(data)
      if (event === 'error') handler.onError?.(data.message || '处理失败')
    }
  }
}

export async function streamChat(payload: { sessionId?: string; userLabel?: string; message: string }, handler: SseHandler) {
  const res = await fetch(`${API_BASE}/api/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    throw new Error('聊天请求失败')
  }
  await parseSseStream(res, handler)
}

export async function streamCard(
  payload: { sessionId?: string; userLabel?: string; cardId: string; values: Record<string, unknown> },
  handler: SseHandler,
) {
  const res = await fetch(`${API_BASE}/api/chat/card`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    throw new Error('卡片提交失败')
  }
  await parseSseStream(res, handler)
}

/** 用 refresh token 换取新 token 对；失败清空本地凭据。 */
async function refreshAccessToken(): Promise<boolean> {
  const refreshToken = localStorage.getItem(REFRESH_KEY)
  if (!refreshToken) return false
  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    })
    if (!res.ok) {
      clearTokens()
      return false
    }
    const json = await res.json()
    const data = unwrap<LoginResult>(json)
    setTokens(data.token, data.refreshToken)
    return true
  } catch {
    return false
  }
}

async function adminFetch(path: string, init: RequestInit = {}, retried = false): Promise<any> {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${getToken()}`)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  const res = await fetch(`${API_BASE}${path}`, { ...init, headers })
  if (res.status === 401 && !retried) {
    const ok = await refreshAccessToken()
    if (ok) {
      return adminFetch(path, init, true)
    }
    throw new Error('登录已过期，请重新登录')
  }
  const json = await res.json()
  return unwrap(json)
}

export const api = {
  login: (username: string, password: string) =>
    fetch(`${API_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    }).then(async (res) => {
      const json = await res.json()
      const data = unwrap<LoginResult>(json)
      setTokens(data.token, data.refreshToken)
      return data
    }),
  health: () => fetch(`${API_BASE}/api/health`).then((r) => r.json()),
  documents: (pageNum = 1, pageSize = 10) =>
    adminFetch(`/api/admin/knowledge?pageNum=${pageNum}&pageSize=${pageSize}`),
  createDocument: (body: { title: string; source?: string; content: string }) =>
    adminFetch('/api/admin/knowledge', { method: 'POST', body: JSON.stringify(body) }),
  tickets: (query: PageQuery & { status?: string } = {}) => {
    const params = new URLSearchParams()
    params.set('pageNum', String(query.pageNum ?? 1))
    params.set('pageSize', String(query.pageSize ?? 10))
    if (query.status) params.set('status', query.status)
    return adminFetch(`/api/admin/tickets?${params.toString()}`)
  },
  updateTicket: (id: number, status: string) =>
    adminFetch(`/api/admin/tickets/${id}`, { method: 'PATCH', body: JSON.stringify({ status }) }),
  sessions: () => adminFetch('/api/admin/sessions'),
  traces: (sessionId: string) => adminFetch(`/api/admin/traces?sessionId=${encodeURIComponent(sessionId)}`),
  memory: (sessionId: string) => adminFetch(`/api/admin/memory?sessionId=${encodeURIComponent(sessionId)}`),
  tools: () => adminFetch('/api/admin/tools'),
  syncBlog: () => adminFetch('/api/admin/blog/sync', { method: 'POST' }),
  /** 引用可点：拉取引用 chunk 的完整原文（公开只读） */
  chunk: (id: string) =>
    fetch(`${API_BASE}/api/knowledge/chunks/${encodeURIComponent(id)}`).then(async (res) => {
      const json = await res.json()
      return unwrap<{ id: string; documentId?: number; source: string; title: string; content: string }>(json)
    }),
  reviews: (query: PageQuery & { status?: string } = {}) => {
    const params = new URLSearchParams()
    params.set('pageNum', String(query.pageNum ?? 1))
    params.set('pageSize', String(query.pageSize ?? 10))
    if (query.status) params.set('status', query.status)
    return adminFetch(`/api/admin/reviews?${params.toString()}`)
  },
  decideReview: (id: number, action: 'APPROVE' | 'REJECT', note?: string) =>
    adminFetch(`/api/admin/reviews/${id}/decide`, {
      method: 'POST',
      body: JSON.stringify({ action, note: note || '' }),
    }),
}

export function logoutTokens() {
  clearTokens()
  localStorage.removeItem('lumencs_user')
}
