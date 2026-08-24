<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { WorkflowCard } from '../api'

const props = defineProps<{ card: WorkflowCard; disabled?: boolean }>()
const emit = defineEmits<{ submit: [values: Record<string, string>] }>()

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
  <div class="mt-2 rounded-xl border border-[#3dd6c6]/40 bg-[#0f1728] p-4">
    <p class="text-sm font-medium text-[#3dd6c6]">{{ card.title }}</p>
    <p class="mt-1 text-xs text-[#8b9bb8]">{{ card.hint }} · 可点选，也可直接输入</p>
    <div class="mt-3 space-y-3">
      <div v-for="field in card.fields" :key="field.name">
        <p class="mb-1 text-xs text-[#8b9bb8]">
          {{ field.label }}
          <span v-if="field.required" class="text-[#f07178]">*</span>
        </p>
        <div v-if="field.type === 'choice'" class="flex flex-wrap gap-2">
          <button
            v-for="opt in field.options"
            :key="opt"
            type="button"
            class="rounded-full border px-3 py-1 text-xs"
            :class="values[field.name] === opt ? 'border-[#3dd6c6] bg-[#3dd6c6]/15 text-[#3dd6c6]' : 'border-[#243049] text-[#c5d0e6]'"
            :disabled="disabled"
            @click="pick(field.name, opt)"
          >
            {{ opt }}
          </button>
        </div>
        <input
          v-model="values[field.name]"
          class="mt-1 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-sm outline-none"
          :placeholder="field.type === 'choice' ? '也可手动输入' : '请填写'"
          :disabled="disabled"
        />
      </div>
    </div>
    <button
      type="button"
      class="mt-4 rounded-lg bg-[#3dd6c6] px-4 py-2 text-sm font-medium text-[#0b1220]"
      :disabled="disabled"
      @click="submit"
    >
      确认提交
    </button>
  </div>
</template>
