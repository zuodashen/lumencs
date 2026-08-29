<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import InteractiveCard from '../components/InteractiveCard.vue'
import { api, logoutTokens, streamCard, streamChat, type AgentStep, type ChatResult, type Citation, type WorkflowCard } from '../api'

type ChatItem = {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  intent?: string
  card?: WorkflowCard
  cardDone?: boolean
  messageId?: number
  reviewPending?: boolean
  feedback?: 'UP' | 'DOWN'
}

const route = useRoute()
const router = useRouter()
const embed = route.path === '/embed'
const hubUser = localStorage.getItem('lumencs_user') || 'admin'
const articleSlug = ref(typeof route.query.slug === 'string' ? route.query.slug : '')
const articleTitle = ref('')
const SESSION_KEY = embed ? 'lumencs_embed_session' : 'lumencs_session'
const SESSION_LIST_KEY = embed ? 'lumencs_embed_sessions' : 'lumencs_sessions'

type SessionMeta = { id: string; title: string; updatedAt: number }
const sessions = ref<SessionMeta[]>([])
const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')
const input = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref<ChatItem[]>([])
const steps = ref<AgentStep[]>([])
const listEl = ref<HTMLElement | null>(null)
const expandedCitations = ref<Record<string, { loading: boolean; content?: string; error?: string }>>({})
let pollTimer: number | null = null

const prompts = articleSlug.value
  ? ['这篇文章在讲什么？', '有哪些关键结论？', '和我手头的项目怎么结合？']
  : ['帮我记一下：生椰拿铁少糖少冰', '加个待办：周五把周报交了', '我现在有哪些待办', '加班口渴，帮我点杯奶茶']

