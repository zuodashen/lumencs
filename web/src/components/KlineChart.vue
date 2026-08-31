<script setup lang="ts">
import { computed, ref } from 'vue'

const props = withDefaults(
  defineProps<{
    klines: any[]
    height?: number
    interactive?: boolean
  }>(),
  { height: 88, interactive: false },
)

const hover = ref<number | null>(null)
const rows = computed(() => (Array.isArray(props.klines) ? props.klines.filter((r) => r && r.close != null) : []))

const layout = computed(() => {
  const list = rows.value
  const highs = list.map((r) => Number(r.high || r.close || 0)).filter((n) => n > 0)
  const lows = list.map((r) => Number(r.low || r.close || 0)).filter((n) => n > 0)
  const max = Math.max(...highs, 1)
  const min = Math.min(...lows, max - 1)
  const span = Math.max(max - min, 0.01)
  const padL = props.interactive ? 44 : 2
  const padR = 8
  const padT = 10
  const padB = props.interactive ? 22 : 4
  const w = Math.max(list.length * (props.interactive ? 9 : 7) + padL + padR, 220)
  const h = props.height
  const innerH = h - padT - padB
  const innerW = w - padL - padR
  const step = list.length ? innerW / list.length : 8
  const y = (v: number) => padT + ((max - v) / span) * innerH
  const bars = list.map((row, i) => {
    const open = Number(row.open || row.close || 0)
    const close = Number(row.close || 0)
    const high = Number(row.high || close)
    const low = Number(row.low || close)
    const x = padL + i * step + step * 0.2
    const bw = Math.max(step * 0.55, 2)
    const bodyTop = y(Math.max(open, close))
    const bodyBot = y(Math.min(open, close))
    return {
      i,
      x,
      cx: x + bw / 2,
      bw,
      wickY1: y(high),
      wickY2: y(low),
      bodyY: bodyTop,
      bodyH: Math.max(bodyBot - bodyTop, 1.2),
      up: close >= open,
      open,
      close,
      high,
      low,
      date: String(row.date || '').slice(0, 10),
    }
  })
  const ma = (n: number) => {
    const pts: { x: number; y: number }[] = []
    for (let i = n - 1; i < list.length; i++) {
      let s = 0
      for (let j = i - n + 1; j <= i; j++) s += Number(list[j].close || 0)
      pts.push({ x: bars[i].cx, y: y(s / n) })
    }
    return pts.map((p) => `${p.x},${p.y}`).join(' ')
  }
  return { w, h, bars, max, min, padL, y, ma5: ma(5), ma10: ma(10), ma20: ma(20) }
})

const tip = computed(() => {
  if (hover.value == null) return null
  return layout.value.bars[hover.value] || null
})

function onMove(e: MouseEvent) {
  if (!props.interactive || !layout.value.bars.length) return
  const svg = e.currentTarget as SVGSVGElement
  const box = svg.getBoundingClientRect()
  const x = ((e.clientX - box.left) / box.width) * layout.value.w
  let best = 0
  let dist = Infinity
  for (const bar of layout.value.bars) {
    const d = Math.abs(bar.cx - x)
    if (d < dist) {
      dist = d
      best = bar.i
    }
  }
  hover.value = best
}

function num(v: number) {
  return v.toFixed(2)
}
</script>

<template>
  <div class="relative">
    <svg
      :viewBox="`0 0 ${layout.w} ${layout.h}`"
      class="w-full"
      :style="{ height: height + 'px' }"
      @mousemove="onMove"
      @mouseleave="hover = null"
    >
      <template v-if="interactive">
        <text :x="2" :y="12" fill="#8b9bb8" font-size="10">{{ num(layout.max) }}</text>
        <text :x="2" :y="layout.h - 26" fill="#8b9bb8" font-size="10">{{ num(layout.min) }}</text>
        <polyline v-if="layout.ma20" :points="layout.ma20" fill="none" stroke="#8b6cff" stroke-width="1" />
        <polyline v-if="layout.ma10" :points="layout.ma10" fill="none" stroke="#ff9f43" stroke-width="1" />
        <polyline v-if="layout.ma5" :points="layout.ma5" fill="none" stroke="#5b8def" stroke-width="1.2" />
      </template>
      <line
        v-for="bar in layout.bars"
        :key="'w' + bar.i"
        :x1="bar.cx"
        :x2="bar.cx"
        :y1="bar.wickY1"
        :y2="bar.wickY2"
        :stroke="bar.up ? '#ff5a6a' : '#3dd68c'"
        stroke-width="1"
      />
      <rect
        v-for="bar in layout.bars"
        :key="'b' + bar.i"
        :x="bar.x"
        :y="bar.bodyY"
        :width="bar.bw"
        :height="bar.bodyH"
        :fill="bar.up ? '#ff5a6a' : '#3dd68c'"
        rx="0.5"
      />
      <line
        v-if="tip"
        :x1="tip.cx"
        :x2="tip.cx"
        y1="8"
        :y2="layout.h - 18"
        stroke="#8b9bb8"
        stroke-dasharray="2 3"
        stroke-width="1"
      />
      <text
        v-if="interactive && layout.bars.length"
        :x="layout.bars[0].cx"
        :y="layout.h - 6"
        fill="#8b9bb8"
        font-size="10"
      >{{ layout.bars[0].date.slice(5) }}</text>
      <text
        v-if="interactive && layout.bars.length"
        :x="layout.bars[layout.bars.length - 1].cx - 28"
        :y="layout.h - 6"
        fill="#8b9bb8"
        font-size="10"
      >{{ layout.bars[layout.bars.length - 1].date.slice(5) }}</text>
    </svg>
    <div v-if="tip" class="pointer-events-none absolute right-2 top-2 rounded-lg bg-[#0c1018cc] px-2 py-1 text-[11px]">
      <p>{{ tip.date }}</p>
      <p :class="tip.up ? 'text-[#ff5a6a]' : 'text-[#3dd68c]'">
        开 {{ num(tip.open) }} 收 {{ num(tip.close) }}
      </p>
      <p class="text-[var(--muted)]">高 {{ num(tip.high) }} 低 {{ num(tip.low) }}</p>
    </div>
  </div>
</template>
