<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../../api'

const data = ref<Record<string, unknown>>({})
const error = ref('')

onMounted(async () => {
  try {
    data.value = (await api.hubOverview()) || {}
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})
</script>

<template>
  <div>
    <p class="text-[11px] uppercase tracking-[0.22em] text-[var(--accent)]">Overview</p>
    <h1 class="serif mb-6 text-3xl">服务中枢</h1>
    <p v-if="error" class="danger mb-4">{{ error }}</p>
    <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="item in [
        ['知识文档', data.documents],
        ['待办', data.tickets],
        ['待审核', data.pendingReviews],
        ['未读事件', data.unreadInbox],
        ['差评缺口', data.csatDown],
        ['RAG', data.rag],
        ['Redis', data.redis],
        ['消息', data.messages],
      ]" :key="item[0] as string" class="panel p-4">
        <p class="text-xs text-[var(--muted)]">{{ item[0] }}</p>
        <p class="serif mt-2 text-3xl">{{ item[1] ?? '—' }}</p>
      </div>
    </div>
    <div class="mt-6 grid gap-3 md:grid-cols-3">
      <RouterLink to="/console/gaps" class="panel block p-5">
        <p class="accent text-sm">知识缺口</p>
      </RouterLink>
      <RouterLink to="/console/channels" class="panel block p-5">
        <p class="accent text-sm">通知渠道</p>
      </RouterLink>
      <RouterLink to="/console/knowledge" class="panel block p-5">
        <p class="accent text-sm">知识库</p>
      </RouterLink>
    </div>
  </div>
</template>
