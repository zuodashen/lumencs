<script setup lang="ts">
import { useRouter } from 'vue-router'
import Icon from './Icon.vue'
import StockInsightEmbed from './StockInsightEmbed.vue'

const props = defineProps<{ embed: Record<string, any>; busy?: boolean }>()
const emit = defineEmits<{ prompt: [text: string] }>()
const router = useRouter()

function syncArticle(slug: string) {
  if (!slug || props.busy) return
  emit('prompt', `同步这篇博客：${slug}`)
}

function chatArticle(slug: string) {
  if (!slug) return
  router.push({ path: '/embed', query: { slug } })
}

function groups() {
  return Array.isArray(props.embed.groups) ? props.embed.groups : []
}

function bookmarks(group: any) {
  return Array.isArray(group?.bookmarks) ? group.bookmarks : []
}
</script>

<template>
  <StockInsightEmbed v-if="embed.kind === 'stock'" :embed="embed" />

  <section v-else-if="embed.kind === 'blog_list'" class="mt-2 space-y-2">
    <article
      v-for="item in embed.articles || []"
      :key="item.slug"
      class="rounded-2xl border border-[var(--line)] bg-[var(--bg-elev)] p-3"
    >
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <p class="font-medium">{{ item.title }}</p>
          <p class="muted mt-1 line-clamp-2 text-xs">{{ item.summary || item.slug }}</p>
          <p class="muted mt-1 text-[11px]">
            {{ item.ingested ? '已在知识库' : '尚未同步' }}
            <span v-if="item.category"> · {{ item.category }}</span>
          </p>
        </div>
        <Icon name="book" :size="16" class="mt-1 shrink-0 text-[var(--muted)]" />
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <button class="chip" :disabled="busy" @click="syncArticle(item.slug)">
          {{ item.ingested ? '再同步' : '同步到知识库' }}
        </button>
        <button class="chip" @click="chatArticle(item.slug)">聊这篇</button>
        <a v-if="item.url" class="chip" :href="item.url" target="_blank" rel="noreferrer">打开前台</a>
      </div>
    </article>
    <p v-if="!(embed.articles || []).length" class="muted text-sm">没有已发布文章。</p>
  </section>

  <section v-else-if="embed.kind === 'bookmark_list'" class="mt-2 space-y-3">
    <div v-for="group in groups()" :key="group.id || group.name" class="rounded-2xl border border-[var(--line)] bg-[var(--bg-elev)] p-3">
      <p class="text-xs font-semibold text-[var(--muted)]">{{ group.name }}</p>
      <a
        v-for="mark in bookmarks(group)"
        :key="mark.id || mark.link"
        class="mt-2 flex items-start justify-between gap-2 rounded-xl px-1 py-1.5 text-sm hover:text-[var(--accent)]"
        :href="mark.link"
        target="_blank"
        rel="noreferrer"
      >
        <span>
          <span class="font-medium">{{ mark.name }}</span>
          <span v-if="mark.description" class="muted mt-0.5 block text-xs">{{ mark.description }}</span>
        </span>
        <Icon name="external" :size="13" class="mt-1 shrink-0 text-[var(--muted)]" />
      </a>
      <p v-if="!bookmarks(group).length" class="muted mt-2 text-xs">这个分组还没有书签。</p>
    </div>
    <p v-if="!groups().length" class="muted text-sm">没有书签。</p>
  </section>
</template>
