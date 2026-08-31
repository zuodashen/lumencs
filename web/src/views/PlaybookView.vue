<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'
import { SCENARIOS, TOOL_HINTS, type Scenario } from '../playbook'

const router = useRouter()
const selected = ref<Scenario | null>(SCENARIOS[0] || null)
const tools = ref<any[]>([])
const error = ref('')
const pickedTool = ref('')

const groups = computed(() => {
  const map = new Map<string, Scenario[]>()
  for (const item of SCENARIOS) {
    const list = map.get(item.group) || []
    list.push(item)
    map.set(item.group, list)
  }
  return [...map.entries()]
})

onMounted(async () => {
  try {
    const data = await api.tools()
    tools.value = data?.tools || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '工具列表加载失败'
  }
})

function openChat(text: string) {
  router.push({ path: '/chat', query: { q: text } })
}

function hintFor(name: string) {
  return TOOL_HINTS[name]
}
</script>

<template>
  <div class="space-y-8">
    <section>
      <div class="mb-3 flex items-center justify-between">
        <h2 class="text-sm font-semibold text-[var(--muted)]">能问什么</h2>
        <p class="muted text-xs">点卡片看示例，再「去对话」会把例句填进输入框。</p>
      </div>
      <div class="space-y-5">
        <div v-for="[group, items] in groups" :key="group">
          <p class="mb-2 text-[11px] uppercase tracking-[0.18em] text-[var(--muted)]">{{ group }}</p>
          <div class="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
            <button
              v-for="item in items"
              :key="item.id"
              class="panel p-4 text-left hover:border-[var(--accent)]"
              :class="selected?.id === item.id ? 'border-[var(--accent)]' : ''"
              @click="selected = item"
            >
              <p class="font-medium">{{ item.title }}</p>
              <p class="muted mt-1 text-xs leading-relaxed">{{ item.hint }}</p>
            </button>
          </div>
        </div>
      </div>
    </section>

    <section v-if="selected" class="panel p-5">
      <p class="text-xs accent">示例 · {{ selected.group }}</p>
      <h3 class="mt-1 text-xl font-semibold">{{ selected.title }}</h3>
      <p class="muted mt-2 text-sm">{{ selected.hint }}</p>
      <p class="muted mt-3 text-xs">工具 {{ selected.tool }}</p>
      <div class="mt-4 flex flex-wrap gap-2">
        <button class="btn-primary text-sm" @click="openChat(selected.example)">去对话：{{ selected.example }}</button>
        <button
          v-for="sample in selected.samples"
          :key="sample"
          class="chip"
          @click="openChat(sample)"
        >{{ sample }}</button>
      </div>
    </section>

    <section>
      <h2 class="mb-3 text-sm font-semibold text-[var(--muted)]">当前可用工具</h2>
      <p v-if="error" class="danger mb-2 text-sm">{{ error }}</p>
      <div class="grid gap-3 sm:grid-cols-2">
        <button
          v-for="tool in tools"
          :key="tool.name"
          class="panel p-4 text-left hover:border-[var(--accent)]"
          @click="pickedTool = pickedTool === tool.name ? '' : tool.name"
        >
          <p class="font-medium text-[var(--sage)]">{{ tool.name }}</p>
          <p class="muted mt-1 text-xs">{{ tool.description }}</p>
          <p class="muted mt-2 text-[11px]">params: {{ (tool.params || []).join(', ') || '无' }}</p>
          <div v-if="pickedTool === tool.name" class="mt-3 rounded-xl border border-[var(--line)] bg-[var(--bg)] p-3">
            <p class="text-xs">{{ hintFor(tool.name)?.title || '对话里触发' }}</p>
            <p v-if="hintFor(tool.name)" class="mt-1 text-sm">例句：{{ hintFor(tool.name)?.example }}</p>
            <button
              v-if="hintFor(tool.name)"
              class="chip mt-2"
              @click.stop="openChat(hintFor(tool.name)!.example)"
            >去对话试试</button>
            <p v-else class="muted mt-1 text-xs">这条主要给办事卡片内部调用，不单独从聊天点。</p>
          </div>
        </button>
      </div>
    </section>
  </div>
</template>
