// ZIP STORE writer: no recompression, upload, or third-party runtime. Append one file at a time.
const maxSize = 100 * 1024 * 1024
const utf8 = text => Uint8Array.from(unescape(encodeURIComponent(text)), c => c.charCodeAt(0))
const crcTable = Array.from({ length: 256 }, (_, n) => {
  for (let i = 0; i < 8; i++) n = n & 1 ? 0xedb88320 ^ n >>> 1 : n >>> 1
  return n >>> 0
})
export function crc32(bytes) {
  let crc = 0xffffffff
  for (const byte of bytes) crc = crcTable[(crc ^ byte) & 255] ^ crc >>> 8
  return (crc ^ 0xffffffff) >>> 0
}
function record(length) { const bytes = new Uint8Array(length); return { bytes, view: new DataView(bytes.buffer) } }
function zipEntry(name, bytes, offset) {
  const filename = utf8(name), crc = crc32(bytes)
  const local = record(30 + filename.length), central = record(46 + filename.length)
  local.view.setUint32(0, 0x04034b50, true); local.view.setUint16(4, 20, true); local.view.setUint16(6, 0x800, true)
  local.view.setUint16(12, 33, true); local.view.setUint32(14, crc, true); local.view.setUint32(18, bytes.length, true); local.view.setUint32(22, bytes.length, true)
  local.view.setUint16(26, filename.length, true); local.bytes.set(filename, 30)
  central.view.setUint32(0, 0x02014b50, true); central.view.setUint16(4, 20, true); central.view.setUint16(6, 20, true); central.view.setUint16(8, 0x800, true)
  central.view.setUint16(14, 33, true); central.view.setUint32(16, crc, true); central.view.setUint32(20, bytes.length, true); central.view.setUint32(24, bytes.length, true)
  central.view.setUint16(28, filename.length, true); central.view.setUint32(42, offset, true); central.bytes.set(filename, 46)
  return { header: local.bytes, central: central.bytes }
}
export function archiveNames(files) {
  const used = new Set()
  return files.map(file => {
    const original = String(file.name || '文件.' + file.extension).replace(/[\\/:*?"<>|\r\n]/g, '_').replace(/^\.+/, '_').slice(0, 180)
    let name = original, index = 1
    while (used.has(name.toLowerCase())) { const dot = original.lastIndexOf('.'); name = dot > 0 ? original.slice(0, dot) + '(' + index++ + ')' + original.slice(dot) : original + '(' + index++ + ')' }
    used.add(name.toLowerCase()); return name
  })
}
export function bundleKey(files) { return JSON.stringify(files.map(file => [file.path, file.name, file.expiresAt]).sort((a,b) => a[0].localeCompare(b[0]))) }
export async function createFileBundle(files, platform = wx, now = Date.now()) {
  if (files.length < 2 || files.length > 20) throw new Error('请选择2至20个文件打包')
  if (files.some(file => !file.expiresAt || file.expiresAt <= now)) throw new Error('选中的文件已过期，请重新处理')
  const expiresAt = Math.min(...files.map(file => file.expiresAt))
  const fs = platform.getFileSystemManager(), path = platform.env.USER_DATA_PATH + '/dengke-bundle-' + expiresAt + '-' + Math.random().toString(36).slice(2) + '.zip'
  const invoke = (method, options) => new Promise((resolve,reject) => fs[method]({ ...options, success: resolve, fail: () => reject(new Error('文件打包失败，请检查文件是否存在及手机存储空间')) }))
  const append = bytes => invoke('appendFile', { filePath: path, data: bytes.buffer })
  const central = [], names = archiveNames(files)
  let offset = 0
  try {
    await invoke('writeFile', { filePath: path, data: new ArrayBuffer(0) })
    for (let i = 0; i < files.length; i++) {
      if (files[i].expiresAt <= Date.now()) throw new Error('选中的文件已过期，请重新处理')
      const stat = await invoke('stat', { path: files[i].path })
      if (offset + stat.stats.size > maxSize) throw new Error('打包总大小不能超过100MB，请减少所选文件')
      const bytes = new Uint8Array((await invoke('readFile', { filePath: files[i].path })).data)
      if (offset + bytes.length > maxSize) throw new Error('打包总大小不能超过100MB，请减少所选文件')
      const entry = zipEntry(names[i], bytes, offset)
      await append(entry.header); await append(bytes); central.push(entry.central); offset += entry.header.length + bytes.length
    }
    const start = offset
    for (const entry of central) { await append(entry); offset += entry.length }
    const end = record(22); end.view.setUint32(0, 0x06054b50, true); end.view.setUint16(8, files.length, true); end.view.setUint16(10, files.length, true)
    end.view.setUint32(12, offset - start, true); end.view.setUint32(16, start, true); await append(end.bytes)
    if (expiresAt <= Date.now()) throw new Error('选中的文件已过期，请重新处理')
    return { path, name: '登科喵文件合集.zip', extension: 'zip', expiresAt }
  } catch (error) { try { fs.unlinkSync(path) } catch {} throw error }
}
export function discardBundle(file, platform = wx) { if (file?.path?.startsWith(platform.env.USER_DATA_PATH + '/dengke-bundle-')) try { platform.getFileSystemManager().unlinkSync(file.path) } catch {} }
