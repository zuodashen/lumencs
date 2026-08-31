<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { logoutTokens } from '../api'
import Icon from './Icon.vue'
import { greeting } from '../status'

const route = useRoute()
const router = useRouter()
const user = localStorage.getItem('lumencs_user') || 'admin'
const now = ref(new Date())
let timer: number | null = null

const groups = [
  {
    label: '日常',
    links: [
      { to: '/', label: '总览', icon: 'grid', exact: true },
      { to: '/chat', label: '对话', icon: 'chat' },
      { to: '/console/knowledge', label: '知识库', icon: 'book' },
      { to: '/console/tickets', label: '待办', icon: 'check' },
      { to: '/playbook', label: '场景', icon: 'spark' },
      { to: '/apps', label: '已上线', icon: 'chart' },
    ],
  },
  {
    label: '中枢',
    links: [
      { to: '/console/inbox', label: '事件', icon: 'inbox' },
      { to: '/console/gaps', label: '知识缺口', icon: 'gap' },
      { to: '/console/channels', label: '通知渠道', icon: 'bell' },
      { to: '/console/reviews', label: '安全审核', icon: 'shield' },
    ],
  },
  {
    label: '排障',
    links: [
      { to: '/console/traces', label: '追踪', icon: 'activity' },
      { to: '/console/memory', label: '记忆', icon: 'brain' },
      { to: '/console/tools', label: '工具', icon: 'wrench' },
    ],
  },
]

const clock = computed(() =>
  now.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }),
)
const dateLabel = computed(() =>
  now.value.toLocaleDateString('zh-CN', { weekday: 'long', month: 'long', day: 'numeric' }),
)
const hello = computed(() => `${greeting(now.value.getHours())}，${user}`)
const night = computed(() => now.value.getHours() < 6 || now.value.getHours() >= 18)
const page = computed(() => {
  const path = route.path
  const map: Record<string, { title: string; sub: string }> = {
    '/': { title: hello.value, sub: '笔记、待办、博客草稿' },
    '/chat': { title: '对话', sub: '问笔记、记一笔、待办、写博客草稿' },
    '/apps': { title: '已上线', sub: '盯盘侠与微光博客，新窗口打开' },
    '/playbook': { title: '场景与工具', sub: '能问什么、用哪条工具，点卡片看例句' },
    '/console/knowledge': { title: '知识库', sub: '上传、切分、召回测试、博客同步' },
    '/console/tickets': { title: '待办', sub: '改状态走状态机，也可在对话里改' },
    '/console/inbox': { title: '事件', sub: '待办、审核、超时都会落到这里' },
    '/console/gaps': { title: '知识缺口', sub: '差评与无引用回复，可生成 FAQ 草稿' },
    '/console/channels': { title: '通知渠道', sub: 'Webhook，同一事件只投一次' },
    '/console/reviews': { title: '安全审核', sub: '规则 + 模型，通过后写回原会话' },
    '/console/traces': { title: '追踪', sub: '这一轮各 Agent 的 span' },
    '/console/memory': { title: '记忆', sub: '短期对话、办事槽位、长期画像' },
    '/console/tools': { title: '工具', sub: '注册表与调用日志' },
  }
  return map[path] || { title: hello.value, sub: '个人 AI 服务中枢' }
})
const onChat = computed(() => route.path === '/' || route.path === '/chat')

function active(to: string, exact?: boolean) {
  if (exact) return route.path === to
  return route.path === to || route.path.startsWith(`${to}/`)
}

function logout() {
  logoutTokens()
  router.push('/console/login')
}

function newChat() {
  router.push('/chat')
}

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date()
  }, 30000)
})
onUnmounted(() => {
  if (timer) window.clearInterval(timer)
})
</script>

<template>
  <div class="min-h-screen lg:grid lg:grid-cols-[232px_minmax(0,1fr)]">
    <aside class="flex flex-col border-b border-[var(--line)] p-5 lg:min-h-screen lg:border-b-0 lg:border-r">
      <div class="flex items-center gap-3">
        <span class="stat-icon bg-gradient-to-br from-[#5b8def] to-[#8b6cff] text-white">
          <Icon name="spark" :size="16" />
        </span>
        <div>
          <p class="text-[11px] uppercase tracking-[0.28em] text-[var(--accent)]">Lumen Hub</p>
          <p class="text-lg font-semibold">个人中枢</p>
        </div>
      </div>
      <nav class="mt-6 flex-1 space-y-5">
        <div v-for="group in groups" :key="group.label">
          <p class="mb-2 text-[10px] uppercase tracking-[0.18em] text-[var(--muted)]">{{ group.label }}</p>
          <div class="flex flex-wrap gap-1 lg:flex-col">
            <RouterLink
              v-for="link in group.links"
              :key="link.label + link.to"
              :to="link.to"
              class="flex items-center gap-2 rounded-xl px-3 py-2 text-sm"
              :class="active(link.to, link.exact) ? 'nav-active' : 'text-[var(--muted)] hover:text-[var(--text)]'"
            >
              <Icon :name="link.icon" :size="16" />
              {{ link.label }}
            </RouterLink>
          </div>
        </div>
      </nav>
      <div class="mt-6 flex items-center gap-3 rounded-2xl border border-[var(--line)] p-3">
        <span class="stat-icon bg-[#1d2b4d] text-[var(--accent)]"><Icon name="user" :size="16" /></span>
        <div class="min-w-0">
          <p class="truncate text-sm font-medium">{{ user }}</p>
          <p class="text-[11px] text-[var(--muted)]">管理员</p>
        </div>
      </div>
    </aside>

    <div class="flex min-h-screen flex-col">
      <header class="flex flex-wrap items-center justify-between gap-3 px-5 py-4">
        <div>
          <h1 class="text-xl font-semibold sm:text-2xl">{{ page.title }}</h1>
          <p class="muted mt-0.5 text-sm">{{ page.sub }}</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <div class="panel flex items-center gap-2 px-3 py-2 text-sm">
            <Icon :name="night ? 'moon' : 'sun'" :size="16" class="text-[var(--warn)]" />
            <span class="font-semibold tabular-nums">{{ clock }}</span>
            <span class="muted text-xs">{{ dateLabel }}</span>
          </div>
          <button v-if="onChat" class="btn-ghost" @click="newChat">新会话</button>
          <button class="btn-ghost inline-flex items-center gap-1" @click="logout">
            <Icon name="logout" :size="14" />
            退出
          </button>
        </div>
      </header>
      <main class="flex-1 px-4 pb-6 sm:px-5">
        <RouterView />
      </main>
    </div>
  </div>
</template>
