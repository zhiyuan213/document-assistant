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
  const result = await new Promise((resolve, reject) => uni.downloadFile({ url, header, success: resolve, fail: reject }))
  if (result.statusCode !== 200) throw new Error('文件已过期或下载失败')
  const path = wx.env.USER_DATA_PATH + '/dengke-result-' + job.id + '.' + job.extension
  await new Promise((resolve, reject) => wx.getFileSystemManager().copyFile({ srcPath: result.tempFilePath, destPath: path, success: resolve, fail: reject }))
  if (job.extension === 'zip') {
    await new Promise((resolve, reject) => wx.shareFileMessage({ filePath: path, fileName: name, success: resolve, fail: reject }))
  } else {
    await new Promise((resolve, reject) => uni.openDocument({ filePath: path, fileType: job.extension, showMenu: true, success: resolve, fail: reject }))
    uni.showToast({ title: '通过右上角菜单保存或分享', icon: 'none' })
  }
  // #endif
}
