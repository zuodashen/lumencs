<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  loading.value = true
  try {
    const data = await api.login(username.value, password.value)
    localStorage.setItem('lumencs_user', data.username)
    const raw = route.query.next
    const next = typeof raw === 'string' && raw.startsWith('/') && !raw.startsWith('//') && !raw.includes('\\')
      ? raw
      : '/'
    await router.push(next)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '登录失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center p-6">
    <form class="panel w-full max-w-sm p-7" @submit.prevent="submit">
      <p class="text-[11px] uppercase tracking-[0.28em] text-[var(--accent)]">Lumen Hub</p>
      <h1 class="serif mt-1 mb-2 text-3xl">登录</h1>
      <p class="muted mb-6 text-sm">对话和控制台使用同一账号</p>
      <label class="mb-3 block text-sm muted">
        用户名
        <input v-model="username" class="input mt-1" autocomplete="username" />
      </label>
      <label class="mb-4 block text-sm muted">
        密码
        <input v-model="password" type="password" class="input mt-1" autocomplete="current-password" />
      </label>
      <p v-if="error" class="danger mb-3 text-sm">{{ error }}</p>
      <button class="btn-primary w-full" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
    </form>
  </div>
</template>
