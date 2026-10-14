import { baseURL } from './api'
let creating
const tokenKey = 'pdf-visitor-token-v1'
function networkError(result) {
  const message = String(result?.errMsg || '').toLowerCase()
  if (message.includes('domain') || message.includes('url not in')) return new Error('接口域名未获微信允许，请配置 request 合法域名后重新预览')
  if (message.includes('ssl') || message.includes('certificate') || message.includes('tls')) return new Error('HTTPS连接校验失败，请检查服务域名和证书')
  if (message.includes('timeout') || message.includes('timed out')) return new Error('连接处理服务超时，请检查手机网络后重试')
  return new Error('手机未能连接处理服务，请检查网络及小程序合法域名后重试')
}
function raw(path, method = 'GET', data, header = {}) {
  return new Promise((resolve, reject) => uni.request({ url: baseURL + '/api/pdf' + path, method, data, timeout: 120000,
    header: { Authorization: 'Bearer ' + (uni.getStorageSync(tokenKey) || ''), ...header },
    success: r => { if (r.statusCode >= 200 && r.statusCode < 300) resolve(r.data); else { const e = new Error(r.data?.message || '请求失败，请重试'); e.status = r.statusCode; reject(e) } },
    fail: result => reject(networkError(result)),
  }))
}
export async function visitor() {
  if (uni.getStorageSync(tokenKey)) return
  if (!creating) creating = raw('/visitor', 'POST').then(r => uni.setStorageSync(tokenKey, r.token)).finally(() => { creating = null })
  await creating
}
export function pdfConfig() { return raw('/config') }
export async function pdfRequest(path, method = 'GET', data) {
  await visitor()
  try { return await raw(path, method, data) } catch (e) {
    if (e.status !== 401) throw e
    // A transient lookup failure must not replace the identity owning an existing job.
    await new Promise(resolve => setTimeout(resolve, 600))
    try { return await raw(path, method, data) } catch (retry) {
      if (retry.status !== 401 || path.startsWith('/jobs/')) throw retry
      uni.removeStorageSync(tokenKey); await visitor(); return raw(path, method, data)
    }
  }
}
async function bytes(path) {
  // #ifdef H5
  return new Uint8Array(await (await fetch(path)).arrayBuffer())
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => wx.getFileSystemManager().readFile({ filePath: path, success: r => resolve(new Uint8Array(r.data)), fail: reject }))
  // #endif
}
export async function submitPdf(tool, files, options) {
  await visitor()
  const boundary = 'DengkePdf' + Date.now(), chunks = [], utf8 = s => Uint8Array.from(unescape(encodeURIComponent(s)), c => c.charCodeAt(0))
  for (const [index, file] of files.entries()) {
    const ext = file.name.split('.').pop().toLowerCase()
    chunks.push(utf8(`--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="input-${index}.${ext}"\r\nContent-Type: application/octet-stream\r\n\r\n`), await bytes(file.path), utf8('\r\n'))
  }
  for (const [key, value] of Object.entries({ tool, ...options })) chunks.push(utf8(`--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${value}\r\n`))
  chunks.push(utf8(`--${boundary}--\r\n`))
  const body = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0)); let offset = 0
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length }
  return raw('/jobs', 'POST', body.buffer, { 'Content-Type': 'multipart/form-data; boundary=' + boundary })
}
export async function savePdf(job) {
  const url = baseURL + '/api/pdf/jobs/' + job.id + '/file', header = { Authorization: 'Bearer ' + uni.getStorageSync(tokenKey) }, name = '登科喵结果.' + job.extension
  // #ifdef H5
  const response = await fetch(url, { headers: header }); if (!response.ok) throw new Error('文件已过期或下载失败')
  const object = URL.createObjectURL(await response.blob()), a = document.createElement('a'); a.href = object; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(object), 30000)
  // #endif
  // #ifdef MP-WEIXIN
  const result = await new Promise((resolve, reject) => uni.downloadFile({ url, header, success: resolve, fail: e => reject(networkError(e)) }))
  if (result.statusCode !== 200) throw new Error('文件已过期或下载失败')
  const path = wx.env.USER_DATA_PATH + '/dengke-result-' + job.id + '.' + job.extension
  const fs = wx.getFileSystemManager()
  let exists = false
  try { fs.accessSync(path); exists = true } catch {}
  if (!exists) await new Promise((resolve, reject) => fs.copyFile({ srcPath: result.tempFilePath, destPath: path, success: resolve, fail: e => reject(fileError('保存到手机', e)) }))
  return { path, name, extension: job.extension }
  // #endif
}

function fileError(stage, cause) {
  const message = String(cause?.errMsg || '').toLowerCase()
  const cancelled = message.includes('cancel')
  let text = cancelled ? '已取消操作' : stage + '失败，请重新下载后重试'
  if (message.includes('tap gesture')) text = '请直接点击“发送 / 保存到微信”按钮'
  else if (message.includes('no such file') || message.includes('not exist')) text = '手机临时文件已清理，请重新下载'
  else if (message.includes('space') || message.includes('quota')) text = '手机存储空间不足，请清理后再下载'
  else if (message.includes('not support')) text = '当前微信版本不支持该文件操作，请更新微信后重试'
  return Object.assign(new Error(text), { cancelled })
}

// Called directly from the second button tap, before any asynchronous file operation.
export function openSavedPdf(file, platform) {
  // #ifdef MP-WEIXIN
  const native = platform || wx
  return new Promise((resolve, reject) => {
    if (file.extension === 'zip') {
      if (typeof native.shareFileMessage !== 'function') { reject(new Error('当前微信版本不支持发送文件，请更新微信')); return }
      native.shareFileMessage({ filePath: file.path, fileName: file.name, success: resolve, fail: e => reject(fileError('发送文件', e)) })
    } else {
      native.openDocument({ filePath: file.path, fileType: file.extension, showMenu: true, success: resolve, fail: e => reject(fileError('打开文档', e)) })
    }
  })
  // #endif
}

export async function pollPdfJob(read, { active = () => true, onProgress = () => {}, interval = 1200, missingRetries = 5 } = {}) {
  let missing = 0
  while (active()) {
    let job
    try { job = await read() }
    catch (error) {
      if (error.status !== 404 || missing++ >= missingRetries) throw error
      onProgress({ status: 'syncing', progress: 10 })
      await new Promise(resolve => setTimeout(resolve, interval))
      continue
    }
    onProgress(job)
    if (job.status === 'failed') throw new Error(job.error_message || '文件处理失败')
    if (job.status === 'completed') return job
    await new Promise(resolve => setTimeout(resolve, interval))
  }
  return null
}
