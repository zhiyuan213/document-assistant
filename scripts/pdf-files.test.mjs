import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
let source = await readFile(new URL('../frontend/src/lib/pdf-api.js', import.meta.url), 'utf8')
source = source.replace("import { baseURL } from './api'", "const baseURL = 'https://test.invalid'")
source = source.replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g, '')
const { openSavedPdf, savePdf, shareSavedFile, saveFileToPhone } = await import('data:text/javascript,' + encodeURIComponent(source))
const file = { path: '/test/result.zip', name: 'result.zip', extension: 'zip' }
test('translated text files use sharing because WeChat openDocument does not support txt',async()=>{
 let shared=false
 await openSavedPdf({...file,extension:'txt'}, {shareFileMessage(options){shared=true;options.success({})},openDocument(){throw Error('txt cannot be opened as a document')}})
 assert.equal(shared,true)
})
test('ZIP sharing is invoked synchronously from the second tap', async () => {
  let invoked = false
  const promise = openSavedPdf(file, { shareFileMessage(options) { invoked = true; queueMicrotask(() => options.success({})) } })
  assert.equal(invoked, true); await promise
})
test('document opening carries the correct extension and menu', async () => {
  let received
  await openSavedPdf({ ...file, extension: 'docx' }, { openDocument(options) { received = options; options.success({}) } })
  assert.equal(received.fileType, 'docx'); assert.equal(received.showMenu, true)
})
test('cancelling file sharing is distinguishable from a failure', async () => {
  await assert.rejects(openSavedPdf(file, { shareFileMessage(options) { options.fail({ errMsg: 'shareFileMessage:fail cancel' }) } }), error => error.cancelled === true)
})
test('download preparation reuses existing local files and does not share automatically', async () => {
  const previousWx = globalThis.wx, previousUni = globalThis.uni
  let copied = false, shared = false
  globalThis.wx = { env: { USER_DATA_PATH: '/test' }, getFileSystemManager: () => ({ accessSync() {}, copyFile() { copied = true } }), shareFileMessage() { shared = true } }
  globalThis.uni = { getStorageSync: () => 'test-token', downloadFile: options => options.success({ statusCode: 200, tempFilePath: '/test/download.tmp' }) }
  try {
    const ready = await savePdf({ id: 'same-job', extension: 'zip' })
    assert.equal(ready.path, '/test/dengke-result-same-job.zip'); assert.equal(copied, false); assert.equal(shared, false)
  } finally { globalThis.wx = previousWx; globalThis.uni = previousUni }
})

test('ZIP download writes directly to a named local ZIP', async () => {
  const oldWx = globalThis.wx, oldUni = globalThis.uni
  let downloaded
  globalThis.wx = { env: { USER_DATA_PATH: '/test' }, getFileSystemManager: () => ({ accessSync() { throw Error('missing') }, copyFile() { assert.fail('direct download should not be copied') } }) }
  globalThis.uni = { getStorageSync: () => 'test-token', downloadFile(options) { downloaded = options; options.success({ statusCode: 200, filePath: options.filePath }) } }
  try { const ready = await savePdf({ id: 'zip-job', extension: 'zip' }); assert.equal(downloaded.filePath, ready.path); assert.ok(ready.path.endsWith('.zip')); assert.equal(downloaded.header.Authorization, 'Bearer test-token') }
  finally { globalThis.wx = oldWx; globalThis.uni = oldUni }
})

test('failed download transport falls back to binary request and writes ZIP bytes', async () => {
  const oldWx = globalThis.wx, oldUni = globalThis.uni
  const data = Uint8Array.from([80,75,3,4]).buffer
  let written
  globalThis.wx = { env: { USER_DATA_PATH: '/test' }, getFileSystemManager: () => ({ accessSync() { throw Error('missing') }, writeFile(options) { written = options; options.success({}) } }) }
  globalThis.uni = { getStorageSync: () => 'test-token', downloadFile(options) { options.fail({ errMsg: 'url not in domain list' }) }, request(options) { assert.equal(options.responseType, 'arraybuffer'); options.success({ statusCode: 200, data }) } }
  try { const ready = await savePdf({ id: 'fallback-job', extension: 'zip' }); assert.equal(written.filePath, ready.path); assert.equal(written.data, data) }
  finally { globalThis.wx = oldWx; globalThis.uni = oldUni }
})

test('expired fallback response is never saved as a ZIP', async () => {
  const oldWx = globalThis.wx, oldUni = globalThis.uni
  globalThis.wx = { env: { USER_DATA_PATH: '/test' }, getFileSystemManager: () => ({ accessSync() { throw Error('missing') }, writeFile() { assert.fail('must not write error response') } }) }
  globalThis.uni = { getStorageSync: () => 'test-token', downloadFile(options) { options.fail({}) }, request(options) { options.success({ statusCode: 410, data: new ArrayBuffer(0) }) } }
  try { await assert.rejects(savePdf({ id: 'expired-job', extension: 'zip' }), /临时文件已清理/) }
  finally { globalThis.wx = oldWx; globalThis.uni = oldUni }
})


test('PDF forwarding opens the native friend chooser directly', async()=>{
 let options
 const pending=shareSavedFile({...file,extension:'pdf'}, {shareFileMessage(o){options=o;o.success({})}})
 assert.equal(options.filePath,file.path);assert.equal(options.fileName,file.name);await pending
})
test('local saving invokes the native disk dialog and preserves cancellation',async()=>{
 let called=false
 await saveFileToPhone(file,{saveFileToDisk(o){called=true;o.success({})},canIUse:()=>true})
 assert.equal(called,true)
 await assert.rejects(saveFileToPhone(file,{saveFileToDisk(o){o.fail({errMsg:'cancel'})}}),e=>e.cancelled)
})
test('unsupported phone saving explains the alternative without reporting a saved file',async()=>{
 const previous=globalThis.uni;let message
 globalThis.uni={showModal(o){message=o}}
 try{const result=await saveFileToPhone(file,{});assert.equal(result.unsupported,true);assert.ok(message.content.includes('文件传输助手'))}
 finally{globalThis.uni=previous}
})


test('an exposed but unsupported disk API uses phone guidance instead of an update-WeChat error',async()=>{
 const old=globalThis.uni;let modal
 globalThis.uni={showModal(o){modal=o}}
 try{
  const result=await saveFileToPhone({...file,extension:'docx'},{canIUse:()=>true,saveFileToDisk(o){o.fail({errMsg:'saveFileToDisk:fail not supported'})}})
  assert.equal(result.unsupported,true);assert.ok(modal.content.includes('文件传输助手'));assert.equal(modal.confirmText,'预览文件')
 }finally{globalThis.uni=old}
})

test('expired exported files never open the native sharing dialog',async()=>{
 let called=false
 await assert.rejects(shareSavedFile({...file,expiresAt:Date.now()-1},{shareFileMessage(){called=true}}),/30/)
 assert.equal(called,false)
})
