<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'

type ReviewItem = {
  id: number
  sessionId: string
  originalContent: string
  intent?: string
  violations?: string[]
  status: string
  reviewNote?: string
  reviewedBy?: string
  createdAt: string
  reviewedAt?: string
}

const reviews = ref<ReviewItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const error = ref('')
const notice = ref('')
const filter = ref('')
const noteInput = ref<Record<number, string>>({})

const maxPage = () => Math.max(1, Math.ceil(total.value / pageSize))

async function load() {
  const data = (await api.reviews({
    status: filter.value || undefined,
    pageNum: pageNum.value,
    pageSize,
  })) as Page<ReviewItem>
  reviews.value = data.records
  total.value = data.total
}

onMounted(async () => {
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
})

async function applyFilter() {
  pageNum.value = 1
  error.value = ''
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function go(p: number) {
  if (p < 1 || p > maxPage()) return
  pageNum.value = p
  try {
    await load()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  }
}

async function decide(item: ReviewItem, action: 'APPROVE' | 'REJECT') {
  notice.value = ''
  try {
    await api.decideReview(item.id, action, noteInput.value[item.id] || '')
    delete noteInput.value[item.id]
    await load()
  } catch (e) {
    notice.value = e instanceof Error ? e.message : '操作失败'
  }
}
</script>

<template>
  <div>
    <h1 class="serif mb-2 text-3xl">安全审核</h1>
    <p class="muted mb-4 text-sm">
      合规未通过的回复在这里处理。通过或驳回后会写回访客会话（刷新或等待轮询即可看到）。备注在通过时会作为改写正文。
    </p>
    <p v-if="error" class="text-[#f07178]">{{ error }}</p>
    <p v-if="notice" class="mb-2 text-[#f07178]">{{ notice }}</p>
    <div class="mb-4 flex items-center gap-2">
      <select v-model="filter" class="rounded border border-[#243049] bg-[#0b1220] px-2 py-1 text-sm" @change="applyFilter">
        <option value="">全部</option>
        <option value="PENDING">待审核</option>
        <option value="APPROVED">已通过</option>
        <option value="REJECTED">已驳回</option>
      </select>
      <button class="rounded border border-[#243049] px-3 py-1 text-sm text-[#8b9bb8]" @click="applyFilter">刷新</button>
    </div>
    <div v-if="!reviews.length" class="rounded-xl border border-[#243049] bg-[#121a2b] p-4 text-sm text-[#8b9bb8]">
      暂无审核单。触发合规不通过（如回复中出现越权承诺）后会出现在这里。
    </div>
    <div v-for="item in reviews" :key="item.id" class="mb-3 rounded-xl border border-[#243049] bg-[#121a2b] p-4">
      <div class="mb-1 flex items-center gap-2 text-xs">
        <span class="font-mono text-[#3dd6c6]">#{{ item.id }}</span>
        <span class="text-[#8b9bb8]">session {{ item.sessionId.slice(0, 8) }}…</span>
        <span class="text-[#8b9bb8]">intent: {{ item.intent || '-' }}</span>
        <span
          class="rounded px-2 py-0.5"
          :class="item.status === 'PENDING' ? 'bg-[#f07178]/20 text-[#f07178]' : 'bg-[#1c2a44] text-[#8b9bb8]'"
        >{{ item.status }}</span>
      </div>
      <p class="whitespace-pre-wrap text-sm text-[#c5d0e6]">{{ item.originalContent }}</p>
      <div v-if="item.violations?.length" class="mt-2 space-y-1">
        <p v-for="v in item.violations" :key="v" class="text-xs text-[#f07178]">· {{ v }}</p>
      </div>
      <div v-if="item.reviewNote" class="mt-2 text-xs text-[#8b9bb8]">备注：{{ item.reviewNote }}（{{ item.reviewedBy }}）</div>
      <div v-if="item.status === 'PENDING'" class="mt-3 flex flex-wrap items-center gap-2">
        <input
          v-model="noteInput[item.id]"
          placeholder="审核备注（可选）"
          class="flex-1 rounded border border-[#243049] bg-[#0b1220] px-3 py-1.5 text-sm"
        />
        <button class="rounded bg-[#3dd6c6] px-3 py-1.5 text-sm font-medium text-[#0b1220]" @click="decide(item, 'APPROVE')">
          通过
        </button>
        <button class="rounded border border-[#f07178] px-3 py-1.5 text-sm text-[#f07178]" @click="decide(item, 'REJECT')">
          驳回
        </button>
      </div>
    </div>
    <div class="mt-3 flex items-center gap-3 text-xs text-[#8b9bb8]">
      <button class="rounded border border-[#243049] px-3 py-1" :disabled="pageNum <= 1" @click="go(pageNum - 1)">
        上一页
      </button>
      <span>第 {{ pageNum }} / {{ maxPage() }} 页 · 共 {{ total }} 条</span>
      <button class="rounded border border-[#243049] px-3 py-1" :disabled="pageNum >= maxPage()" @click="go(pageNum + 1)">
        下一页
      </button>
    </div>
  </div>
</template>
