<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import InteractiveCard from '../components/InteractiveCard.vue'
import { api, streamCard, streamChat, type AgentStep, type ChatResult, type Citation, type WorkflowCard } from '../api'

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
const embed = route.path === '/embed'
const articleSlug = ref(typeof route.query.slug === 'string' ? route.query.slug : '')
const articleTitle = ref('')
const SESSION_KEY = embed ? 'lumencs_embed_session' : 'lumencs_session'
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
  : ['加班口渴，帮我点杯奶茶', '帮我写一篇博客：OrbStack 连不上 Docker MySQL', '理财产品A收益多少？', '我要退款']

onMounted(async () => {
  if (!sessionId.value) {
    sessionId.value = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, sessionId.value)
  }
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

function newSession() {
  sessionId.value = crypto.randomUUID()
  localStorage.setItem(SESSION_KEY, sessionId.value)
  messages.value = []
  steps.value = []
  error.value = ''
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
        <RouterLink class="btn-ghost" to="/console/login?next=/">登录</RouterLink>
        <RouterLink class="btn-primary" to="/console">中枢控制台</RouterLink>
      </div>
    </header>

    <main
      class="mx-auto grid w-full max-w-6xl flex-1 gap-4 px-4 pb-6"
      :class="embed ? 'grid-cols-1' : 'lg:grid-cols-[minmax(0,1fr)_280px]'"
    >
      <section class="panel flex min-h-[72vh] flex-col overflow-hidden">
        <div v-if="articleSlug" class="border-b border-[var(--line)] px-5 py-3 text-sm">
          <span class="muted">正在以单文范围检索 · </span>
          <span class="accent">{{ articleTitle || articleSlug }}</span>
        </div>
        <div ref="listEl" class="flex-1 space-y-5 overflow-y-auto p-5">
          <div v-if="!messages.length" class="max-w-lg">
            <p class="serif text-3xl leading-tight">先问一句，再决定走知识还是办事。</p>
            <p class="muted mt-3 text-sm leading-relaxed">
              {{ articleSlug
                ? '回答只会引用当前文章的切块。不确定的内容会建议转人工或去知识库补文档。'
                : '点奶茶看记忆预填；问收益看引用。写博客请先登录控制台，再回本页发「写一篇」。' }}
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
