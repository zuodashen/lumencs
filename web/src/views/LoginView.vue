<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const username = ref('admin')
const password = ref('lumen123')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const data = await api.login(username.value, password.value)
    localStorage.setItem('lumencs_user', data.username)
    await router.push('/console/overview')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center p-4">
    <form class="w-full max-w-sm rounded-xl border border-[#243049] bg-[#121a2b] p-6" @submit.prevent="submit">
      <p class="text-xs tracking-[0.2em] text-[#8b9bb8] uppercase">LumenCS Console</p>
      <h1 class="mt-1 mb-6 text-xl font-semibold">控制台登录</h1>
      <label class="mb-3 block text-sm text-[#8b9bb8]">
        用户名
        <input v-model="username" class="mt-1 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-[var(--text)]" />
      </label>
      <label class="mb-4 block text-sm text-[#8b9bb8]">
        密码
        <input v-model="password" type="password" class="mt-1 w-full rounded-lg border border-[#243049] bg-[#0b1220] px-3 py-2 text-[var(--text)]" />
      </label>
      <p v-if="error" class="mb-3 text-sm text-[#f07178]">{{ error }}</p>
      <button class="w-full rounded-lg bg-[#3dd6c6] py-2 font-medium text-[#0b1220]" :disabled="loading">
        {{ loading ? '登录中…' : '进入控制台' }}
      </button>
    </form>
  </div>
</template>
