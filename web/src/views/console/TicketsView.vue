<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Page } from '../../api'

const tickets = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 10
const error = ref('')
const notice = ref('')

/** 与后端 TicketStatus 状态机一致：只允许合法流转 */
const TRANSITIONS: Record<string, string[]> = {
  CREATED: ['PROCESSING', 'ESCALATED', 'CLOSED'],
  PROCESSING: ['WAITING_HUMAN', 'RESOLVED', 'ESCALATED'],
  WAITING_HUMAN: ['PROCESSING', 'RESOLVED', 'ESCALATED'],
  RESOLVED: ['CLOSED', 'PROCESSING', 'ESCALATED'],
  ESCALATED: ['PROCESSING', 'WAITING_HUMAN', 'RESOLVED'],
  CLOSED: [],
}

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
    <h1 class="serif mb-2 text-3xl">工单</h1>
    <p v-if="error" class="text-[#f07178]">{{ error }}</p>
    <p v-if="notice" class="mb-2 text-[#f07178]">{{ notice }}</p>
    <p class="mb-3 text-xs text-[#8b9bb8]">
      状态机：CREATED → PROCESSING → WAITING_HUMAN → RESOLVED → CLOSED，任意阶段可 ESCALATED；非法流转会被拒绝。
    </p>
    <div class="overflow-x-auto rounded-xl border border-[#243049]">
      <table class="w-full text-left text-sm">
        <thead class="bg-[#121a2b] text-[#8b9bb8]">
          <tr>
            <th class="px-3 py-2">单号</th>
            <th class="px-3 py-2">标题</th>
            <th class="px-3 py-2">状态</th>
            <th class="px-3 py-2">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in tickets" :key="item.id" class="border-t border-[#243049]">
            <td class="px-3 py-2 font-mono text-xs">{{ item.ticketNo }}</td>
            <td class="px-3 py-2">{{ item.title }}</td>
            <td class="px-3 py-2">{{ item.status }}</td>
            <td class="px-3 py-2">
              <select
                class="rounded border border-[#243049] bg-[#0b1220] px-2 py-1"
                :value="item.status"
                @change="change(item.id, ($event.target as HTMLSelectElement).value)"
              >
                <option :value="item.status" disabled>{{ item.status }}（当前）</option>
                <option v-for="s in TRANSITIONS[item.status] || []" :key="s" :value="s">{{ s }}</option>
              </select>
            </td>
          </tr>
        </tbody>
      </table>
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
