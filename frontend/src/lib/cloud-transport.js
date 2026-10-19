// Small JSON envelopes only: binary input/output travels as bounded base64 chunks.
// Keep configuration explicit: a public CloudRun hostname is not an environment ID.
const cloudEnv = import.meta.env.VITE_CLOUD_ENV || ''
const cloudService = import.meta.env.VITE_CLOUD_SERVICE || ''
const enabled = import.meta.env.VITE_MP_TRANSPORT === 'cloud'
let initialized = false
const delay = ms => new Promise(resolve => setTimeout(resolve, ms))
export function cloudEnabled() {
  // #ifdef MP-WEIXIN
  return enabled
  // #endif
  // #ifndef MP-WEIXIN
  return false
  // #endif
}
function init() {
  if (!cloudEnv || !cloudService) throw new Error('请配置 PDF 云托管环境 ID 和服务名称后重新构建')
  if (!wx.cloud?.callContainer) throw new Error('当前微信版本不支持云托管调用，请更新微信')
  if (!initialized) { wx.cloud.init({ env: cloudEnv }); initialized = true }
}
async function call(path, method, data, header) {
  init()
  try {
    return await wx.cloud.callContainer({ config: { env: cloudEnv }, path, method,
      header: { 'Content-Type': 'application/json', ...header, 'X-WX-SERVICE': cloudService }, data, timeout: 14000 })
  } catch (cause) {
    // Preserve safe diagnostics, never log request headers, tokens, or document payloads.
    const raw = String(cause?.errMsg || cause?.message || '')
    const code = String(cause?.errCode ?? cause?.code ?? raw.match(/-\d{4,}/)?.[0] ?? 'UNKNOWN').replace(/[^A-Za-z0-9_-]/g, '').slice(0, 48)
    const stage = path.startsWith('/api/pdf/cloud-transfers') ? '文件传输' : path === '/api/pdf/visitor' ? '设备凭证' : path === '/api/pdf/config' ? '服务配置' : '业务接口'
    const hint = /timeout|timed out/i.test(raw) ? '请求超时，请检查服务冷启动和运行日志' : /permission|auth|access denied|not authorized/i.test(raw) ? '请检查当前小程序是否获准调用此环境' : /env.*not.*exist|invalid.*env|environment.*not.*found/i.test(raw) ? '请检查环境 ID 和小程序环境关联' : '请检查环境关联、服务名称及服务运行日志'
    console.warn('[pdf][cloud]', { stage, code, path: path.split('?')[0], env: cloudEnv, service: cloudService })
    const error = new Error(`云托管${stage}调用失败（${code}），${hint}`)
    error.cloudCode = code
    throw error
  }
}
async function control(path, method, data, header) {
  const res = await call('/api/pdf/cloud-transfers' + path, method, data, header)
  if (res.statusCode < 200 || res.statusCode >= 300) {
    const error = new Error(res.data?.message || '云端文件传输失败，请重试'); error.status = res.statusCode; throw error
  }
  return res.data
}
function encode(data) {
  if (data instanceof ArrayBuffer) return new Uint8Array(data)
  return Uint8Array.from(unescape(encodeURIComponent(data == null ? '' : JSON.stringify(data))), c => c.charCodeAt(0))
}
function decode(data) {
  let text = ''
  for (let start = 0; start < data.length; start += 8192) text += String.fromCharCode(...data.subarray(start, start + 8192))
  return decodeURIComponent(escape(text))
}
export async function cloudRequest(options) {
  const { url, header = {}, responseType, data, method = 'GET' } = options
  // Callers provide an origin-relative API path, never a remote URL.
  if (!url.startsWith('/api/pdf/')) throw new Error('云托管请求路径无效')
  const body = encode(data)
  const binary = responseType === 'arraybuffer' || data instanceof ArrayBuffer
  const slow = url === '/api/pdf/ai' || url.startsWith('/api/pdf/watermarks/')
  if (!binary && !slow && body.length < 80 * 1024) return call(url, method, data, header)
  const transferHeader = { Authorization: header.Authorization || '', 'Content-Type': 'application/json' }
  const contentType = header['Content-Type'] || header['content-type'] || 'application/json'
  const transfer = await control('', 'POST', { path: url, method, contentType, size: body.length }, transferHeader)
  const path = '/' + transfer.id
  try {
    for (let offset = 0; offset < body.length; offset += transfer.chunkSize) {
      const part = body.slice(offset, offset + transfer.chunkSize)
      const payload = { offset, data: wx.arrayBufferToBase64(part.buffer) }
      // Only chunks are retried: their offsets make a lost response safe to retry.
      try { await control(path + '/chunks', 'POST', payload, transferHeader) }
      catch (error) { if (error.status) throw error; await delay(500); await control(path + '/chunks', 'POST', payload, transferHeader) }
    }
    await control(path + '/run', 'POST', {}, transferHeader)
    const deadline = Date.now() + 150000
    let status
    do {
      if (Date.now() > deadline) throw new Error('云端处理时间较长，请稍后查询任务结果')
      await delay(800)
      status = await control(path, 'GET', undefined, transferHeader)
      if (status.state === 'failed') throw new Error(status.error)
    } while (status.state !== 'completed')
    if (!Number.isSafeInteger(status.size) || status.size < 0 || status.size > 110 * 1024 * 1024) throw new Error('云端结果大小无效')
    const output = new Uint8Array(status.size)
    for (let offset = 0; offset < output.length; offset += transfer.chunkSize) {
      const chunk = await control(path + '/chunks?offset=' + offset, 'GET', undefined, transferHeader)
      const bytes = new Uint8Array(wx.base64ToArrayBuffer(chunk.data))
      if (bytes.length !== Math.min(transfer.chunkSize, output.length - offset)) throw new Error('下载分块不完整，请重试')
      output.set(bytes, offset)
    }
    return { statusCode: status.statusCode, data: responseType === 'arraybuffer' ? output.buffer : output.length ? JSON.parse(decode(output)) : null }
  } finally {
    await control(path, 'DELETE', undefined, transferHeader).catch(() => {})
  }
}
