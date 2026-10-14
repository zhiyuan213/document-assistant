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
