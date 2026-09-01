<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { WorkflowCard } from '../api'

const props = defineProps<{ card: WorkflowCard; disabled?: boolean }>()
const emit = defineEmits<{ submit: [values: Record<string, string>]; abandon: [] }>()

const values = reactive<Record<string, string>>({})

watch(
  () => props.card,
  (card) => {
    for (const field of card.fields) {
      values[field.name] = field.value ? String(field.value) : ''
    }
  },
  { immediate: true },
)

function pick(name: string, option: string) {
  values[name] = option
}

function submit() {
  emit('submit', { ...values })
}
</script>

<template>
  <div class="mt-2 rounded-2xl border border-[var(--accent)]/35 bg-[var(--bg-elev)] p-4">
    <p class="text-sm font-medium text-[var(--accent)]">{{ card.title }}</p>
    <p class="muted mt-1 text-xs">{{ card.hint }} · 可点选，也可手输</p>
    <div class="mt-3 space-y-3">
      <div v-for="field in card.fields" :key="field.name">
        <p class="mb-1 text-xs muted">
          {{ field.label }}
          <span v-if="field.required" class="danger">*</span>
        </p>
        <div v-if="field.type === 'choice'" class="flex flex-wrap gap-2">
          <button
            v-for="opt in field.options"
            :key="opt"
            type="button"
            class="rounded-full border px-3 py-1 text-xs"
            :class="values[field.name] === opt ? 'border-[var(--accent)] bg-[var(--accent-dim)] text-[var(--accent)]' : 'border-[var(--line)]'"
            :disabled="disabled"
            @click="pick(field.name, opt)"
          >
            {{ opt }}
          </button>
        </div>
        <div v-if="field.type === 'textarea'">
          <textarea
            v-model="values[field.name]"
            class="input mt-1 min-h-40 font-mono text-xs leading-relaxed"
            rows="10"
            placeholder="可直接改 AI 起草的内容"
            :disabled="disabled"
          />
        </div>
        <input
          v-else
          v-model="values[field.name]"
          class="input mt-1"
          :placeholder="field.type === 'choice' ? '也可手动输入' : '请填写'"
          :disabled="disabled"
        />
      </div>
    </div>
    <div class="mt-4 flex flex-wrap gap-2">
      <button type="button" class="btn-primary" :disabled="disabled" @click="submit">确认提交</button>
      <button type="button" class="chip" :disabled="disabled" @click="emit('abandon')">先不提交</button>
    </div>
  </div>
</template>
