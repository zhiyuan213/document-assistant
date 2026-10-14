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
