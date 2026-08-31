<script setup lang="ts">
import { computed, ref } from 'vue'
import KlineChart from './KlineChart.vue'

const props = defineProps<{ embed: Record<string, any> }>()

const tab = ref<'overview' | 'kline' | 'news'>('overview')
const openChart = ref(false)
const quote = computed(() => props.embed.quote || {})
const score = computed(() => props.embed.score || {})
const summary = computed(() => props.embed.summary || {})
const news = computed(() => (Array.isArray(props.embed.news) ? props.embed.news : []) as any[])
const tags = computed(() => (Array.isArray(score.value.tags) ? score.value.tags : []) as any[])
const klines = computed(() => (Array.isArray(props.embed.klines) ? props.embed.klines : []) as any[])
const up = computed(() => Number(quote.value.change_pct || 0) >= 0)
const marketLabel = computed(() => {
  const m = String(props.embed.market || 'CN').toUpperCase()
  if (m === 'HK') return '港股'
  if (m === 'US') return '美股'
  return 'A股'
})

function num(v: unknown, digits = 2) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n.toFixed(digits)
}

function compact(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  const abs = Math.abs(n)
  if (abs >= 1e8) return (n / 1e8).toFixed(2) + '亿'
  if (abs >= 1e4) return (n / 1e4).toFixed(2) + '万'
  return n.toFixed(2)
}

function marketCap(v: unknown) {
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return Math.abs(n) >= 1000 ? compact(n) : n.toFixed(2) + '亿'
}

const amplitude = computed(() => {
  if (summary.value.amplitude != null) return num(summary.value.amplitude) + '%'
  const high = Number(quote.value.high_price)
  const low = Number(quote.value.low_price)
  const prev = Number(quote.value.prev_close)
  if (!prev) return '—'
  return (((high - low) / prev) * 100).toFixed(2) + '%'
})

const metrics = computed(() => [
  { label: '今开', value: num(quote.value.open_price) },
  { label: '最高', value: num(quote.value.high_price) },
  { label: '最低', value: num(quote.value.low_price) },
  { label: '成交量', value: compact(quote.value.volume) },
  { label: '成交额', value: compact(quote.value.turnover) },
  { label: '振幅', value: amplitude.value },
  { label: '换手率', value: quote.value.turnover_rate == null ? '—' : num(quote.value.turnover_rate) + '%' },
  { label: '市盈率', value: num(quote.value.pe_ratio) },
  { label: '总市值', value: marketCap(quote.value.total_market_value) },
])

const actionClass = computed(() => {
  const a = score.value.action
  if (a === 'avoid') return 'badge-avoid'
  if (a === 'buy') return 'badge-buy'
  return 'badge-watch'
})

function showKline() {
  tab.value = 'kline'
  openChart.value = true
}
</script>

