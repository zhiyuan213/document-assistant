import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
let source = await readFile(new URL('../frontend/src/lib/pdf-api.js', import.meta.url), 'utf8')
source = source.replace("import { baseURL } from './api'", "const baseURL = 'https://test.invalid'")
source = source.replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g, '')
const { openSavedPdf, savePdf } = await import('data:text/javascript,' + encodeURIComponent(source))
const file = { path: '/test/result.zip', name: 'result.zip', extension: 'zip' }
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
