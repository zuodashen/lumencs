<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const sessions = ref<any[]>([])
const spans = ref<any[]>([])
const current = ref('')
const error = ref('')

onMounted(async () => {
  try {
    sessions.value = await api.sessions()
    if (sessions.value[0]) {
      await load(sessions.value[0].id)
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function load(id: string) {
  current.value = id
  spans.value = await api.traces(id)
}
</script>

<template>
  <div class="grid gap-4 lg:grid-cols-[240px_1fr]">
    <section>
      <h1 class="serif mb-3 text-3xl">追踪</h1>
      <p v-if="error" class="text-sm text-[#f07178]">{{ error }}</p>
      <button
        v-for="item in sessions"
        :key="item.id"
        class="mb-2 block w-full rounded-lg border px-3 py-2 text-left text-xs"
        :class="current === item.id ? 'border-[#3dd6c6] bg-[#121a2b]' : 'border-[#243049]'"
        @click="load(item.id)"
      >
        {{ item.id.slice(0, 8) }} · {{ item.userLabel }}
      </button>
    </section>
    <section class="space-y-2">
      <article v-for="span in spans" :key="span.id" class="rounded-xl border border-[#243049] bg-[#121a2b] p-3 text-sm">
        <p class="text-[#3dd6c6]">{{ span.agent }}.{{ span.method }}</p>
        <p class="text-xs text-[#8b9bb8]">{{ span.status }} · {{ span.durationMs }}ms</p>
        <pre v-if="span.detailJson" class="mt-2 overflow-x-auto text-xs text-[#8b9bb8]">{{ span.detailJson }}</pre>
      </article>
      <p v-if="!spans.length" class="text-sm text-[#8b9bb8]">选择左侧会话查看 Agent Span。</p>
    </section>
  </div>
</template>
