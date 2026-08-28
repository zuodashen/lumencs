<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const sessionId = ref(localStorage.getItem('lumencs_session') || '')
const data = ref<any>(null)
const error = ref('')

async function load() {
  if (!sessionId.value) {
    error.value = '还没有会话 ID，先去聊天台发一条消息。'
    return
  }
  data.value = await api.memory(sessionId.value)
}

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})
</script>

<template>
  <div>
    <h1 class="serif mb-2 text-3xl">记忆</h1>
    <p class="mb-4 text-sm text-[#8b9bb8]">工作槽位、短期对话、长期画像（口味/工位预填）+ 知识库。</p>
    <div class="mb-4 flex gap-2">
      <input v-model="sessionId" class="flex-1 rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm" placeholder="sessionId" />
      <button class="rounded-lg bg-[#3dd6c6] px-4 text-sm text-[#0b1220]" @click="load">刷新</button>
    </div>
    <p v-if="error" class="text-[#f07178]">{{ error }}</p>
    <div v-if="data" class="grid gap-4 lg:grid-cols-3">
      <section class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <h2 class="text-sm font-semibold text-[#3dd6c6]">工作记忆</h2>
        <p class="mt-1 text-xs text-[#8b9bb8]">{{ data.working?.desc }}</p>
        <pre class="mt-3 overflow-auto text-xs text-[#c5d0e6]">{{ JSON.stringify(data.working?.data, null, 2) }}</pre>
      </section>
      <section class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <h2 class="text-sm font-semibold text-[#3dd6c6]">短期记忆</h2>
        <p class="mt-1 text-xs text-[#8b9bb8]">{{ data.shortTerm?.desc }}</p>
        <pre class="mt-3 max-h-80 overflow-auto text-xs text-[#c5d0e6]">{{ JSON.stringify(data.shortTerm?.data, null, 2) }}</pre>
      </section>
      <section class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
        <h2 class="text-sm font-semibold text-[#3dd6c6]">长期记忆</h2>
        <p class="mt-1 text-xs text-[#8b9bb8]">{{ data.longTerm?.desc }}</p>
        <p class="mt-3 text-2xl">{{ data.longTerm?.documentCount ?? 0 }} 篇文档</p>
        <pre class="mt-3 max-h-64 overflow-auto text-xs text-[#c5d0e6]">{{ JSON.stringify(data.longTerm?.profile || {}, null, 2) }}</pre>
      </section>
    </div>
  </div>
</template>
