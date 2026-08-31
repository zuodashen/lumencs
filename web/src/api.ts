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

export type ChatEmbed = {
  kind: 'blog_list' | 'bookmark_list' | 'stock' | string
  [key: string]: any
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
  embed?: ChatEmbed
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
  onEmbed?: (embed: ChatEmbed) => void
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

async function parseSseStream(res: Response, handler: SseHandler): Promise<{ gotMessage: boolean; gotDone: boolean }> {
  if (!res.body) {
    throw new Error('浏览器不支持流式读取')
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let gotMessage = false
  let gotDone = false

  const consume = (block: string) => {
    const eventMatch = block.match(/^event:(.+)$/m)
    const dataMatch = block.match(/^data:(.+)$/m)
    if (!eventMatch || !dataMatch) return
    const event = eventMatch[1].trim()
    let data: any = {}
    try {
      data = JSON.parse(dataMatch[1])
    } catch {
      return
    }
    if (event === 'session') handler.onSession?.(data.sessionId)
    if (event === 'step') handler.onStep?.(data)
    if (event === 'card') handler.onCard?.(data)
    if (event === 'embed') handler.onEmbed?.(data)
    if (event === 'token') handler.onToken?.(data.delta || '')
    if (event === 'message') {
      gotMessage = true
      handler.onMessage?.(data)
    }
    if (event === 'error') handler.onError?.(data.message || '处理失败')
    if (event === 'done') gotDone = true
  }

  try {
    while (true) {
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() || ''
      for (const block of blocks) consume(block)
    }
    buffer += decoder.decode()
    if (buffer.trim()) consume(buffer)
  } catch (e) {
    if (gotMessage || gotDone) {
      return { gotMessage, gotDone }
    }
    throw e
  }
  return { gotMessage, gotDone }
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
  const res = await authedStreamFetch(`${API_BASE}/api/chat`, payload)
  try {
    const result = await parseSseStream(res, handler)
    if (!result.gotMessage && !result.gotDone) {
      throw new Error('没有收到完整回复，请再试一次')
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : ''
    if (/network error|failed to fetch|abort/i.test(msg)) {
      throw new Error('连接中断了，请再发一次。')
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
  const res = await authedStreamFetch(`${API_BASE}/api/chat/card`, payload)
  try {
    const result = await parseSseStream(res, handler)
    if (!result.gotMessage && !result.gotDone) {
      throw new Error('卡片提交后没有收到完整回复')
    }
  } catch (e) {
    const msg = e instanceof Error ? e.message : ''
    if (/network error|failed to fetch|abort/i.test(msg)) {
      throw new Error('连接中断了，请再发一次。')
    }
    throw e
  }
}

function loginRedirect() {
  const path = window.location.pathname
  if (path.includes('/console/login')) return
  const next = `${path}${window.location.search}` || '/'
  window.location.href = `/console/login?next=${encodeURIComponent(next)}`
}

async function authedStreamFetch(url: string, payload: unknown, retried = false): Promise<Response> {
  const res = await fetch(url, {
    method: 'POST',
    headers: chatHeaders(),
    body: JSON.stringify(payload),
  })
  if (res.status === 401 || res.status === 403) {
    if (!retried && (await refreshAccessToken())) {
      return authedStreamFetch(url, payload, true)
    }
    clearTokens()
    loginRedirect()
    throw new Error('请先登录')
  }
  if (!res.ok) {
    throw new Error(url.includes('/card') ? '卡片提交失败' : '聊天请求失败')
  }
  return res
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
    loginRedirect()
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
  createDocument: (body: {
    title: string
    source?: string
    content: string
    collapseWhitespace?: boolean
    paragraphSplit?: boolean
    parentMax?: number
    childMax?: number
  }) => adminFetch('/api/admin/knowledge', { method: 'POST', body: JSON.stringify(body) }),
  documentDetail: (id: number) => adminFetch(`/api/admin/knowledge/${id}`),
  deleteDocument: (id: number) => adminFetch(`/api/admin/knowledge/${id}`, { method: 'DELETE' }),
  previewChunks: (body: {
    content: string
    collapseWhitespace?: boolean
    paragraphSplit?: boolean
    parentMax?: number
    childMax?: number
  }) => adminFetch('/api/admin/knowledge/preview', { method: 'POST', body: JSON.stringify(body) }),
  recallTest: (body: { query: string; documentId?: number }) =>
    adminFetch('/api/admin/knowledge/recall', { method: 'POST', body: JSON.stringify(body) }),
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
  blogSettings: () => adminFetch('/api/admin/blog/settings'),
  updateBlogSettings: (body: { syncEnabled: boolean }) =>
    adminFetch('/api/admin/blog/settings', { method: 'PATCH', body: JSON.stringify(body) }),
  syncBlog: () => adminFetch('/api/admin/blog/sync', { method: 'POST' }),
  syncBlogSlug: (slug: string) =>
    adminFetch(`/api/admin/blog/sync/${encodeURIComponent(slug)}`, { method: 'POST' }),
  chunk: (id: string) =>
    adminFetch(`/api/knowledge/chunks/${encodeURIComponent(id)}`),
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
  history: (sessionId: string) => adminFetch(`/api/chat/${encodeURIComponent(sessionId)}/messages`),
  deleteSession: (sessionId: string) =>
    adminFetch(`/api/chat/${encodeURIComponent(sessionId)}`, { method: 'DELETE' }),
  scope: (slug: string) => adminFetch(`/api/hub/scope?slug=${encodeURIComponent(slug)}`),
  feedback: (body: { sessionId: string; messageId: number; score: 'UP' | 'DOWN'; cited?: boolean; comment?: string }) =>
    adminFetch('/api/chat/feedback', { method: 'POST', body: JSON.stringify(body) }),
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
