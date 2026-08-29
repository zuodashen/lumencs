<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api, type Page } from '../api'
import ChatPanel from '../components/ChatPanel.vue'
import Icon from '../components/Icon.vue'
import { LIVE_APPS } from '../apps'
import { nextDoneStatus, relativeTime, statusZh } from '../status'

const router = useRouter()
const overview = ref<Record<string, unknown>>({})
const tickets = ref<any[]>([])
const inbox = ref<any[]>([])
const docs = ref<any[]>([])
const error = ref('')

const stats = computed(() => [
  { label: '知识文档', value: overview.value.documents, hint: '已入库', icon: 'file', color: '#5b8def' },
  { label: '待办', value: overview.value.tickets, hint: '全部事项', icon: 'check', color: '#3dd6c6' },
  { label: '待审核', value: overview.value.pendingReviews, hint: '安全审核', icon: 'shield', color: '#ff9f43' },
  { label: '未读事件', value: overview.value.unreadInbox, hint: '事件箱', icon: 'inbox', color: '#8b6cff' },
  { label: '知识缺口', value: overview.value.csatDown, hint: '差评 / 无引用', icon: 'gap', color: '#ff6b7a' },
  { label: 'RAG', value: overview.value.rag === 'up' ? '正常' : overview.value.rag === 'down' ? '异常' : '—', hint: '向量服务', icon: 'spark', color: '#5b8def' },
  { label: 'Redis', value: overview.value.redis === 'up' ? '正常' : overview.value.redis === 'down' ? '异常' : '—', hint: '记忆 / 限流', icon: 'activity', color: '#3dd6c6' },
  { label: '消息', value: overview.value.messages, hint: '历史消息', icon: 'chat', color: '#8b6cff' },
])

const modules = computed(() => {
  const groups: Record<string, { label: string; icon: string; count: number }> = {
    memo: { label: '随手记', icon: 'file', count: 0 },
    blog: { label: '博客同步', icon: 'book', count: 0 },
    other: { label: '本地上传', icon: 'folder', count: 0 },
  }
  for (const doc of docs.value) {
    const source = String(doc.source || '')
    if (source === 'memo') groups.memo.count += 1
    else if (source.startsWith('blog:')) groups.blog.count += 1
    else groups.other.count += 1
  }
  return Object.values(groups)
})

const tools = [
  { to: '/chat?q=' + encodeURIComponent('帮我记一下：'), label: '记一笔', icon: 'file', color: '#5b8def' },
  { to: '/chat?q=' + encodeURIComponent('加个待办：'), label: '新待办', icon: 'plus', color: '#3dd6c6' },
  { to: '/console/knowledge', label: '知识库', icon: 'book', color: '#8b6cff' },
  { to: '/chat?q=' + encodeURIComponent('我现在有哪些待办'), label: '查待办', icon: 'search', color: '#ff9f43' },
  { to: '/console/gaps', label: '知识缺口', icon: 'gap', color: '#ff6b7a' },
  { to: '/console/knowledge', label: '上传文档', icon: 'upload', color: '#5b8def' },
]

