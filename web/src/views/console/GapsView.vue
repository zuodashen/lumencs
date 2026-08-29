<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api'

const gaps = ref<any[]>([])
const error = ref('')
const draft = ref('')
const busy = ref(false)

onMounted(async () => {
  try {
    gaps.value = (await api.gaps()) || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function generate(item: any) {
  busy.value = true
  draft.value = ''
  error.value = ''
  try {
    const data = await api.faqDraft({ sessionId: item.sessionId, messageId: item.messageId })
    draft.value = data.markdown || ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '生成失败'
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-[1.1fr_0.9fr]">
    <section>
      <h1 class="serif mb-2 text-3xl">知识缺口</h1>
      <p class="muted mb-5 text-sm">差评和没有引用的回复会出现在这里。</p>
      <p v-if="error" class="danger mb-3">{{ error }}</p>
      <article v-for="item in gaps" :key="item.messageId" class="panel mb-3 p-4">
        <p class="text-xs accent">{{ item.kind }} · {{ item.intent || '—' }}</p>
        <p class="mt-2 text-sm whitespace-pre-wrap">{{ item.content }}</p>
        <button class="btn-ghost mt-3" :disabled="busy" @click="generate(item)">生成 FAQ 草稿</button>
      </article>
      <p v-if="!gaps.length" class="muted text-sm">暂无缺口。给助手回复点「缺口」，或问一个知识库没有的问题。</p>
    </section>
    <section class="panel p-4 h-fit">
      <h2 class="serif text-xl">草稿</h2>
      <p class="muted mt-1 text-xs">生成的是 Markdown 文本，请复制后自己贴进知识库或博客。系统不会自动发布。</p>
      <pre class="mt-4 whitespace-pre-wrap text-sm leading-relaxed">{{ draft || '选一条缺口生成。' }}</pre>
    </section>
  </div>
</template>
