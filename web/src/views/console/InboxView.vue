<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const items = ref<any[]>([])
const error = ref('')

async function load() {
  items.value = (await api.inbox()) || []
}

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function read(id: number) {
  await api.markInboxRead(id)
  await load()
}
</script>

<template>
  <div>
    <h1 class="serif mb-2 text-3xl">事件</h1>
    <p class="muted mb-5 text-sm">工单、审核、SLA 超时都先落在这里，再按渠道向外推。</p>
    <p v-if="error" class="danger">{{ error }}</p>
    <div v-if="!items.length" class="panel p-5 text-sm muted">还没有事件。创建工单或触发 HITL 后会出现。</div>
    <article v-for="item in items" :key="item.id" class="panel mb-3 p-4">
      <div class="flex items-start justify-between gap-3">
        <div>
          <p class="text-xs accent">{{ item.eventType }}</p>
          <p class="mt-1 font-medium">{{ item.title }}</p>
          <p class="muted mt-1 text-sm whitespace-pre-wrap">{{ item.body }}</p>
        </div>
        <button v-if="!item.readFlag" class="btn-ghost" @click="read(item.id)">标为已读</button>
      </div>
    </article>
  </div>
</template>
