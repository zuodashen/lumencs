<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'

const docs = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 9
const title = ref('')
const source = ref('')
const content = ref('')
const error = ref('')
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const fileName = ref('')
const selected = ref<any | null>(null)
const detailChunks = ref<any[]>([])
const detailContent = ref('')
const collapseWhitespace = ref(true)
const paragraphSplit = ref(true)
const parentMax = ref(500)
const childMax = ref(200)
const preview = ref<string[]>([])
const recallQuery = ref('')
const recallHits = ref<any[]>([])
const tab = ref<'upload' | 'recall'>('upload')
const syncEnabled = ref(true)
const syncCron = ref('')
const blogConfigured = ref(true)

const maxPage = () => Math.max(1, Math.ceil(total.value / pageSize))

function statusLabel(status: string) {
  if (status === 'READY') return '可用'
  if (status === 'KEYWORD_ONLY') return '仅关键词'
  if (status === 'INDEXING') return '索引中'
  return status || '—'
}

async function load() {
  const data = (await api.documents(pageNum.value, pageSize)) as Page<any>
  docs.value = data.records
  total.value = data.total
}

onMounted(async () => {
  try {
    await load()
    const settings = await api.blogSettings() as { syncEnabled?: boolean; syncCron?: string; blogConfigured?: boolean }
    syncEnabled.value = settings.syncEnabled !== false
    syncCron.value = settings.syncCron || ''
    blogConfigured.value = settings.blogConfigured !== false
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

async function openDoc(doc: any) {
  error.value = ''
  try {
    const data = await api.documentDetail(doc.id)
    selected.value = data.document
    detailContent.value = data.content || ''
    detailChunks.value = data.chunks || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function removeDoc(id: number) {
  if (!confirm('删除后不可恢复，向量也会清掉。确定？')) return
  error.value = ''
  saving.value = true
  try {
    await api.deleteDocument(id)
    if (selected.value?.id === id) {
      selected.value = null
      detailChunks.value = []
      detailContent.value = ''
    }
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '删除失败'
  } finally {
    saving.value = false
  }
}

async function toggleSync() {
  error.value = ''
  saving.value = true
  try {
    const settings = await api.updateBlogSettings({ syncEnabled: !syncEnabled.value }) as { syncEnabled?: boolean }
    syncEnabled.value = settings.syncEnabled !== false
  } catch (e) {
    error.value = e instanceof Error ? e.message : '更新失败'
  } finally {
    saving.value = false
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
    fileName.value = file.name
  }
  reader.readAsText(file, 'UTF-8')
}

async function runPreview() {
  error.value = ''
  try {
    const data = await api.previewChunks({
      content: content.value,
      collapseWhitespace: collapseWhitespace.value,
      paragraphSplit: paragraphSplit.value,
      parentMax: parentMax.value,
      childMax: childMax.value,
    })
    preview.value = data.chunks || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '预览失败'
  }
}

async function runRecall() {
  error.value = ''
  try {
    recallHits.value = (await api.recallTest({
      query: recallQuery.value,
      documentId: selected.value?.id,
    })) || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '召回失败'
  }
}

async function submit() {
  error.value = ''
  saving.value = true
  try {
    await api.createDocument({
      title: title.value,
      source: source.value,
      content: content.value,
      collapseWhitespace: collapseWhitespace.value,
      paragraphSplit: paragraphSplit.value,
      parentMax: parentMax.value,
      childMax: childMax.value,
    })
    title.value = ''
    source.value = ''
    content.value = ''
    preview.value = []
    fileName.value = ''
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
  <div class="space-y-6">
    <div>
      <h1 class="serif mb-2 text-3xl">知识库</h1>
      <p class="muted max-w-3xl text-sm leading-relaxed">上传文本或从博客同步，切分后写入向量库。</p>
    </div>

    <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
      <article v-for="doc in docs" :key="doc.id" class="panel cursor-pointer p-4" @click="openDoc(doc)">
        <p class="font-medium">{{ doc.title }}</p>
        <p class="muted mt-2 text-xs">
          {{ doc.chunkCount || 0 }} 切块 · {{ doc.charCount || 0 }} 字符 · {{ statusLabel(doc.status) }}
        </p>
        <p class="muted mt-1 truncate text-xs">{{ doc.source }}</p>
      </article>
      <p v-if="!docs.length" class="muted text-sm">暂无文档。右侧上传，或在对话里说「帮我记一下」。</p>
    </div>
    <div class="muted flex items-center gap-3 text-xs">
      <button class="btn-ghost" :disabled="pageNum <= 1" @click="go(pageNum - 1)">上一页</button>
      <span>第 {{ pageNum }} / {{ maxPage() }} 页 · 共 {{ total }} 篇</span>
      <button class="btn-ghost" :disabled="pageNum >= maxPage()" @click="go(pageNum + 1)">下一页</button>
    </div>

    <div v-if="selected" class="panel p-4">
      <div class="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p class="text-xs accent">文档</p>
          <h2 class="serif text-2xl">{{ selected.title }}</h2>
          <p class="muted mt-1 text-xs">{{ selected.source }} · {{ statusLabel(selected.status) }} · {{ detailChunks.length }} 块</p>
        </div>
        <button class="btn-ghost text-xs" :disabled="saving" @click="removeDoc(selected.id)">删除</button>
      </div>
      <div class="mt-4 max-h-64 space-y-2 overflow-y-auto">
        <article v-for="chunk in detailChunks" :key="chunk.id" class="rounded-xl border border-[var(--line)] p-3 text-xs">
          <p class="muted">#{{ chunk.sortOrder }} · {{ chunk.charCount }} 字符</p>
          <p class="mt-1 whitespace-pre-wrap leading-relaxed">{{ chunk.content }}</p>
        </article>
      </div>
    </div>

    <form class="panel max-w-2xl p-4" @submit.prevent="submit">
        <div class="mb-3 flex gap-2 text-sm">
          <button type="button" class="chip" :class="tab === 'upload' ? 'border-[var(--accent)] text-[var(--accent)]' : ''" @click="tab = 'upload'">导入</button>
          <button type="button" class="chip" :class="tab === 'recall' ? 'border-[var(--accent)] text-[var(--accent)]' : ''" @click="tab = 'recall'">召回测试</button>
        </div>

        <template v-if="tab === 'upload'">
          <p class="muted mb-3 text-sm">粘贴正文，或选择本地文本文件。</p>
          <input
            ref="fileInput"
            type="file"
            accept=".txt,.md,.markdown,.csv,.json,.log,text/plain"
            class="hidden"
            @change="onFile"
          />
          <div class="mb-3 flex flex-wrap items-center gap-2">
            <button type="button" class="btn-primary" @click="fileInput?.click()">选择文件</button>
            <span class="muted text-sm">{{ fileName || '支持 .txt / .md / .csv / .json' }}</span>
          </div>
          <input v-model="title" class="input mb-3" placeholder="标题" />
          <input v-model="source" class="input mb-3" placeholder="来源，如 notes.md" />
          <textarea v-model="content" rows="8" class="input mb-3" placeholder="正文" />
          <label class="muted mb-2 flex items-center gap-2 text-xs">
            <input v-model="collapseWhitespace" type="checkbox" /> 压缩连续空格 / 空行
          </label>
          <label class="muted mb-3 flex items-center gap-2 text-xs">
            <input v-model="paragraphSplit" type="checkbox" /> 按空行切父段（检索用更短的子段）
          </label>
          <div class="mb-3 grid grid-cols-2 gap-2">
            <label class="muted text-xs">父段最大字符
              <input v-model.number="parentMax" class="input mt-1" type="number" min="80" max="4000" />
            </label>
            <label class="muted text-xs">子段最大字符
              <input v-model.number="childMax" class="input mt-1" type="number" min="40" max="2000" />
            </label>
          </div>
          <p v-if="error" class="danger mb-3 text-sm">{{ error }}</p>
          <div class="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--line)] px-3 py-2">
            <div>
              <p class="text-sm">定时同步博客</p>
              <p class="muted text-xs">
                {{ blogConfigured ? (syncCron ? `Cron：${syncCron}` : '已配置博客地址') : '还没配 BLOG_BASE_URL' }}
                。关掉后只保留手动同步和对话里同步单篇。
              </p>
            </div>
            <button type="button" class="chip" :disabled="saving || !blogConfigured" @click="toggleSync">
              {{ syncEnabled ? '已开启' : '已关闭' }}
            </button>
          </div>
          <button class="btn-primary" :disabled="saving">写入并向量化</button>
          <button type="button" class="btn-ghost ml-2" :disabled="saving" @click="runPreview">预览切块</button>
          <button type="button" class="btn-ghost ml-2" :disabled="saving" @click="syncBlog">从博客同步</button>
          <button type="button" class="btn-ghost ml-2" :disabled="saving" @click="reindex">重新向量化</button>
          <div v-if="preview.length" class="mt-4 max-h-48 space-y-2 overflow-y-auto">
            <p class="muted text-xs">将生成 {{ preview.length }} 个检索块</p>
            <p v-for="(item, idx) in preview" :key="idx" class="rounded-lg bg-[var(--bg)] p-2 text-xs">{{ idx + 1 }}. {{ item }}</p>
          </div>
        </template>

        <template v-else>
          <p class="muted mb-3 text-sm">输入一句用户可能会问的话，看知识库会召回哪些块。若已打开某篇文档，只测这一篇。</p>
          <input v-model="recallQuery" class="input mb-3" placeholder="例如：我常喝什么" />
          <button type="button" class="btn-primary" @click="runRecall">测试召回</button>
          <p v-if="error" class="danger mt-3 text-sm">{{ error }}</p>
          <article v-for="hit in recallHits" :key="hit.id" class="mt-3 rounded-xl border border-[var(--line)] p-3 text-xs">
            <p class="accent">{{ hit.source }} · {{ Number(hit.score).toFixed(3) }}</p>
            <p class="mt-1 whitespace-pre-wrap">{{ hit.content }}</p>
          </article>
          <p v-if="recallQuery && !recallHits.length" class="muted mt-3 text-xs">没有命中。试着换问法，或先导入文档。</p>
        </template>
      </form>
  </div>
</template>
