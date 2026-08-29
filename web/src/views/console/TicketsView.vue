<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'
import { TRANSITIONS, statusZh } from '../../status'

const tickets = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const error = ref('')
const notice = ref('')

const maxPage = () => Math.max(1, Math.ceil(total.value / pageSize))

async function load() {
  const data = (await api.tickets({ pageNum: pageNum.value, pageSize })) as Page<any>
  tickets.value = data.records
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

async function change(id: number, status: string) {
  notice.value = ''
  try {
    await api.updateTicket(id, status)
    await load()
  } catch (e) {
    notice.value = e instanceof Error ? e.message : '流转失败'
    await load()
  }
}
</script>

<template>
  <div>
    <p v-if="error" class="danger">{{ error }}</p>
    <p v-if="notice" class="mb-2 danger">{{ notice }}</p>
    <p class="muted mb-3 text-xs">
      已创建 → 进行中 → 等待处理 → 已完成 → 已关闭；任意非关闭态可升级。非法跳步会被拒绝。
    </p>
    <div class="panel overflow-x-auto">
      <table class="w-full text-left text-sm">
        <thead class="text-[var(--muted)]">
          <tr>
            <th class="px-3 py-2">单号</th>
            <th class="px-3 py-2">标题</th>
            <th class="px-3 py-2">状态</th>
            <th class="px-3 py-2">下一步</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in tickets" :key="item.id" class="border-t border-[var(--line)]">
            <td class="px-3 py-2 font-mono text-xs">{{ item.ticketNo }}</td>
            <td class="px-3 py-2">{{ item.title }}</td>
            <td class="px-3 py-2">{{ statusZh(item.status) }}</td>
            <td class="px-3 py-2">
              <select
                class="input py-1"
                :value="item.status"
                @change="change(item.id, ($event.target as HTMLSelectElement).value)"
              >
                <option :value="item.status" disabled>{{ statusZh(item.status) }}（当前）</option>
                <option v-for="s in TRANSITIONS[item.status] || []" :key="s" :value="s">{{ statusZh(s) }}</option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="muted mt-3 flex items-center gap-3 text-xs">
      <button class="btn-ghost" :disabled="pageNum <= 1" @click="go(pageNum - 1)">上一页</button>
      <span>第 {{ pageNum }} / {{ maxPage() }} 页 · 共 {{ total }} 条</span>
      <button class="btn-ghost" :disabled="pageNum >= maxPage()" @click="go(pageNum + 1)">下一页</button>
    </div>
  </div>
</template>
