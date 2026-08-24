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

async function submit() {
  error.value = ''
  saving.value = true
  try {
    await api.createDocument({ title: title.value, source: source.value, content: content.value })
    title.value = ''
    source.value = ''
    content.value = ''
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
      <h1 class="mb-4 text-xl font-semibold">知识库</h1>
      <div class="space-y-3">
        <article v-for="doc in docs" :key="doc.id" class="rounded-xl border border-[#243049] bg-[#121a2b] p-4">
          <p class="font-medium">{{ doc.title }}</p>
          <p class="mt-1 text-xs text-[#8b9bb8]">{{ doc.source }} · {{ doc.status }} · {{ doc.chunkCount }} chunks</p>
        </article>
        <p v-if="!docs.length" class="text-sm text-[#8b9bb8]">暂无文档</p>
        <div class="flex items-center gap-3 pt-1 text-xs text-[#8b9bb8]">
          <button class="rounded border border-[#243049] px-3 py-1" :disabled="pageNum <= 1" @click="go(pageNum - 1)">
            上一页
          </button>
          <span>第 {{ pageNum }} / {{ maxPage() }} 页 · 共 {{ total }} 篇</span>
          <button class="rounded border border-[#243049] px-3 py-1" :disabled="pageNum >= maxPage()" @click="go(pageNum + 1)">
            下一页
          </button>
        </div>
      </div>
    </section>
    <form class="rounded-xl border border-[#243049] bg-[#121a2b] p-4" @submit.prevent="submit">
      <h2 class="mb-3 font-medium">新增文档</h2>
      <input v-model="title" class="mb-3 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm" placeholder="标题" />
      <input v-model="source" class="mb-3 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm" placeholder="来源，如 policy.md" />
      <textarea v-model="content" rows="10" class="mb-3 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm" placeholder="正文" />
      <p v-if="error" class="mb-3 text-sm text-[#f07178]">{{ error }}</p>
      <button class="rounded-lg bg-[#3dd6c6] px-4 py-2 text-sm font-medium text-[#0b1220]" :disabled="saving">写入并向量化</button>
      <button type="button" class="ml-2 rounded-lg border border-[#243049] px-4 py-2 text-sm" @click="syncBlog">从博客同步</button>
    </form>
  </div>
</template>
