<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import InteractiveCard from './InteractiveCard.vue'
import Icon from './Icon.vue'
import { api, streamCard, streamChat, type AgentStep, type ChatEmbed, type ChatResult, type Citation, type WorkflowCard } from '../api'
import ChatEmbedCard from './ChatEmbed.vue'
import { FEATURED_PROMPTS } from '../playbook'

type ChatItem = {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  intent?: string
  card?: WorkflowCard
  cardDone?: boolean
  embed?: ChatEmbed
  messageId?: number
  reviewPending?: boolean
  feedback?: 'UP' | 'DOWN'
}

const props = withDefaults(defineProps<{ layout?: 'page' | 'dock' | 'embed' }>(), { layout: 'page' })
const route = useRoute()
const embed = props.layout === 'embed' || route.path === '/embed'
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
  : FEATURED_PROMPTS

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
  const q = route.query.q
  if (typeof q === 'string' && q.trim()) {
    input.value = q
  }
  pollTimer = window.setInterval(() => {
    if (!loading.value) loadHistory(true)
  }, 8000)
})

onUnmounted(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string' && q.trim()) input.value = q
  },
)

async function loadHistory(silent = false) {
  if (!sessionId.value) return
  try {
    const rows = await api.history(sessionId.value)
    if (!Array.isArray(rows) || !rows.length) return
    if (silent && messages.value.some((m) => (m.card && !m.cardDone) || m.embed)) return
    const mapped: ChatItem[] = rows.map((row: any) => ({
      role: row.role === 'user' ? 'user' : 'assistant',
      content: row.content,
      intent: row.intent,
      citations: row.citations || [],
      embed: row.embed || undefined,
      messageId: row.id,
      reviewPending: typeof row.content === 'string' && row.content.includes('已转人工审核'),
    }))
    if (silent && mapped.length <= messages.value.filter((m) => m.content).length) return
    const extras = messages.value.filter((m) => m.card || m.embed)
    messages.value = mapped
    for (const extra of extras) {
      if (extra.card && !messages.value.some((m) => m.card?.cardId === extra.card?.cardId)) {
        messages.value.push(extra)
      } else if (extra.embed && !messages.value.some((m) => m.embed === extra.embed)) {
        messages.value.push(extra)
      }
    }
  } catch {
    /* empty session */
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
  onEmbed(embed: ChatEmbed) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant') {
      last.embed = embed
      return
    }
    messages.value.push({ role: 'assistant', content: '', embed })
  },
  onToken(delta: string) {
    const last = messages.value[messages.value.length - 1]
    if (last && last.role === 'assistant' && !last.card && !last.embed) {
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
      embed: msg.embed || last?.embed,
    }
    if (last?.card && !last.content) {
      Object.assign(last, patch)
      return
    }
    if (last && last.role === 'assistant' && (!last.card || last.content || last.embed)) {
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
        userLabel: localStorage.getItem('lumencs_user') || 'admin',
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
        userLabel: localStorage.getItem('lumencs_user') || 'admin',
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
  const trimmed = list.sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 30)
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
    if (title && (existing.title === '新对话' || !existing.title)) existing.title = title
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
    /* keep local */
  }
  writeSessionList(readSessionList().filter((item) => item.id !== id))
  if (id === sessionId.value) newSession()
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

function onKey(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}
</script>

