import { test } from 'node:test'
import assert from 'node:assert/strict'
import { pollPdfJob } from '../frontend/src/lib/pdf-job.mjs'
const missing = () => Object.assign(new Error('not found'), { status: 404 })
test('new task can become visible after a transient 404 without resubmission', async () => {
  let reads = 0
  const progress = []
  const job = await pollPdfJob(async () => {
    if (++reads === 1) throw missing()
    return { status: 'completed', id: 'same-job', progress: 100 }
  }, { interval: 0, onProgress: value => progress.push(value.status) })
  assert.equal(job.id, 'same-job'); assert.equal(reads, 2)
  assert.deepEqual(progress, ['syncing', 'completed'])
})
test('permanent missing tasks stop after bounded retries', async () => {
  let reads = 0
  await assert.rejects(pollPdfJob(async () => { reads++; throw missing() }, { interval: 0, missingRetries: 2 }), { status: 404 })
  assert.equal(reads, 3)
})
test('processing failures and non-404 errors are not hidden', async () => {
  await assert.rejects(pollPdfJob(async () => ({ status: 'failed', error_message: 'invalid PDF' }), { interval: 0 }), /invalid PDF/)
  await assert.rejects(pollPdfJob(async () => { throw Object.assign(new Error('network'), { status: 502 }) }, { interval: 0 }), { status: 502 })
})
