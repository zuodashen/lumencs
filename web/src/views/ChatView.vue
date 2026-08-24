<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import InteractiveCard from '../components/InteractiveCard.vue'
import { api, streamCard, streamChat, type AgentStep, type Citation, type WorkflowCard } from '../api'

type ChatItem = {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
  intent?: string
  card?: WorkflowCard
  cardDone?: boolean
}

const route = useRoute()
const embed = route.path === '/embed'
const SESSION_KEY = embed ? 'lumencs_embed_session' : 'lumencs_session'
const sessionId = ref(localStorage.getItem(SESSION_KEY) || '')
const input = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref<ChatItem[]>([])
const steps = ref<AgentStep[]>([])
const listEl = ref<HTMLElement | null>(null)
/** 引用展开：chunkId -> 完整原文 */
const expandedCitations = ref<Record<string, { loading: boolean; content?: string; error?: string }>>({})

const prompts = ['加班口渴，帮我点杯奶茶', '再来一杯，送到老工位', '理财产品A收益多少？', '我要退款']

onMounted(() => {
  if (!sessionId.value) {
    sessionId.value = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, sessionId.value)
  }
})

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
  onMessage(msg: { content: string; citations?: Citation[]; intent?: string }) {
    const last = messages.value[messages.value.length - 1]
    if (last?.card && !last.content) {
      last.content = msg.content
      last.intent = msg.intent
      last.citations = msg.citations
      return
    }
    if (last && last.role === 'assistant' && !last.card) {
      last.content = msg.content
      last.intent = msg.intent
      last.citations = msg.citations
      return
    }
    messages.value.push({
      role: 'assistant',
      content: msg.content,
      citations: msg.citations,
      intent: msg.intent,
    })
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
    await streamChat({ sessionId: sessionId.value, userLabel: '访客', message }, sseHandler)
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
  const summary = Object.entries(values)
    .filter(([, v]) => v)
    .map(([k, v]) => `${k}=${v}`)
    .join('，')
  messages.value.push({ role: 'user', content: summary ? `已填写：${summary}` : '已提交卡片' })
  steps.value = []
  loading.value = true
  await scrollBottom()
  try {
    await streamCard(
      { sessionId: sessionId.value, userLabel: '访客', cardId: item.card.cardId, values },
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
    supervisor: 'Supervisor',
    intent_router: '意图路由',
    knowledge_rag: '知识检索',
    workflow: '办事流程',
    mcp: 'MCP 工具',
    ticket_handler: '工单处理',
    compliance: '合规审查',
  }
  return `${names[step.agent] || step.agent} · ${step.status}`
}

/** 引用可点：点击后拉取 chunk 完整原文并展开 */
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
</script>

<template>
  <div class="flex min-h-screen flex-col" :class="embed ? 'min-h-[100vh]' : ''">
    <header class="flex items-center justify-between border-b border-[#243049] px-5 py-3">
      <div>
        <p class="text-xs tracking-[0.2em] text-[#8b9bb8] uppercase">LumenCS</p>
        <h1 class="text-lg font-semibold">{{ embed ? '微光助手' : '微光客服工作台' }}</h1>
      </div>
      <div v-if="!embed" class="flex items-center gap-3 text-sm">
        <button class="rounded-md border border-[#243049] px-3 py-1.5 text-[#8b9bb8]" @click="newSession">新会话</button>
        <RouterLink class="rounded-md bg-[#3dd6c6] px-3 py-1.5 font-medium text-[#0b1220]" to="/console">控制台</RouterLink>
      </div>
    </header>

    <main class="mx-auto grid w-full max-w-6xl flex-1 grid-cols-1 gap-4 p-4" :class="embed ? '' : 'lg:grid-cols-[1fr_280px]'">
      <section class="flex min-h-[70vh] flex-col rounded-xl border border-[#243049] bg-[#121a2b]">
        <div ref="listEl" class="flex-1 space-y-4 overflow-y-auto p-5">
          <div v-if="!messages.length" class="text-sm text-[#8b9bb8]">
            加班改 bug 口渴了？说「帮我点杯奶茶」。下过单后再说「再来一杯」会按口味预填。
            <div class="mt-3 flex flex-wrap gap-2">
              <button
                v-for="item in prompts"
                :key="item"
                class="rounded-full border border-[#243049] px-3 py-1 text-xs"
                @click="send(item)"
              >
                {{ item }}
              </button>
            </div>
          </div>
          <article v-for="(msg, idx) in messages" :key="idx" class="max-w-[85%]" :class="msg.role === 'user' ? 'ml-auto' : ''">
            <p class="mb-1 text-xs text-[#8b9bb8]">{{ msg.role === 'user' ? '你' : 'LumenCS' }}</p>
            <div v-if="msg.content" class="whitespace-pre-wrap rounded-xl px-4 py-3 text-sm" :class="msg.role === 'user' ? 'bg-[#1c2a44]' : 'bg-[#0f1728]'">
              {{ msg.content }}
            </div>
            <InteractiveCard
              v-if="msg.card"
              :card="msg.card"
              :disabled="msg.cardDone || loading"
              @submit="(values) => submitCard(msg, values)"
            />
            <div v-if="msg.citations?.length" class="mt-2 space-y-1">
              <p class="text-xs text-[#8b9bb8]">引用来源（点击展开原文）</p>
              <div
                v-for="cite in msg.citations"
                :key="cite.id"
                class="cursor-pointer rounded-lg border border-[#243049] px-3 py-2 text-xs text-[#8b9bb8] hover:border-[#3dd6c6]"
                @click="toggleCitation(cite)"
              >
                <span class="text-[#3dd6c6]">{{ cite.source }}</span>
                · score {{ Number(cite.score).toFixed(3) }}
                <p class="mt-1 text-[#c5d0e6]">{{ cite.snippet }}</p>
                <div v-if="expandedCitations[cite.id]" class="mt-2 rounded-lg bg-[#0b1220] p-3">
                  <p v-if="expandedCitations[cite.id].loading" class="text-[#8b9bb8]">加载中…</p>
                  <p v-else-if="expandedCitations[cite.id].error" class="text-[#f07178]">{{ expandedCitations[cite.id].error }}</p>
                  <p v-else class="whitespace-pre-wrap text-[#c5d0e6]">{{ expandedCitations[cite.id].content }}</p>
                </div>
              </div>
            </div>
          </article>
          <p v-if="loading" class="text-xs text-[#8b9bb8]">Agent 正在处理…</p>
          <p v-if="error" class="text-sm text-[#f07178]">{{ error }}</p>
        </div>
        <form class="border-t border-[#243049] p-3" @submit.prevent="send()">
          <div class="flex gap-2">
            <input
              v-model="input"
              class="flex-1 rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm outline-none"
              placeholder="输入问题，回车发送"
            />
            <button class="rounded-lg bg-[#3dd6c6] px-4 py-2 text-sm font-medium text-[#0b1220]" :disabled="loading">发送</button>
          </div>
        </form>
      </section>

      <aside v-if="!embed" class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <h2 class="mb-3 text-sm font-semibold">Agent 时间线</h2>
        <ol v-if="steps.length" class="space-y-2">
          <li v-for="(step, idx) in steps" :key="idx" class="rounded-lg border border-[#243049] px-3 py-2 text-xs">
            <p class="text-[#3dd6c6]">{{ stepLabel(step) }}</p>
            <p v-if="step.intent" class="text-[#8b9bb8]">intent: {{ step.intent }}</p>
            <p v-if="step.confidence != null" class="text-[#8b9bb8]">confidence: {{ Number(step.confidence).toFixed(2) }}</p>
            <p v-if="step.clarify" class="text-[#f07178]">低置信度 → 已要求澄清</p>
            <p v-if="step.workflow" class="text-[#8b9bb8]">workflow: {{ step.workflow }}</p>
            <p v-if="step.tool" class="text-[#8b9bb8]">tool: {{ step.tool }}</p>
            <p v-if="step.hitCount != null" class="text-[#8b9bb8]">hits: {{ step.hitCount }} → top{{ step.rankedCount ?? '-' }}</p>
            <p v-if="step.ticketNo" class="text-[#8b9bb8]">{{ step.ticketNo }}</p>
            <p v-if="step.passed === false" class="text-[#f07178]">合规未通过</p>
            <p v-if="step.hitl" class="text-[#f07178]">已转人工审核 #{{ step.reviewId }}</p>
          </li>
        </ol>
        <p v-else class="text-xs text-[#8b9bb8]">发送消息后会显示意图 → 卡片/检索 → MCP 工具 → 合规。</p>
      </aside>
    </main>
  </div>
</template>