onMounted(async () => {
  sessions.value = readSessionList()
  if (!sessionId.value) {
    sessionId.value = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, sessionId.value)
  }
  touchSession(sessionId.value)
  if (articleSlug.value) {
    try {
      const scope = await api.scope(articleSlug.value)
      articleTitle.value = scope.title
    } catch {
      articleTitle.value = articleSlug.value
    }
  }
  await loadHistory()
  pollTimer = window.setInterval(() => {
    if (!loading.value) loadHistory(true)
  }, 8000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

async function loadHistory(silent = false) {
  if (!sessionId.value) return
  try {
    const rows = await api.history(sessionId.value)
    if (!Array.isArray(rows) || !rows.length) return
    if (silent && messages.value.some((m) => m.card && !m.cardDone)) return
    const mapped: ChatItem[] = rows.map((row: any) => ({
      role: row.role === 'user' ? 'user' : 'assistant',
      content: row.content,
      intent: row.intent,
      citations: row.citations || [],
      messageId: row.id,
      reviewPending: typeof row.content === 'string' && row.content.includes('已转人工审核'),
    }))
    if (silent && mapped.length <= messages.value.filter((m) => m.content).length) return
    const cards = messages.value.filter((m) => m.card)
    messages.value = mapped
    for (const card of cards) {
      if (!messages.value.some((m) => m.card?.cardId === card.card?.cardId)) {
        messages.value.push(card)
      }
    }
  } catch {
    if (!silent) {
      /* first load may be empty session */
    }
  }
}

async function scrollBottom() {
  await nextTick()
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

const sseHandler = {
  onSession(id: string) {
    sessionId.value = id
    localStorage.setItem(SESSION_KEY, id)
    touchSession(id)
  },
  onStep(step: AgentStep) {
    steps.value.push(step)
  },
  onCard(card: WorkflowCard) {
    messages.value.push({ role: 'assistant', content: '', card })
  },
  onToken(delta: string) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && !last.card) {
      last.content += delta
      return
    }
    messages.value.push({ role: 'assistant', content: delta })
  },
  onMessage(msg: ChatResult) {
    const last = messages.value[messages.value.length - 1]
    const patch = {
      content: msg.content,
      intent: msg.intent,
      citations: msg.citations,
      messageId: msg.messageId,
      reviewPending: msg.reviewPending,
    }
    if (last?.card && !last.content) {
      Object.assign(last, patch)
      return
    }
    if (last && last.role === 'assistant' && !last.card) {
      Object.assign(last, patch)
      return
    }
    messages.value.push({ role: 'assistant', ...patch })
  },
  onError(message: string) {
    error.value = message
  },
}

async function send(text?: string) {
  const message = (text ?? input.value).trim()
  if (!message || loading.value) return
  error.value = ''
  input.value = ''
  messages.value.push({ role: 'user', content: message })
  touchSession(sessionId.value, message)
  steps.value = []
  loading.value = true
  await scrollBottom()
  try {
    await streamChat(
      {
        sessionId: sessionId.value,
        userLabel: '访客',
        message,
        articleSlug: articleSlug.value || undefined,
      },
      sseHandler,
    )
  } catch (e) {
    error.value = e instanceof Error ? e.message : '发送失败'
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

async function submitCard(item: ChatItem, values: Record<string, string>) {
  if (!item.card || item.cardDone || loading.value) return
  item.cardDone = true
  const title = values.title || values.name || '提交'
  messages.value.push({ role: 'user', content: `已确认卡片：${title}` })
  steps.value = []
  loading.value = true
  await scrollBottom()
  try {
    await streamCard(
      {
        sessionId: sessionId.value,
        userLabel: '访客',
        cardId: item.card.cardId,
        confirmToken: item.card.confirmToken,
        values,
      },
      sseHandler,
    )
  } catch (e) {
    error.value = e instanceof Error ? e.message : '提交失败'
    item.cardDone = false
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

function readSessionList(): SessionMeta[] {
  try {
    const raw = JSON.parse(localStorage.getItem(SESSION_LIST_KEY) || '[]')
    return Array.isArray(raw) ? raw : []
  } catch {
    return []
  }
}

function writeSessionList(list: SessionMeta[]) {
  const trimmed = list
    .sort((a, b) => b.updatedAt - a.updatedAt)
    .slice(0, 30)
  localStorage.setItem(SESSION_LIST_KEY, JSON.stringify(trimmed))
  sessions.value = trimmed
}

function touchSession(id: string, maybeTitle?: string) {
  if (!id) return
  const list = readSessionList()
  const title = (maybeTitle || '').trim().slice(0, 24)
  const existing = list.find((item) => item.id === id)
  if (existing) {
    existing.updatedAt = Date.now()
    if (title && (existing.title === '新对话' || !existing.title)) {
      existing.title = title
    }
  } else {
    list.unshift({ id, title: title || '新对话', updatedAt: Date.now() })
  }
  writeSessionList(list)
}

async function openSession(id: string) {
  if (id === sessionId.value || loading.value) return
  sessionId.value = id
  localStorage.setItem(SESSION_KEY, id)
  messages.value = []
  steps.value = []
  error.value = ''
  touchSession(id)
  await loadHistory()
}

function newSession() {
  sessionId.value = crypto.randomUUID()
  localStorage.setItem(SESSION_KEY, sessionId.value)
  messages.value = []
  steps.value = []
  error.value = ''
  touchSession(sessionId.value)
}

async function removeSession(id: string) {
  if (loading.value) return
  try {
    await api.deleteSession(id)
  } catch {
    /* 本地列表仍删 */
  }
  writeSessionList(readSessionList().filter((item) => item.id !== id))
  if (id === sessionId.value) {
    newSession()
  }
}

function stepLabel(step: AgentStep) {
  const names: Record<string, string> = {
    supervisor: '编排',
    intent_router: '意图',
    knowledge_rag: '检索',
    chitchat: '闲聊',
    workflow: '办事',
    mcp: '工具',
    compliance: '合规',
  }
  return `${names[step.agent] || step.agent} · ${step.status}`
}

function logout() {
  logoutTokens()
  router.push('/console/login?next=/')
}

async function toggleCitation(cite: Citation) {
  const state = expandedCitations.value[cite.id] || { loading: false }
  if (state.content) {
    delete expandedCitations.value[cite.id]
    return
  }
  expandedCitations.value[cite.id] = { loading: true }
  try {
    const detail = await api.chunk(cite.id)
    expandedCitations.value[cite.id] = { loading: false, content: detail.content }
  } catch (e) {
    expandedCitations.value[cite.id] = { loading: false, error: e instanceof Error ? e.message : '加载失败' }
  }
}

async function rate(item: ChatItem, score: 'UP' | 'DOWN') {
  if (!item.messageId) return
  item.feedback = score
  try {
    await api.feedback({
      sessionId: sessionId.value,
      messageId: item.messageId,
      score,
      cited: Boolean(item.citations?.length),
    })
  } catch (e) {
    item.feedback = undefined
    error.value = e instanceof Error ? e.message : '评分失败'
  }
}
</script>

<template>
  <div class="flex min-h-screen flex-col">
    <header class="flex items-center justify-between px-5 py-4">
      <div>
        <p class="text-[11px] uppercase tracking-[0.28em] text-[var(--accent)]">Lumen Hub</p>
        <h1 class="serif mt-0.5 text-2xl text-[var(--text)]">
          {{ embed ? (articleTitle ? '聊这篇' : '微光助手') : '个人 AI 服务中枢' }}
        </h1>
      </div>
      <div v-if="!embed" class="flex items-center gap-2">
        <button class="btn-ghost" @click="newSession">新会话</button>
        <span class="muted text-sm">{{ hubUser }}</span>
        <RouterLink class="btn-primary" to="/console">中枢控制台</RouterLink>
        <button class="btn-ghost" @click="logout">退出</button>
      </div>
    </header>

    <main
      class="mx-auto grid w-full max-w-6xl flex-1 gap-4 px-4 pb-6"
      :class="embed ? 'grid-cols-1' : 'lg:grid-cols-[200px_minmax(0,1fr)_240px]'"
    >
      <aside v-if="!embed" class="panel h-fit max-h-[72vh] overflow-y-auto p-3">
        <div class="mb-2 flex items-center justify-between">
          <p class="text-[11px] uppercase tracking-[0.2em] text-[var(--muted)]">会话</p>
          <button class="text-xs accent" @click="newSession">新建</button>
        </div>
        <div
          v-for="item in sessions"
          :key="item.id"
          class="mb-1 flex items-center gap-1 rounded-xl px-2 py-1.5"
          :class="item.id === sessionId ? 'bg-[var(--accent-dim)]' : ''"
        >
          <button
            class="min-w-0 flex-1 truncate text-left text-xs"
            :class="item.id === sessionId ? 'text-[var(--accent)]' : 'text-[var(--muted)]'"
            @click="openSession(item.id)"
          >
            {{ item.title || '新对话' }}
          </button>
          <button
            class="shrink-0 px-1 text-[11px] text-[var(--muted)] hover:text-[var(--danger)]"
            title="删除"
            @click.stop="removeSession(item.id)"
          >
            删除
          </button>
        </div>
        <p v-if="!sessions.length" class="muted text-xs">暂无会话</p>
      </aside>
      <section class="panel flex min-h-[72vh] flex-col overflow-hidden">
        <div v-if="articleSlug" class="border-b border-[var(--line)] px-5 py-3 text-sm">
          <span class="muted">正在以单文范围检索 · </span>
          <span class="accent">{{ articleTitle || articleSlug }}</span>
        </div>
        <div ref="listEl" class="flex-1 space-y-5 overflow-y-auto p-5">
          <div v-if="!messages.length" class="max-w-lg">
            <p class="serif text-3xl leading-tight">有什么需要帮忙的？</p>
            <p v-if="articleSlug" class="muted mt-3 text-sm leading-relaxed">
              回答只会引用当前文章。
            </p>
            <div class="mt-5 flex flex-wrap gap-2">
              <button v-for="item in prompts" :key="item" class="chip" @click="send(item)">{{ item }}</button>
            </div>
          </div>
          <article
            v-for="(msg, idx) in messages"
            :key="idx"
            class="max-w-[88%]"
            :class="msg.role === 'user' ? 'ml-auto' : ''"
          >
            <p class="mb-1 text-[11px] uppercase tracking-wider text-[var(--muted)]">
              {{ msg.role === 'user' ? '你' : 'Lumen' }}
            </p>
            <div
              v-if="msg.content"
              class="whitespace-pre-wrap rounded-2xl px-4 py-3 text-sm leading-relaxed"
              :class="msg.role === 'user' ? 'bg-[var(--user)]' : 'bg-[var(--bg-elev)]'"
            >
              {{ msg.content }}
            </div>
            <InteractiveCard
              v-if="msg.card"
              :card="msg.card"
              :disabled="msg.cardDone || loading"
              @submit="(values) => submitCard(msg, values)"
            />
            <div v-if="msg.citations?.length" class="mt-2 space-y-1">
              <p class="text-[11px] text-[var(--muted)]">引用（点击展开）</p>
              <button
                v-for="cite in msg.citations"
                :key="cite.id"
                class="block w-full rounded-xl border border-[var(--line)] px-3 py-2 text-left text-xs"
                @click="toggleCitation(cite)"
              >
                <span class="accent">{{ cite.source }}</span>
                <span class="muted"> · {{ Number(cite.score).toFixed(3) }}</span>
                <p class="mt-1 text-[var(--text)]/80">{{ cite.snippet }}</p>
                <div v-if="expandedCitations[cite.id]" class="mt-2 rounded-lg bg-[var(--bg)] p-3">
                  <p v-if="expandedCitations[cite.id].loading" class="muted">加载中…</p>
                  <p v-else-if="expandedCitations[cite.id].error" class="danger">{{ expandedCitations[cite.id].error }}</p>
                  <p v-else class="whitespace-pre-wrap text-[var(--text)]/90">{{ expandedCitations[cite.id].content }}</p>
                </div>
              </button>
            </div>
            <div
              v-if="msg.role === 'assistant' && msg.messageId && !msg.reviewPending && msg.content"
              class="mt-2 flex gap-2 text-xs"
            >
              <button class="chip" :class="msg.feedback === 'UP' ? 'border-[var(--sage)] text-[var(--sage)]' : ''" @click="rate(msg, 'UP')">有用</button>
              <button class="chip" :class="msg.feedback === 'DOWN' ? 'border-[var(--danger)] text-[var(--danger)]' : ''" @click="rate(msg, 'DOWN')">缺口</button>
            </div>
          </article>
          <p v-if="loading" class="text-xs text-[var(--accent)]">编排进行中…</p>
          <p v-if="error" class="text-sm danger">{{ error }}</p>
        </div>
        <form class="border-t border-[var(--line)] p-3" @submit.prevent="send()">
          <div class="flex gap-2">
            <input v-model="input" class="input flex-1" placeholder="输入，回车发送" />
            <button class="btn-primary" :disabled="loading">发送</button>
          </div>
        </form>
      </section>

      <aside v-if="!embed" class="panel h-fit p-4">
        <p class="text-[11px] uppercase tracking-[0.2em] text-[var(--muted)]">时间线</p>
        <ol v-if="steps.length" class="mt-3 space-y-2">
          <li v-for="(step, idx) in steps" :key="idx" class="rounded-xl border border-[var(--line)] px-3 py-2 text-xs">
            <p class="accent">{{ stepLabel(step) }}</p>
            <p v-if="step.intent" class="muted">intent {{ step.intent }}</p>
            <p v-if="step.confidence != null" class="muted">conf {{ Number(step.confidence).toFixed(2) }}</p>
            <p v-if="step.clarify" class="danger">低置信 · 已澄清</p>
            <p v-if="step.tool" class="muted">{{ step.tool }}</p>
            <p v-if="step.hitl" class="danger">HITL #{{ step.reviewId }}</p>
          </li>
        </ol>
        <p v-else class="muted mt-3 text-xs leading-relaxed">意图 → 卡片或检索 → 工具 → 合规。审核通过后会自动写回本会话。</p>
      </aside>
    </main>
  </div>
</template>
