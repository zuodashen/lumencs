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
    type: 'text' | 'choice' | 'textarea' | string
  label: string
  required: boolean
  options: string[]
  value?: string
}

export type WorkflowCard = {
  cardId: string
  confirmToken?: string
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
  messageId?: number
  reviewPending?: boolean
  reviewId?: number
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

function unwrap<T>(json: { state?: number; code?: number; data?: T; msg?: string; message?: string; error?: string }): T {
  if (json.state === 200 || json.code === 0) {
    return json.data as T
  }
  const text = json.msg || json.message || json.error
  if (json.state === 401 || json.state === 403 || text === 'Forbidden' || text === 'Unauthorized') {
    throw new Error('登录已过期，请重新登录')
  }
  throw new Error(text || '请求失败')
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

function chatHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Accept: 'text/event-stream',
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

export async function streamChat(
  payload: { sessionId?: string; userLabel?: string; message: string; articleSlug?: string },
  handler: SseHandler,
) {
  const res = await fetch(`${API_BASE}/api/chat`, {
    method: 'POST',
    headers: chatHeaders(),
    body: JSON.stringify(payload),
  })
  if (!res.ok) {
    throw new Error('聊天请求失败')
  }
  try {
    await parseSseStream(res, handler)
  } catch (e) {
    const msg = e instanceof Error ? e.message : ''
    if (/network error|failed to fetch/i.test(msg)) {
      throw new Error('对话连接被中断。请看页面是否已提示密钥问题；否则检查后端日志里的 dmx_api_error。')
    }
    throw e
  }
}

export async function streamCard(
  payload: {
    sessionId?: string
    userLabel?: string
    cardId: string
    confirmToken?: string
    values: Record<string, unknown>
  },
  handler: SseHandler,
) {
  const res = await fetch(`${API_BASE}/api/chat/card`, {
    method: 'POST',
    headers: chatHeaders(),
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
  if ((res.status === 401 || res.status === 403) && !retried) {
    const ok = await refreshAccessToken()
    if (ok) {
      return adminFetch(path, init, true)
    }
    clearTokens()
    if (!window.location.pathname.includes('/console/login')) {
      window.location.href = '/console/login'
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
  reindexKnowledge: () => adminFetch('/api/admin/knowledge/reindex', { method: 'POST' }),
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
  history: (sessionId: string) =>
    fetch(`${API_BASE}/api/chat/${encodeURIComponent(sessionId)}/messages`).then(async (res) => {
      const json = await res.json()
      return Array.isArray(json) ? json : unwrap<any[]>(json)
    }),
  scope: (slug: string) =>
    fetch(`${API_BASE}/api/hub/scope?slug=${encodeURIComponent(slug)}`).then(async (res) => {
      const json = await res.json()
      return unwrap<{ slug: string; ready: boolean; title: string }>(json)
    }),
  feedback: (body: { sessionId: string; messageId: number; score: 'UP' | 'DOWN'; cited?: boolean; comment?: string }) =>
    fetch(`${API_BASE}/api/chat/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    }).then(async (res) => unwrap(await res.json())),
  hubOverview: () => adminFetch('/api/admin/hub/overview'),
  inbox: () => adminFetch('/api/admin/hub/inbox'),
  markInboxRead: (id: number) => adminFetch(`/api/admin/hub/inbox/${id}/read`, { method: 'POST' }),
  gaps: () => adminFetch('/api/admin/hub/gaps'),
  faqDraft: (body: { sessionId: string; messageId?: number }) =>
    adminFetch('/api/admin/hub/faq-draft', { method: 'POST', body: JSON.stringify(body) }),
  channels: () => adminFetch('/api/admin/hub/channels'),
  saveWebhook: (body: { name?: string; url: string; enabled?: boolean }) =>
    adminFetch('/api/admin/hub/channels/webhook', { method: 'POST', body: JSON.stringify(body) }),
}

export function logoutTokens() {
  clearTokens()
  localStorage.removeItem('lumencs_user')
}
