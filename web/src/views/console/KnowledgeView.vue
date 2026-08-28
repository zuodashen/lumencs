<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'

const docs = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const title = ref('')
const source = ref('')
const content = ref('')
const error = ref('')
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const maxPage = () => Math.max(1, Math.ceil(total.value / pageSize))

async function load() {
  const data = (await api.documents(pageNum.value, pageSize)) as Page<any>
  docs.value = data.records
  total.value = data.total
}

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function go(p: number) {
  if (p < 1 || p > maxPage()) return
  pageNum.value = p
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function syncBlog() {
  error.value = ''
  saving.value = true
  try {
    await api.syncBlog()
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '同步失败'
  } finally {
    saving.value = false
  }
}

async function reindex() {
  error.value = ''
  saving.value = true
  try {
    const data = await api.reindexKnowledge()
    await load()
    error.value = data?.reindexed != null ? `已重新向量化 ${data.reindexed} 篇` : ''
  } catch (e) {
    error.value = e instanceof Error ? e.message : '重新向量化失败'
  } finally {
    saving.value = false
  }
}

function onFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  const ext = (file.name.split('.').pop() || '').toLowerCase()
  if (!['txt', 'md', 'markdown', 'csv', 'json', 'log'].includes(ext)) {
    error.value = '目前只解析纯文本：.txt / .md / .csv / .json（PDF、Word 尚未接入）'
    input.value = ''
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    error.value = '文件请小于 2MB'
    input.value = ''
    return
  }
  error.value = ''
  const reader = new FileReader()
  reader.onload = () => {
    content.value = String(reader.result || '')
    if (!title.value) title.value = file.name.replace(/\.[^.]+$/, '')
    if (!source.value) source.value = file.name
  }
  reader.readAsText(file, 'UTF-8')
}

async function submit() {
  error.value = ''
  saving.value = true
  try {
    await api.createDocument({ title: title.value, source: source.value, content: content.value })
    title.value = ''
    source.value = ''
    content.value = ''
    if (fileInput.value) fileInput.value = ''
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-[1.1fr_1fr]">
    <section>
      <h1 class="serif mb-4 text-3xl">知识库</h1>
      <div class="space-y-3">
        <article v-for="doc in docs" :key="doc.id" class="panel p-4">
          <p class="font-medium">{{ doc.title }}</p>
          <p class="muted mt-1 text-xs">{{ doc.source }} · {{ doc.status }} · {{ doc.chunkCount }} chunks</p>
        </article>
        <p v-if="!docs.length" class="muted text-sm">暂无文档</p>
        <div class="muted flex items-center gap-3 pt-1 text-xs">
          <button class="btn-ghost" :disabled="pageNum <= 1" @click="go(pageNum - 1)">上一页</button>
          <span>第 {{ pageNum }} / {{ maxPage() }} 页 · 共 {{ total }} 篇</span>
          <button class="btn-ghost" :disabled="pageNum >= maxPage()" @click="go(pageNum + 1)">下一页</button>
        </div>
      </div>
    </section>
    <form class="panel p-4" @submit.prevent="submit">
      <h2 class="serif mb-3 text-xl">新增文档</h2>
      <p class="muted mb-3 text-sm leading-relaxed">
        粘贴正文，或上传纯文本文件。浏览器读成文字后走同一条「切分 → 向量化」链路。PDF / Word 暂不解析。
      </p>
      <input
        ref="fileInput"
        type="file"
        accept=".txt,.md,.markdown,.csv,.json,.log,text/plain"
        class="mb-3 block w-full text-sm text-[var(--muted)]"
        @change="onFile"
      />
      <input v-model="title" class="input mb-3" placeholder="标题" />
      <input v-model="source" class="input mb-3" placeholder="来源，如 policy.md" />
      <textarea v-model="content" rows="10" class="input mb-3" placeholder="正文" />
      <p v-if="error" class="danger mb-3 text-sm">{{ error }}</p>
      <button class="btn-primary" :disabled="saving">写入并向量化</button>
      <button type="button" class="btn-ghost ml-2" :disabled="saving" @click="syncBlog">从博客同步正文</button>
      <button type="button" class="btn-ghost ml-2" :disabled="saving" @click="reindex">重新向量化已有文档</button>
    </form>
  </div>
</template>
