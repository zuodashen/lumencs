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
    <h1 class="serif mb-2 text-3xl">服务中枢</h1>
    <p class="muted mb-6 max-w-2xl text-sm leading-relaxed">
      对话编排在 LumenCS，知识来自博客同步；确认卡片后也可把草稿/书签写回 LightDiary 管理 API。三个仓库互不合并，不直连博客数据库。
    </p>
    <p v-if="error" class="danger mb-4">{{ error }}</p>
    <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <div v-for="item in [
        ['知识文档', data.documents],
        ['工单', data.tickets],
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
        <p class="accent text-sm">质量飞轮</p>
        <p class="mt-2 text-sm leading-relaxed text-[var(--text)]/80">低分与无引用会话进入缺口清单，一键起草 FAQ，再同步回知识库。</p>
      </RouterLink>
      <RouterLink to="/console/channels" class="panel block p-5">
        <p class="accent text-sm">多通道通知</p>
        <p class="mt-2 text-sm leading-relaxed text-[var(--text)]/80">工单、HITL、SLA 写入站内事件，并可 Webhook 出去。幂等按事件 ID 去重。</p>
      </RouterLink>
      <RouterLink to="/console/knowledge" class="panel block p-5">
        <p class="accent text-sm">聊这篇</p>
        <p class="mt-2 text-sm leading-relaxed text-[var(--text)]/80">博客同步已拉正文。正文页用 /embed?slug= 即可把检索锁在单文。</p>
      </RouterLink>
    </div>
  </div>
</template>
