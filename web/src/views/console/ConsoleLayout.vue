<script setup lang="ts">
import { useRouter, useRoute, RouterLink, RouterView } from 'vue-router'
import { logoutTokens } from '../../api'

const router = useRouter()
const route = useRoute()
const user = localStorage.getItem('lumencs_user') || 'admin'

const links = [
  { to: '/console/overview', label: '总览' },
  { to: '/console/knowledge', label: '知识库' },
  { to: '/console/tickets', label: '工单' },
  { to: '/console/reviews', label: '审核收件箱' },
  { to: '/console/traces', label: '追踪' },
  { to: '/console/memory', label: '记忆' },
  { to: '/console/tools', label: 'MCP 工具' },
]

function logout() {
  logoutTokens()
  router.push('/console/login')
}
</script>

<template>
  <div class="min-h-screen lg:grid lg:grid-cols-[220px_1fr]">
    <aside class="border-b border-[#243049] p-4 lg:border-r lg:border-b-0">
      <p class="text-xs tracking-[0.2em] text-[#8b9bb8] uppercase">LumenCS</p>
      <p class="mt-1 mb-6 font-semibold">运营控制台</p>
      <nav class="flex flex-wrap gap-2 lg:flex-col">
        <RouterLink
          v-for="link in links"
          :key="link.to"
          :to="link.to"
          class="rounded-md px-3 py-2 text-sm"
          :class="route.path === link.to ? 'bg-[#1c2a44] text-[#3dd6c6]' : 'text-[#8b9bb8]'"
        >
          {{ link.label }}
        </RouterLink>
        <RouterLink to="/" class="rounded-md px-3 py-2 text-sm text-[#8b9bb8]">返回聊天</RouterLink>
      </nav>
      <button class="mt-6 text-xs text-[#8b9bb8]" @click="logout">退出 {{ user }}</button>
    </aside>
    <main class="p-5">
      <RouterView />
    </main>
  </div>
</template>
