import { baseURL } from './api'
import { cloudEnabled, cloudRequest } from './cloud-transport'
function request(options) {
  if (cloudEnabled()) return cloudRequest({ ...options, url: options.url.slice(baseURL.length) })
  return new Promise((resolve, reject) => uni.request({ ...options, success: resolve, fail: e => reject(networkError(e)) }))
}
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
  return request({ url: baseURL + '/api/pdf' + path, method, data, timeout: 120000,
    header: { Authorization: 'Bearer ' + (uni.getStorageSync(tokenKey) || ''), ...header },
  }).then(r => { if (r.statusCode >= 200 && r.statusCode < 300) return r.data; const e = new Error(r.data?.message || '请求失败，请重试'); e.status = r.statusCode; throw e })
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
export async function submitPdf(tool, files, options, endpoint = '/jobs') {
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
  return raw(endpoint, 'POST', body.buffer, { 'Content-Type': 'multipart/form-data; boundary=' + boundary })
}
async function postPdfAi(path, mode, files, prompt, page, targetLanguage, requestId = '') {
  await visitor()
  const boundary = 'DengkePdfAi' + Date.now(), chunks = [], utf8 = s => Uint8Array.from(unescape(encodeURIComponent(s)), c => c.charCodeAt(0))
  for (const [index, file] of files.entries()) {
    chunks.push(utf8(`--${boundary}\r\nContent-Disposition: form-data; name="files"; filename="ai-input-${index}.pdf"\r\nContent-Type: application/pdf\r\n\r\n`), await bytes(file.path), utf8('\r\n'))
  }
  for (const [key, value] of Object.entries({ mode, prompt, page, targetLanguage, requestId })) chunks.push(utf8(`--${boundary}\r\nContent-Disposition: form-data; name="${key}"\r\n\r\n${value}\r\n`))
  chunks.push(utf8(`--${boundary}--\r\n`))
  const body = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0)); let offset = 0
  for (const chunk of chunks) { body.set(chunk, offset); offset += chunk.length }
  return raw(path, 'POST', body.buffer, { 'Content-Type': 'multipart/form-data; boundary=' + boundary })
}
export function estimatePdfAi(mode, files, prompt = '', page = 1, targetLanguage = 'zh-CN') { return postPdfAi('/ai/estimate', mode, files, prompt, page, targetLanguage) }
export function submitPdfAi(mode, files, prompt = '', page = 1, targetLanguage = 'zh-CN', requestId = '') { return postPdfAi('/ai', mode, files, prompt, page, targetLanguage, requestId) }
export async function savePdf(job) {
  const url = baseURL + '/api/pdf/jobs/' + job.id + '/file', header = { Authorization: 'Bearer ' + uni.getStorageSync(tokenKey) }, name = job.name || '登科喵结果.' + job.extension
  // #ifdef H5
  const response = await fetch(url, { headers: header }); if (!response.ok) throw new Error('文件已过期或下载失败')
  const object = URL.createObjectURL(await response.blob()), a = document.createElement('a'); a.href = object; a.download = name; a.click(); setTimeout(() => URL.revokeObjectURL(object), 30000)
  // #endif
  // #ifdef MP-WEIXIN
  const path = wx.env.USER_DATA_PATH + '/dengke-result-' + job.id + '.' + job.extension
  const fs = wx.getFileSystemManager()
  let exists = false
  try { fs.accessSync(path); exists = true } catch {}
  if (!exists) {
    // Download to a named file directly; ZIP is shared rather than opened as a document.
    let result
    try { if (cloudEnabled()) throw new Error('cloud download'); result = await new Promise((resolve, reject) => uni.downloadFile({ url, header, filePath: path, timeout: 120000, success: resolve, fail: reject })) }
    catch {
      // request and downloadFile have separate domain lists in WeChat.
      const response = await request({ url, header, responseType: 'arraybuffer', timeout: 120000 })
      if (response.statusCode !== 200) throw new Error(response.statusCode === 410 ? '服务器临时文件已清理，请重新处理' : response.statusCode === 404 ? '结果已过期或不存在，请重新处理' : '下载失败（HTTP ' + response.statusCode + '），请重试')
      await new Promise((resolve, reject) => fs.writeFile({ filePath: path, data: response.data, success: resolve, fail: e => reject(fileError('保存文件', e)) }))
      return { path, name, extension: job.extension }
    }
    if (result.statusCode !== 200) throw new Error(result.statusCode === 410 ? '服务器临时文件已清理，请重新处理' : result.statusCode === 404 ? '结果已过期或不存在，请重新处理' : '下载失败（HTTP ' + result.statusCode + '），请重试')
    const downloaded = result.filePath || result.tempFilePath
    if (downloaded !== path) await new Promise((resolve, reject) => fs.copyFile({ srcPath: downloaded, destPath: path, success: resolve, fail: e => reject(fileError('保存文件', e)) }))
  }
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
    if (['zip','txt'].includes(file.extension)) {
      if (typeof native.shareFileMessage !== 'function') { reject(new Error('当前微信版本不支持发送文件，请更新微信')); return }
      native.shareFileMessage({ filePath: file.path, fileName: file.name, success: resolve, fail: e => reject(fileError('发送文件', e)) })
    } else {
      native.openDocument({ filePath: file.path, fileType: file.extension, showMenu: true, success: resolve, fail: e => reject(fileError('打开文档', e)) })
    }
  })
  // #endif
}

