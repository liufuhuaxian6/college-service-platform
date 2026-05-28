function toDate(t) {
  if (!t) return null
  if (t instanceof Date) return isNaN(t.getTime()) ? null : t
  // 后端 LocalDateTime 序列化为 ISO 字符串或带毫秒小数, 都能被 Date 解析
  // PostgreSQL TIMESTAMP 无时区, 这里按本地时区显示
  const d = new Date(t)
  return isNaN(d.getTime()) ? null : d
}

function pad(n) { return n < 10 ? '0' + n : '' + n }

/** 表格列用: YYYY-MM-DD HH:mm */
export function formatDateTime(t) {
  const d = toDate(t)
  if (!d) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 仅日期: YYYY-MM-DD */
export function formatDate(t) {
  const d = toDate(t)
  if (!d) return '-'
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 卡片/列表用: 刚刚 / X 分钟前 / X 小时前 / 昨天 HH:mm / YYYY-MM-DD */
export function formatRelativeTime(t) {
  const d = toDate(t)
  if (!d) return ''
  const now = new Date()
  const diffMs = now - d
  if (diffMs < 0) return formatDateTime(d)
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin} 分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24 && d.getDate() === now.getDate()) return `${diffHour} 小时前`
  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (d >= yesterday && d < new Date(now.getFullYear(), now.getMonth(), now.getDate())) {
    return `昨天 ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  if (d.getFullYear() === now.getFullYear()) {
    return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  }
  return formatDate(d)
}
