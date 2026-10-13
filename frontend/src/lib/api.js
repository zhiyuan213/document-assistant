export const baseURL = import.meta.env.VITE_API_URL || 'http://localhost:8080'
export function request(path, method = 'GET', data, extra = {}) {
  return new Promise((resolve, reject) =>
    uni.request({
      url: baseURL + '/api' + path,
      method,
      data,
      timeout: 120000,
      header: { Authorization: 'Bearer ' + (uni.getStorageSync('token') || ''), ...extra },
      success: (r) => {
        if (r.statusCode >= 200 && r.statusCode < 300) resolve(r.data)
        else {
          if (r.statusCode === 401) uni.removeStorageSync('token')
          reject(new Error(r.data?.message || '请求失败，请重试'))
        }
      },
      fail: () => reject(new Error('无法连接服务，请确认后端已启动')),
    }),
  )
}
export async function login() {
  let body
  // #ifdef MP-WEIXIN
  const r = await new Promise((resolve, reject) =>
    uni.login({ provider: 'weixin', success: resolve, fail: reject }),
  )
  body = { code: r.code, development: false }
  // #endif
  // #ifdef H5
  body = { development: true }
  // #endif
  const result = await request('/auth/login', 'POST', body)
  uni.setStorageSync('token', result.token)
  return result
}
export async function ensureLogin() {
  if (!uni.getStorageSync('token')) await login()
}
async function readFile(path) {
  // #ifdef H5
  return new Uint8Array(await (await fetch(path)).arrayBuffer())
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) =>
    wx
      .getFileSystemManager()
      .readFile({ filePath: path, success: (r) => resolve(new Uint8Array(r.data)), fail: reject }),
  )
  // #endif
}
// 小程序uploadFile不支持多文件，通过multipart协议一次上传，保留排序。
export async function processFiles(operation, files, options) {
  await ensureLogin()
  const boundary = 'Dengke' + Date.now(),
    ascii = (text) => Uint8Array.from(text, (c) => c.charCodeAt(0)),
    chunks = []
  for (let i = 0; i < files.length; i++) {
    const bytes = await readFile(files[i].path),
      pdf = ['compress', 'merge'].includes(operation)
    chunks.push(
      ascii(
        `--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="file${i}.${pdf ? 'pdf' : 'jpg'}"\r\nContent-Type: application/octet-stream\r\n\r\n`,
      ),
      bytes,
      ascii('\r\n'),
    )
  }
  for (const [name, value] of Object.entries(options))
    chunks.push(
      ascii(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`),
    )
  chunks.push(ascii(`--${boundary}--\r\n`))
  const result = new Uint8Array(chunks.reduce((sum, c) => sum + c.length, 0))
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.length
  }
  return request('/process/' + operation, 'POST', result.buffer, {
    'Content-Type': 'multipart/form-data; boundary=' + boundary,
  })
}
export async function download(file) {
  await ensureLogin()
  // #ifdef H5
  const r = await fetch(baseURL + '/api/files/' + file.id + '/download', {
    headers: { Authorization: 'Bearer ' + uni.getStorageSync('token') },
  })
  if (!r.ok) throw new Error('下载失败，请重新登录后重试')
  const url = URL.createObjectURL(await r.blob()),
    anchor = document.createElement('a')
  anchor.href = url
  anchor.download = file.name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 30000)
  // #endif
  // #ifdef MP-WEIXIN
  const r = await new Promise((resolve, reject) =>
    uni.downloadFile({
      url: baseURL + '/api/files/' + file.id + '/download',
      header: { Authorization: 'Bearer ' + uni.getStorageSync('token') },
      success: resolve,
      fail: reject,
    }),
  )
  if (r.statusCode !== 200) throw new Error('下载失败，请重新登录后重试')
  if (file.kind === 'pdf')
    await new Promise((resolve, reject) =>
      uni.openDocument({
        filePath: r.tempFilePath,
        fileType: 'pdf',
        showMenu: true,
        success: resolve,
        fail: reject,
      }),
    )
  else {
    const text = await new Promise((resolve, reject) =>
      wx.getFileSystemManager().readFile({
        filePath: r.tempFilePath,
        encoding: 'utf8',
        success: (r) => resolve(r.data),
        fail: reject,
      }),
    )
    await new Promise((resolve, reject) =>
      uni.setClipboardData({ data: text, success: resolve, fail: reject }),
    )
  }
  // #endif
}