// Native file dialogs must start directly from a user tap.
export function shareSavedFile(file, platform) {
 if(file.expiresAt && file.expiresAt<=Date.now())return Promise.reject(new Error('文件已超过30分钟，请重新处理'))
 // #ifdef MP-WEIXIN
 const native=platform||wx
 return new Promise((resolve,reject)=>{
  if(typeof native.shareFileMessage!=='function'){reject(new Error('当前微信版本不支持转发文件，请更新微信'));return}
  native.shareFileMessage({filePath:file.path,fileName:file.name,success:resolve,fail:e=>reject(fileError('转发文件',e))})
 })
 // #endif
}
export function saveFileToPhone(file, platform) {
 // #ifdef MP-WEIXIN
 const native=platform||wx
 return new Promise((resolve,reject)=>{
  const explain=()=>{
   uni.showModal({title:'手机文件保存方式',content:'当前微信环境不支持直接下载到手机文件夹。可点击“转发给微信好友”，发送到文件传输助手后打开，再使用文件菜单中提供的保存功能。也可以先预览文件。',confirmText:'预览文件',cancelText:'知道了',success:r=>{
    if(r.confirm && ['pdf','docx'].includes(file.extension))openSavedPdf(file,native).catch(e=>{if(!e.cancelled)uni.showModal({title:'无法预览',content:e.message,showCancel:false})})
   }})
   resolve({unsupported:true})
  }
  if(typeof native.saveFileToDisk==='function' && (!native.canIUse || native.canIUse('saveFileToDisk'))){
   native.saveFileToDisk({filePath:file.path,success:resolve,fail:e=>{
    const message=String(e?.errMsg||'').toLowerCase()
    if(/not support|not available|not implemented|only support/.test(message))explain()
    else reject(fileError('保存文件',e))
   }})
  }else explain()
 })
 // #endif
}

export async function exportAiReport(content, format, translationJobId = '', mode = '') {
 await visitor()
 const translated=Boolean(translationJobId)
 const url=baseURL+'/api/pdf/ai/'+(translated?'translations/'+encodeURIComponent(translationJobId)+'/file?format='+encodeURIComponent(format):'export'),header={Authorization:'Bearer '+uni.getStorageSync(tokenKey),'Content-Type':'application/json'},name=(translated?'PDF文档译文':mode==='mindmap'?'AI脑图':mode==='ppt'?'AI演示文稿':'AI总结报告')+'.'+format
 // #ifdef H5
 const response=await fetch(url,{method:translated?'GET':'POST',headers:header,...(translated?{}:{body:JSON.stringify({content,format,mode})})})
 if(!response.ok){const detail=await response.json().catch(()=>({}));throw new Error(detail.message||'导出失败，请重试')}
 const object=URL.createObjectURL(await response.blob()),a=document.createElement('a');a.href=object;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(object),30000)
 // #endif
 // #ifdef MP-WEIXIN
 const response=await request({url,method:translated?'GET':'POST',header,...(translated?{}:{data:{content,format,mode}}),responseType:'arraybuffer',timeout:120000})
 if(response.statusCode!==200)throw new Error('导出失败（HTTP '+response.statusCode+'），请稍后重试')
 const path=wx.env.USER_DATA_PATH+'/ai-report-'+Date.now()+'.'+format
 await new Promise((resolve,reject)=>wx.getFileSystemManager().writeFile({filePath:path,data:response.data,success:resolve,fail:e=>reject(fileError('保存文件',e))}))
 return {path,name,extension:format}
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