<template>
  <section class="stock-card mt-2">
    <header class="flex flex-wrap items-start justify-between gap-2 px-4 pt-4">
      <div>
        <p class="text-[11px] uppercase tracking-[0.18em] text-[var(--muted)]">
          {{ marketLabel }} · {{ embed.symbol }}
        </p>
        <h3 class="mt-1 text-lg font-semibold">{{ embed.name }}</h3>
        <p class="muted mt-0.5 text-xs">现价、迷你 K 线、技术标签与新闻，数据来自盯盘侠。点 K 线可放大。</p>
      </div>
      <a
        v-if="embed.openUrl"
        class="btn-ghost px-2 py-1 text-xs"
        :href="embed.openUrl"
        target="_blank"
        rel="noreferrer"
      >打开盯盘侠</a>
    </header>

    <div class="mt-3 flex gap-2 px-4 text-xs">
      <button class="chip" :class="tab === 'overview' ? 'border-[var(--accent)] text-[var(--accent)]' : ''" @click="tab = 'overview'">总览</button>
      <button class="chip" :class="tab === 'kline' ? 'border-[var(--accent)] text-[var(--accent)]' : ''" @click="showKline">K线</button>
      <button class="chip" :class="tab === 'news' ? 'border-[var(--accent)] text-[var(--accent)]' : ''" @click="tab = 'news'">
        新闻 ({{ news.length }})
      </button>
    </div>

    <div v-if="tab === 'overview'" class="grid gap-3 p-4 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,1fr)]">
      <div class="rounded-2xl border border-[var(--line)] bg-[#0c1018] p-4">
        <div class="flex items-end gap-3">
          <p class="font-semibold leading-none" :class="up ? 'text-[#ff5a6a]' : 'text-[#3dd68c]'" style="font-size: 2.1rem">
            {{ num(quote.current_price) }}
          </p>
          <p class="mb-1 text-sm font-medium" :class="up ? 'text-[#ff5a6a]' : 'text-[#3dd68c]'">
            {{ up ? '+' : '' }}{{ num(quote.change_pct) }}%
          </p>
        </div>
        <div class="mt-4 grid grid-cols-3 gap-x-3 gap-y-3 text-xs">
          <div v-for="item in metrics" :key="item.label">
            <p class="text-[var(--muted)]">{{ item.label }}</p>
            <p class="mt-0.5 font-medium">{{ item.value }}</p>
          </div>
        </div>
      </div>

      <button type="button" class="rounded-2xl border border-[var(--line)] bg-[#0c1018] p-4 text-left hover:border-[var(--accent)]" @click="showKline">
        <div class="mb-2 flex items-center justify-between">
          <p class="text-xs text-[var(--muted)]">迷你 K 线 · 点击放大</p>
          <span class="badge-action" :class="actionClass">
            {{ score.actionLabel || '—' }}
            <em v-if="score.score != null">{{ Number(score.score) > 0 ? '+' : '' }}{{ score.score }}</em>
          </span>
        </div>
        <KlineChart v-if="klines.length" :klines="klines.slice(-30)" :height="88" />
        <p v-else class="muted py-8 text-center text-xs">暂无 K 线</p>
        <div class="mt-3 flex flex-wrap gap-1.5">
          <span
            v-for="tag in tags"
            :key="tag.label"
            class="tag-pill"
            :class="tag.tone === 'up' ? 'tone-up' : tag.tone === 'down' ? 'tone-down' : 'tone-neutral'"
          >{{ tag.label }}</span>
        </div>
      </button>
    </div>

    <div v-else-if="tab === 'kline'" class="p-4">
      <div class="mb-2 flex items-center justify-between">
        <p class="text-xs text-[var(--muted)]">日 K · 蓝 MA5 · 橙 MA10 · 紫 MA20 · 悬停看 OHLC</p>
        <button class="chip" @click="openChart = true">全屏</button>
      </div>
      <div class="rounded-2xl border border-[var(--line)] bg-[#0c1018] p-3">
        <KlineChart :klines="klines" :height="260" interactive />
      </div>
    </div>

    <div v-else class="space-y-2 p-4">
      <a
        v-for="item in news"
        :key="item.title"
        class="block rounded-xl border border-[var(--line)] px-3 py-2 text-sm hover:border-[var(--accent)]"
        :href="item.url || undefined"
        :target="item.url ? '_blank' : undefined"
        rel="noreferrer"
      >
        <p>{{ item.title }}</p>
        <p class="muted mt-1 text-xs">{{ item.source }} · {{ item.time }}</p>
      </a>
      <p v-if="!news.length" class="muted text-sm">暂无新闻。</p>
    </div>

    <p class="muted px-4 pb-3 text-[11px]">{{ score.reason || '仅供参考，不是投资建议。' }}</p>
  </section>

  <div v-if="openChart" class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" @click.self="openChart = false">
    <div class="w-full max-w-4xl rounded-2xl border border-[var(--line)] bg-[#12151c] p-4">
      <div class="mb-3 flex items-center justify-between">
        <p class="font-medium">{{ embed.name }} {{ embed.symbol }} · 日 K</p>
        <button class="chip" @click="openChart = false">关闭</button>
      </div>
      <KlineChart :klines="klines" :height="360" interactive />
      <p class="muted mt-2 text-xs">蓝 MA5 · 橙 MA10 · 紫 MA20。数据来自盯盘侠，仅供参考。</p>
    </div>
  </div>
</template>

<style scoped>
.stock-card {
  border: 1px solid var(--line);
  border-radius: 1.15rem;
  background: #12151c;
  overflow: hidden;
}
.badge-action {
  display: inline-flex;
  align-items: baseline;
  gap: 0.35rem;
  border-radius: 999px;
  padding: 0.15rem 0.6rem;
  font-size: 0.75rem;
  font-weight: 600;
}
.badge-action em {
  font-style: normal;
  opacity: 0.85;
  font-size: 0.7rem;
}
.badge-avoid { background: #ff5a6a22; color: #ff5a6a; }
.badge-buy { background: #ff5a6a22; color: #ff5a6a; }
.badge-watch { background: #8b9bb822; color: #c5d0e6; }
.tag-pill {
  border-radius: 999px;
  padding: 0.12rem 0.5rem;
  font-size: 0.7rem;
}
.tone-up { background: #ff5a6a22; color: #ff8b96; }
.tone-down { background: #3dd68c22; color: #3dd68c; }
.tone-neutral { background: #243049; color: #8b9bb8; }
</style>