onMounted(async () => {
  try {
    const [ov, tk, ib, kb] = await Promise.all([
      api.hubOverview(),
      api.tickets({ pageNum: 1, pageSize: 6 }),
      api.inbox(),
      api.documents(1, 12),
    ])
    overview.value = ov || {}
    tickets.value = (tk as Page<any>).records || []
    inbox.value = (ib || []).slice(0, 6)
    docs.value = (kb as Page<any>).records || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function toggleDone(item: any) {
  const next = nextDoneStatus(item.status)
  if (!next) return
  try {
    await api.updateTicket(item.id, next)
    const tk = (await api.tickets({ pageNum: 1, pageSize: 6 })) as Page<any>
    tickets.value = tk.records || []
    overview.value = (await api.hubOverview()) || overview.value
  } catch (e) {
    error.value = e instanceof Error ? e.message : '改状态失败'
  }
}

function goChat(q: string) {
  router.push('/chat?q=' + encodeURIComponent(q))
}
</script>

<template>
  <div class="xl:grid xl:grid-cols-[minmax(0,1fr)_340px] xl:gap-4">
    <div class="space-y-4">
      <p v-if="error" class="danger text-sm">{{ error }}</p>
      <section>
        <h2 class="mb-3 text-sm font-semibold text-[var(--muted)]">服务中枢概览</h2>
        <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <div v-for="item in stats" :key="item.label" class="panel p-4">
            <span class="stat-icon text-white" :style="{ background: item.color }">
              <Icon :name="item.icon" :size="15" />
            </span>
            <p class="mt-3 text-xs text-[var(--muted)]">{{ item.label }}</p>
            <p class="mt-1 text-2xl font-semibold tabular-nums">{{ item.value ?? '—' }}</p>
            <p class="muted mt-1 text-[11px]">{{ item.hint }}</p>
          </div>
        </div>
      </section>

      <div class="grid gap-4 lg:grid-cols-3">
        <section class="panel p-4">
          <div class="mb-3 flex items-center justify-between">
            <h2 class="text-sm font-semibold">最近动态</h2>
            <RouterLink to="/console/inbox" class="text-xs accent">全部</RouterLink>
          </div>
          <div v-if="!inbox.length" class="muted text-sm">还没有事件。</div>
          <ul class="space-y-3">
            <li v-for="item in inbox" :key="item.id" class="flex gap-3 text-sm">
              <span class="mt-1 h-2.5 w-2.5 shrink-0 rounded-full" :class="item.readFlag ? 'bg-[var(--line)]' : 'bg-[var(--accent)]'" />
              <div class="min-w-0">
                <p class="truncate">{{ item.title || item.eventType }}</p>
                <p class="muted text-[11px]">{{ relativeTime(item.createdAt) }}</p>
              </div>
            </li>
          </ul>
        </section>

        <section class="panel p-4">
          <div class="mb-3 flex items-center justify-between">
            <h2 class="text-sm font-semibold">待办清单</h2>
            <RouterLink to="/console/tickets" class="text-xs accent">管理</RouterLink>
          </div>
          <div v-if="!tickets.length" class="muted text-sm">暂无待办。</div>
          <ul class="space-y-2">
            <li v-for="item in tickets" :key="item.id" class="flex items-start gap-2 rounded-xl border border-[var(--line)] px-3 py-2">
              <input
                type="checkbox"
                class="mt-1"
                :checked="item.status === 'RESOLVED' || item.status === 'CLOSED'"
                :disabled="item.status === 'CLOSED'"
                @change="toggleDone(item)"
              />
              <div class="min-w-0 flex-1">
                <p class="truncate text-sm">{{ item.title }}</p>
                <p class="muted font-mono text-[11px]">{{ item.ticketNo }}</p>
              </div>
              <span class="shrink-0 rounded-full px-2 py-0.5 text-[10px]" :class="item.status === 'CREATED' ? 'bg-[#ff6b7a22] text-[var(--danger)]' : 'bg-[var(--accent-dim)] text-[var(--accent)]'">
                {{ statusZh(item.status) }}
              </span>
            </li>
          </ul>
          <button class="btn-ghost mt-3 w-full text-xs" @click="goChat('加个待办：')">+ 添加待办</button>
        </section>

        <section class="panel p-4">
          <h2 class="mb-3 text-sm font-semibold">快捷工具</h2>
          <div class="grid grid-cols-3 gap-2">
            <RouterLink
              v-for="tool in tools"
              :key="tool.label"
              :to="tool.to"
              class="flex flex-col items-center gap-2 rounded-2xl border border-[var(--line)] px-2 py-3 text-center text-[11px] text-[var(--muted)] hover:border-[var(--accent)] hover:text-[var(--text)]"
            >
              <span class="stat-icon text-white" :style="{ background: tool.color }">
                <Icon :name="tool.icon" :size="15" />
              </span>
              {{ tool.label }}
            </RouterLink>
          </div>
        </section>
      </div>

      <section>
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-[var(--muted)]">已上线应用</h2>
          <RouterLink to="/apps" class="text-xs accent">全部</RouterLink>
        </div>
        <div class="grid gap-3 sm:grid-cols-2">
          <a
            v-for="app in LIVE_APPS"
            :key="app.id"
            class="panel flex items-start gap-3 p-4 hover:border-[var(--accent)]"
            :href="app.href"
            target="_blank"
            rel="noreferrer"
          >
            <span class="stat-icon shrink-0 text-white" :style="{ background: app.color }">
              <Icon :name="app.icon" :size="16" />
            </span>
            <div class="min-w-0">
              <p class="font-medium">{{ app.name }}</p>
              <p class="muted mt-1 text-xs">{{ app.hint }}</p>
            </div>
            <Icon name="external" :size="14" class="ml-auto mt-1 shrink-0 text-[var(--muted)]" />
          </a>
        </div>
      </section>

      <section>
        <div class="mb-3 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-[var(--muted)]">知识库模块</h2>
          <RouterLink to="/console/knowledge" class="text-xs accent">打开知识库</RouterLink>
        </div>
        <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <RouterLink
            v-for="mod in modules"
            :key="mod.label"
            to="/console/knowledge"
            class="panel block p-4"
          >
            <span class="stat-icon bg-[#1d2b4d] text-[var(--accent)]"><Icon :name="mod.icon" :size="16" /></span>
            <p class="mt-3 font-medium">{{ mod.label }}</p>
            <p class="muted mt-1 text-xs">{{ mod.count }} 篇文档</p>
          </RouterLink>
        </div>
      </section>
    </div>

    <div class="mt-4 xl:mt-0">
      <ChatPanel layout="dock" />
    </div>
  </div>
</template>
