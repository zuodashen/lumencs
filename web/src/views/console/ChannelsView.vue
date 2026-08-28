<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const url = ref('')
const enabled = ref(true)
const data = ref<any>(null)
const error = ref('')
const notice = ref('')

onMounted(async () => {
  try {
    data.value = await api.channels()
    const webhook = (data.value?.channels || []).find((c: any) => c.type === 'WEBHOOK')
    if (webhook) {
      try {
        url.value = JSON.parse(webhook.configJson || '{}').url || ''
      } catch {
        url.value = ''
      }
      enabled.value = webhook.enabled
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function save() {
  notice.value = ''
  try {
    await api.saveWebhook({ name: 'Webhook', url: url.value, enabled: enabled.value })
    data.value = await api.channels()
    notice.value = '已保存。可把 URL 指到 PanWatch 或其他入站 Webhook。'
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  }
}
</script>

<template>
  <div>
    <h1 class="serif mb-2 text-3xl">通知渠道</h1>
    <p class="muted mb-5 max-w-2xl text-sm leading-relaxed">
      站内事件始终写入。Webhook 用事件 ID 去重，和 PanWatch 的 notify_dedupe 同一思路。不把两个系统做成硬依赖。
    </p>
    <p v-if="error" class="danger mb-3">{{ error }}</p>
    <p v-if="notice" class="accent mb-3 text-sm">{{ notice }}</p>
    <form class="panel mb-6 max-w-xl p-5" @submit.prevent="save">
      <label class="mb-3 block text-sm muted">
        Webhook URL
        <input v-model="url" class="input mt-1" placeholder="https://example.com/hooks/lumencs" />
      </label>
      <label class="mb-4 flex items-center gap-2 text-sm">
        <input v-model="enabled" type="checkbox" />
        启用
      </label>
      <button class="btn-primary">保存渠道</button>
    </form>
    <h2 class="serif mb-3 text-xl">最近投递</h2>
    <pre class="panel overflow-auto p-4 text-xs">{{ JSON.stringify(data?.logs || [], null, 2) }}</pre>
  </div>
</template>
