<script setup lang="ts">
import Icon from '../components/Icon.vue'
import { LIVE_APPS } from '../apps'
</script>

<template>
  <div class="grid gap-4 sm:grid-cols-2">
    <a
      v-for="app in LIVE_APPS"
      :key="app.id"
      class="panel block p-6 transition hover:border-[var(--accent)]"
      :href="app.href"
      target="_blank"
      rel="noreferrer"
    >
      <span class="stat-icon text-white" :style="{ background: app.color }">
        <Icon :name="app.icon" :size="16" />
      </span>
      <p class="mt-4 text-lg font-semibold">{{ app.name }}</p>
      <p class="muted mt-1 text-sm">{{ app.hint }}</p>
      <p class="mt-4 inline-flex items-center gap-1 text-xs accent">
        打开
        <Icon name="external" :size="12" />
      </p>
    </a>
  </div>
</template>
