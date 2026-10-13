import { reactive } from 'vue'
import { request, ensureLogin, baseURL } from './api'
import { toast } from './tools'
export const downloadState = reactive({ items: [], owner: null, total: 0, page: 0 })
const running = new Map(),
  savedFiles = new Map()
async function userScope() {
  await ensureLogin()
  const me = await request('/me'),
    owner = me.user.id
  if (downloadState.owner !== owner) {
    downloadState.owner = owner
    downloadState.items = []
    downloadState.total = 0
    downloadState.page = 0
    savedFiles.clear()
  }
  return owner
}
export async function refreshDownloads(page = 1) {
  await userScope()
  const response = await request('/downloads?page=' + page)
  const live = new Map(downloadState.items.map((item) => [item.id, item]))
  const incoming = response.items.map((item) => {
    const existing = live.get(item.id)
    if (existing && running.has(item.id)) return existing
    return {
      ...item,
      bytes: item.status === 'completed' ? item.size_bytes : 0,
      status: item.status === 'started' ? 'paused' : item.status,
    }
  })
  const keep =
    page === 1 ? downloadState.items.filter((item) => running.has(item.id)) : downloadState.items
  const merged = new Map(keep.map((item) => [item.id, item]))
  for (const item of incoming) merged.set(item.id, item)
  downloadState.items =
    page === 1
      ? [...incoming, ...keep.filter((item) => !incoming.some((value) => value.id === item.id))]
      : [...merged.values()]
  downloadState.total = response.total
  downloadState.page = page
}
function patch(id, status) {
  return request('/downloads/' + id, 'PUT', { status })
}
export async function startDownload(resource) {
  await userScope()
  const existing = downloadState.items.find(
    (item) => item.resource_id === resource.id && running.has(item.id),
  )
  if (existing) return existing
  const item = await request('/downloads', 'POST', { resourceId: resource.id })
  item.bytes = 0
  downloadState.items.unshift(item)
  perform(item, downloadState.owner)
  return item
}
async function perform(item, owner) {
  if (running.has(item.id)) return
  const original = downloadState.items.find((value) => value.id === item.id) || item
  original.status = 'started'
  original.bytes = 0
  const url = baseURL + '/api/downloads/' + item.id + '/file',
    header = { Authorization: 'Bearer ' + uni.getStorageSync('token') }
  try {
    // #ifdef H5
    const controller = new AbortController()
    running.set(item.id, { abort: () => controller.abort() })
    const response = await fetch(url, { headers: header, signal: controller.signal })
    if (!response.ok) {
      if (response.status === 401) uni.removeStorageSync('token')
      throw new Error((await response.json()).message || '下载失败')
    }
    const parts = []
    if (response.body) {
      const reader = response.body.getReader()
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        parts.push(value)
        original.bytes += value.length
      }
    } else {
      const bytes = new Uint8Array(await response.arrayBuffer())
      parts.push(bytes)
      original.bytes = bytes.length
    }
    const blob = new Blob(parts, {
      type: response.headers.get('content-type') || 'application/octet-stream',
    })
    savedFiles.set(item.id, blob)
    // #endif
    // #ifdef MP-WEIXIN
    const response = await new Promise((resolve, reject) => {
      const task = uni.downloadFile({
        url,
        header,
        timeout: 120000,
        success: resolve,
        fail: reject,
      })
      running.set(item.id, { abort: () => task.abort() })
      task.onProgressUpdate((progress) => {
        original.bytes = progress.totalBytesWritten
      })
    })
    if (response.statusCode !== 200) throw new Error('下载失败，请重新登录后重试')
    savedFiles.set(item.id, response.tempFilePath)
    // #endif
    if (original.status === 'paused') return
    const activity = running.get(item.id)
    if (activity) activity.completedTransfer = true
    await patch(item.id, 'completed')
    original.status = 'completed'
    original.bytes = item.size_bytes
    if (downloadState.owner === owner) {
      try {
        await openDownload(item)
      } catch {
        toast('已下载，可在下载管理再次打开')
      }
    }
  } catch (e) {
    if (original.status === 'paused') return
    original.status = 'failed'
    try {
      await patch(item.id, 'failed')
    } catch {}
    if (downloadState.owner === owner) toast(e.message || '下载失败，请稍后重试')
  } finally {
    running.delete(item.id)
  }
}
export async function pauseDownload(item) {
  const task = running.get(item.id)
  if (!task || task.completedTransfer) return
  item.status = 'paused'
  task.abort()
  await patch(item.id, 'paused')
}
export async function resumeDownload(item) {
  if (running.has(item.id)) return
  await userScope()
  await patch(item.id, 'started')
  perform(item, downloadState.owner)
}
export async function openDownload(item) {
  const saved = savedFiles.get(item.id)
  if (!saved) {
    await startDownload({ id: item.resource_id })
    return
  }
  // #ifdef H5
  const url = URL.createObjectURL(saved),
    link = document.createElement('a')
  link.href = url
  link.download = item.name.endsWith('.' + item.kind) ? item.name : item.name + '.' + item.kind
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 30000)
  // #endif
  // #ifdef MP-WEIXIN
  if (item.kind === 'txt') {
    const text = await new Promise((resolve, reject) =>
      wx.getFileSystemManager().readFile({
        filePath: saved,
        encoding: 'utf8',
        success: (r) => resolve(r.data),
        fail: reject,
      }),
    )
    await new Promise((resolve, reject) =>
      uni.setClipboardData({ data: text, success: resolve, fail: reject }),
    )
  } else
    await new Promise((resolve, reject) =>
      uni.openDocument({
        filePath: saved,
        fileType: item.kind,
        showMenu: true,
        success: resolve,
        fail: reject,
      }),
    )
  // #endif
}
export function resetDownloads() {
  for (const item of downloadState.items) {
    const task = running.get(item.id)
    if (task) {
      item.status = 'paused'
      task.abort()
    }
  }
  savedFiles.clear()
  downloadState.items = []
  downloadState.owner = null
  downloadState.total = 0
  downloadState.page = 0
}
