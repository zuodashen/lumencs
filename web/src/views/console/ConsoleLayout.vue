<script setup lang="ts">
import { useRouter, useRoute, RouterLink, RouterView } from 'vue-router'
import { logoutTokens } from '../../api'

const router = useRouter()
const route = useRoute()
const user = localStorage.getItem('lumencs_user') || 'admin'

const groups = [
  {
    label: '中枢',
    links: [
      { to: '/console/overview', label: '总览' },
      { to: '/console/inbox', label: '事件' },
      { to: '/console/gaps', label: '知识缺口' },
      { to: '/console/channels', label: '通知渠道' },
    ],
  },
  {
    label: '运营',
    links: [
      { to: '/console/knowledge', label: '知识库' },
      { to: '/console/tickets', label: '工单' },
      { to: '/console/reviews', label: '审核' },
      { to: '/console/traces', label: '追踪' },
      { to: '/console/memory', label: '记忆' },
      { to: '/console/tools', label: '工具' },
    ],
  },
]

function logout() {
  logoutTokens()
  router.push('/console/login')
}
</script>

<template>
  <div class="min-h-screen lg:grid lg:grid-cols-[232px_1fr]">
    <aside class="border-b border-[var(--line)] p-5 lg:border-b-0 lg:border-r">
      <p class="text-[11px] uppercase tracking-[0.28em] text-[var(--accent)]">Lumen Hub</p>
      <p class="serif mt-1 text-xl">控制台</p>
      <nav class="mt-6 space-y-5">
        <div v-for="group in groups" :key="group.label">
          <p class="mb-2 text-[10px] uppercase tracking-[0.18em] text-[var(--muted)]">{{ group.label }}</p>
          <div class="flex flex-wrap gap-1 lg:flex-col">
            <RouterLink
              v-for="link in group.links"
              :key="link.to"
              :to="link.to"
              class="rounded-lg px-3 py-2 text-sm"
              :class="route.path === link.to ? 'bg-[var(--accent-dim)] text-[var(--accent)]' : 'text-[var(--muted)]'"
            >
              {{ link.label }}
            </RouterLink>
          </div>
        </div>
        <RouterLink to="/" class="block rounded-lg px-3 py-2 text-sm text-[var(--muted)]">返回对话</RouterLink>
      </nav>
      <button class="mt-8 text-xs text-[var(--muted)]" @click="logout">退出 {{ user }}</button>
    </aside>
    <main class="p-6">
      <RouterView />
    </main>
  </div>
</template>
