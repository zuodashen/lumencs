<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'

const health = ref<Record<string, unknown> | null>(null)
const ticketTotal = ref(0)
const docTotal = ref(0)
const error = ref('')

onMounted(async () => {
  try {
    const h = await api.health()
    health.value = h.data || h
    const tickets = (await api.tickets({ pageNum: 1, pageSize: 1 })) as Page<any>
    ticketTotal.value = tickets.total
    const docs = (await api.documents(1, 1)) as Page<any>
    docTotal.value = docs.total
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})
</script>

<template>
  <div>
    <h1 class="mb-4 text-xl font-semibold">总览</h1>
    <p v-if="error" class="text-[#f07178]">{{ error }}</p>
    <div class="grid gap-4 sm:grid-cols-3">
      <div class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <p class="text-xs text-[#8b9bb8]">知识文档</p>
        <p class="mt-2 text-2xl">{{ docTotal }}</p>
      </div>
      <div class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <p class="text-xs text-[#8b9bb8]">工单</p>
        <p class="mt-2 text-2xl">{{ ticketTotal }}</p>
      </div>
      <div class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <p class="text-xs text-[#8b9bb8]">RAG sidecar</p>
        <p class="mt-2 text-2xl">{{ String(health?.rag || '-') }}</p>
      </div>
    </div>
  </div>
</template>
