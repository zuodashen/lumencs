<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const data = ref<any>(null)
const error = ref('')

onMounted(async () => {
  try {
    data.value = await api.tools()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})
</script>

<template>
  <div>
    <h1 class="mb-2 text-xl font-semibold">MCP 工具</h1>
    <p class="mb-4 text-sm text-[#8b9bb8]">办事流程收集完槽位后，会真实调用这些 handler（工单 / 知识 / 博客 / 演示订单）。</p>
    <p v-if="error" class="text-[#f07178]">{{ error }}</p>
    <p class="mb-4 text-xs text-[#8b9bb8]">博客同步：{{ data?.blogEnabled ? '已配置 BLOG_BASE_URL' : '未配置（博客仓先不改，配上公开 API 即可检索）' }}</p>
    <div class="mb-6 grid gap-3 sm:grid-cols-2">
      <div v-for="tool in data?.tools || []" :key="tool.name" class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <p class="font-medium text-[#3dd6c6]">{{ tool.name }}</p>
        <p class="mt-1 text-xs text-[#8b9bb8]">{{ tool.description }}</p>
        <p class="mt-2 text-xs">params: {{ (tool.params || []).join(', ') }}</p>
      </div>
    </div>
    <h2 class="mb-2 text-sm font-semibold">最近调用</h2>
    <pre class="overflow-auto rounded-xl border border-[#243049] bg-[#121a2b] p-4 text-xs">{{ JSON.stringify(data?.recentLogs || [], null, 2) }}</pre>
  </div>
</template>
