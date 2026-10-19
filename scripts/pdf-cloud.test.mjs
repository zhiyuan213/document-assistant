import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
let source = await readFile(new URL('../frontend/src/lib/cloud-transport.js', import.meta.url), 'utf8')
source = source.replaceAll('import.meta.env.VITE_CLOUD_ENV', "'pdf-env'").replaceAll('import.meta.env.VITE_CLOUD_SERVICE', "'pdf-service'").replaceAll('import.meta.env.VITE_MP_TRANSPORT', "'cloud'")
source = source.replace(/\/\/ #ifndef MP-WEIXIN[\s\S]*?\/\/ #endif/g, '')
const { cloudRequest, cloudEnabled } = await import('data:text/javascript,' + encodeURIComponent(source))
test('cloud transport handles multipart chunks and binary results without public HTTP', async () => {
  const previous = globalThis.wx
  const input = Buffer.alloc(130000, 173), result = Buffer.from('synthetic PDF result'), uploaded = []
  let chunkAttempts = 0, deleted = false
  globalThis.wx = {
    arrayBufferToBase64: b => Buffer.from(b).toString('base64'),
    base64ToArrayBuffer: b => Uint8Array.from(Buffer.from(b, 'base64')).buffer,
    cloud: { init() {}, async callContainer(o) {
      assert.equal(o.config.env, 'pdf-env'); assert.equal(o.header['X-WX-SERVICE'], 'pdf-service'); assert.ok(o.timeout < 15000)
      if (o.path === '/api/pdf/cloud-transfers') {
        assert.equal(o.data.size, input.length); assert.equal(o.data.path, '/api/pdf/jobs'); return { statusCode: 200, data: { id: 'test', chunkSize: 49152 } }
      }
      if (o.path.endsWith('/chunks') && o.method === 'POST') {
        assert.ok(Buffer.byteLength(JSON.stringify(o.data)) < 100 * 1024)
        if (++chunkAttempts === 1) throw Error('lost response')
        uploaded.push(Buffer.from(o.data.data, 'base64')); return { statusCode: 200, data: {} }
      }
      if (o.path.endsWith('/run')) return { statusCode: 200, data: { state: 'processing' } }
      if (o.path.includes('/chunks?')) return { statusCode: 200, data: { data: result.toString('base64') } }
      if (o.method === 'DELETE') { deleted = true; return { statusCode: 200, data: {} } }
      return { statusCode: 200, data: { state: 'completed', size: result.length, statusCode: 200 } }
    } }
  }
  try {
    assert.equal(cloudEnabled(), true)
    const response = await cloudRequest({ url: '/api/pdf/jobs', method: 'POST', data: Uint8Array.from(input).buffer, responseType: 'arraybuffer', header: { Authorization: 'Bearer synthetic', 'Content-Type': 'multipart/form-data; boundary=test' } })
    assert.deepEqual(Buffer.concat(uploaded), input); assert.deepEqual(Buffer.from(response.data), result); assert.equal(deleted, true)
  } finally { globalThis.wx = previous }
})
test('ordinary cloud requests preserve status and credentials and never fall back to public domain', async () => {
  const previous = globalThis.wx
  globalThis.wx = { cloud: { async callContainer(o) { assert.equal(o.header.Authorization, 'Bearer test'); return { statusCode: 401, data: { message: 'expired' } } } } }
  try {
    const response = await cloudRequest({ url: '/api/pdf/quota', header: { Authorization: 'Bearer test' } })
    assert.equal(response.statusCode, 401)
    globalThis.wx.cloud.callContainer = async () => { throw Error('unavailable') }
    await assert.rejects(cloudRequest({ url: '/api/pdf/quota' }), /云托管业务接口调用失败/)
  } finally { globalThis.wx = previous }
})
test('cloud failures expose stage and code without logging secrets or document content', async () => {
  const previous = globalThis.wx, previousWarn = console.warn
  const logs = []
  console.warn = (...args) => logs.push(args)
  globalThis.wx = { cloud: { async callContainer() { throw { errCode: -501000, errMsg: 'permission denied SECRET-DOCUMENT SECRET-TOKEN' } } } }
  try {
    await assert.rejects(cloudRequest({ url: '/api/pdf/visitor', method: 'POST', header: { Authorization: 'SECRET-TOKEN' } }), e => e.cloudCode === '-501000' && e.message.includes('设备凭证') && e.message.includes('获准调用'))
    assert.ok(!JSON.stringify(logs).includes('SECRET'))
  } finally { globalThis.wx = previous; console.warn = previousWarn }
})
