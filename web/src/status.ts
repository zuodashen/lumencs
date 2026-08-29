export const STATUS_ZH: Record<string, string> = {
  CREATED: '已创建',
  PROCESSING: '进行中',
  WAITING_HUMAN: '等待处理',
  RESOLVED: '已完成',
  CLOSED: '已关闭',
  ESCALATED: '已升级',
}

export function statusZh(status?: string) {
  if (!status) return '—'
  return STATUS_ZH[status] || status
}

export const TRANSITIONS: Record<string, string[]> = {
  CREATED: ['PROCESSING', 'ESCALATED', 'CLOSED'],
  PROCESSING: ['WAITING_HUMAN', 'RESOLVED', 'ESCALATED'],
  WAITING_HUMAN: ['PROCESSING', 'RESOLVED', 'ESCALATED'],
  RESOLVED: ['CLOSED', 'PROCESSING', 'ESCALATED'],
  ESCALATED: ['PROCESSING', 'WAITING_HUMAN', 'RESOLVED'],
  CLOSED: [],
}

export function nextDoneStatus(status?: string) {
  if (status === 'CREATED') return 'PROCESSING'
  if (status === 'PROCESSING' || status === 'WAITING_HUMAN' || status === 'ESCALATED') return 'RESOLVED'
  if (status === 'RESOLVED') return 'CLOSED'
  return ''
}

export function greeting(hour: number) {
  if (hour < 6) return '夜深了'
  if (hour < 12) return '早上好'
  if (hour < 18) return '下午好'
  return '晚上好'
}

export function relativeTime(raw?: string | number[] | Date) {
  if (raw == null || raw === '') return ''
  let t: number
  if (raw instanceof Date) {
    t = raw.getTime()
  } else if (Array.isArray(raw) && raw.length >= 3) {
    t = new Date(raw[0], raw[1] - 1, raw[2], raw[3] || 0, raw[4] || 0, raw[5] || 0).getTime()
  } else {
    t = new Date(raw).getTime()
  }
  if (Number.isNaN(t)) return String(raw)
  const mins = Math.max(0, Math.round((Date.now() - t) / 60000))
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins} 分钟前`
  const hours = Math.round(mins / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.round(hours / 24)} 天前`
}