<template>
  <div
    class="grid gap-4"
    :class="
      embed || layout === 'dock'
        ? 'grid-cols-1'
        : 'lg:grid-cols-[200px_minmax(0,1fr)_220px]'
    "
  >
    <aside v-if="layout === 'page'" class="panel h-fit max-h-[72vh] overflow-y-auto p-3">
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
        <button class="shrink-0 px-1 text-[11px] text-[var(--muted)] hover:text-[var(--danger)]" @click.stop="removeSession(item.id)">
          删
        </button>
      </div>
      <p v-if="!sessions.length" class="muted text-xs">暂无会话</p>
    </aside>

    <section class="panel flex flex-col overflow-hidden" :class="layout === 'dock' ? 'min-h-[420px]' : 'min-h-[72vh]'">
      <div class="flex items-center justify-between border-b border-[var(--line)] px-4 py-3">
        <div>
          <p class="text-sm font-semibold">AI 助手</p>
          <p v-if="articleSlug" class="muted text-xs">单文 · {{ articleTitle || articleSlug }}</p>
        </div>
        <button v-if="layout !== 'embed'" class="btn-ghost px-2 py-1 text-xs" @click="newSession">新会话</button>
      </div>
      <div ref="listEl" class="flex-1 space-y-4 overflow-y-auto p-4">
        <div v-if="!messages.length">
          <p class="text-sm leading-relaxed">你好，我是你的个人助手。可以记笔记、看待办、列出已发布博客，或查一只股票行情。</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <button v-for="item in prompts" :key="item" class="chip" @click="send(item)">{{ item }}</button>
          </div>
        </div>
        <article
          v-for="(msg, idx) in messages"
          :key="idx"
          :class="[msg.role === 'user' ? 'ml-auto' : '', msg.embed?.kind === 'stock' ? 'max-w-full' : 'max-w-[92%]']"
        >
          <p class="mb-1 text-[11px] uppercase tracking-wider text-[var(--muted)]">
            {{ msg.role === 'user' ? '你' : 'Lumen' }}
          </p>
          <div
            v-if="msg.content"
            class="whitespace-pre-wrap rounded-2xl px-3 py-2.5 text-sm leading-relaxed"
            :class="msg.role === 'user' ? 'bg-[var(--user)]' : 'bg-[var(--bg-elev)]'"
          >
            {{ msg.content }}
          </div>
          <InteractiveCard v-if="msg.card" :card="msg.card" :disabled="msg.cardDone || loading" @submit="(values) => submitCard(msg, values)" />
          <ChatEmbedCard v-if="msg.embed" :embed="msg.embed" :busy="loading" @prompt="send" />
          <div v-if="msg.citations?.length" class="mt-2 space-y-1">
            <button
              v-for="cite in msg.citations"
              :key="cite.id"
              class="block w-full rounded-xl border border-[var(--line)] px-3 py-2 text-left text-xs"
              @click="toggleCitation(cite)"
            >
              <span class="accent">{{ cite.source }}</span>
              <p class="mt-1 text-[var(--text)]/80">{{ cite.snippet }}</p>
              <p v-if="expandedCitations[cite.id]?.content" class="mt-2 whitespace-pre-wrap">{{ expandedCitations[cite.id].content }}</p>
            </button>
          </div>
          <div v-if="msg.role === 'assistant' && msg.messageId && !msg.reviewPending && msg.content" class="mt-2 flex gap-2 text-xs">
            <button class="chip" @click="rate(msg, 'UP')">有用</button>
            <button class="chip" @click="rate(msg, 'DOWN')">缺口</button>
          </div>
        </article>
        <p v-if="loading" class="text-xs text-[var(--accent)]">编排进行中…</p>
        <p v-if="error" class="text-sm danger">{{ error }}</p>
      </div>
      <form class="border-t border-[var(--line)] p-3" @submit.prevent="send()">
        <div class="flex gap-2">
          <textarea
            v-model="input"
            class="input min-h-11 flex-1 resize-none py-2.5"
            rows="1"
            placeholder="输入消息…"
            @keydown="onKey"
          />
          <button class="btn-primary inline-flex items-center gap-1" :disabled="loading">
            <Icon name="send" :size="14" />
            发送
          </button>
        </div>
        <p class="muted mt-2 text-[11px]">Enter 发送，Shift + Enter 换行</p>
      </form>
    </section>

    <aside v-if="layout === 'page' || layout === 'dock'" class="panel h-fit p-4">
      <p class="text-[11px] uppercase tracking-[0.2em] text-[var(--muted)]">时间线</p>
      <ol v-if="steps.length" class="mt-3 space-y-2">
        <li v-for="(step, idx) in steps" :key="idx" class="rounded-xl border border-[var(--line)] px-3 py-2 text-xs">
          <p class="accent">{{ stepLabel(step) }}</p>
          <p v-if="step.intent" class="muted">{{ step.intent }}</p>
        </li>
      </ol>
      <p v-else class="muted mt-3 text-xs leading-relaxed">意图 → 卡片或检索 → 工具 → 合规。审核结果会写回本会话。</p>
    </aside>
  </div>
</template>
