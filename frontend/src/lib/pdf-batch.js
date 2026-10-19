export function selectionLimit(tool) { return tool === 'compare' ? 2 : tool === 'merge' ? 10 : 20 }
export function groupedTool(tool, options = {}) { return tool === 'merge' || tool === 'compare' || tool === 'images' && options.imageOutput !== 'separate' }
export function outputName(source, title, extension) {
  const base = String(source || '文档').replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|\r\n]/g, '_').slice(0, 100) || '文档'
  return base + '_' + title.replace(/\s+/g, '') + '.' + extension
}
export function appendSelection(existing, incoming, tool) {
  const files = [...existing]
  for (const file of incoming) if (!files.some(item => item.path === file.path)) files.push(file)
  if (files.length > selectionLimit(tool)) throw new Error('最多选择' + selectionLimit(tool) + '个文件')
  if (files.some(file => file.size > 20 * 1024 * 1024)) throw new Error('单个文件不能超过20MB')
  if (files.reduce((sum, file) => sum + (file.size || 0), 0) > 60 * 1024 * 1024) throw new Error('本批文件总大小不能超过60MB')
  return files
}
export async function runPdfBatch({ tool, title, files, options, imageFile, watermarkPlans = {}, submit, poll, active = () => true, onProgress = () => {} }) {
  const groups = groupedTool(tool, options) ? [files] : files.map(file => [file])
  const items = [], failures = []
  for (let index = 0; index < groups.length && active(); index++) {
    const group = groups[index], source = group[0]
    const report = (progress, phase = 'processing') => onProgress({ index, total: groups.length, name: source.name, phase,
      progress: Math.round((index + progress / 100) / groups.length * 100),
      ceiling: Math.floor((index + (phase === 'uploading' ? 0.1 : progress === 100 ? 1 : 0.9)) / groups.length * 100) })
    report(0, 'uploading')
    try {
      const parameters = { ...options }
      if (tool === 'images') parameters.imageOutput = 'merged'
      if (tool === 'remove-watermark') {
        const plan = watermarkPlans[source.path]
        if (!plan?.proof || !plan.ids?.length) throw new Error('请先预览并确认此文件的去水印效果')
        parameters.candidateIds = plan.ids.join(','); parameters.previewProof = plan.proof
      }
      const imageMark = tool === 'sign' || tool === 'watermark' && parameters.watermarkMode === 'image'
      if (imageMark && !imageFile) throw new Error(tool === 'sign' ? '请选择签名图片' : '请选择水印图片')
      const input = imageMark ? [...group, imageFile] : group
      const created = await submit(tool, input, parameters)
      const job = await poll(created.id, value => report(value.progress || 0))
      if (!job || !active()) break
      items.push({ job, sources: group.map(file => file.path), name: outputName(source.name, title, job.extension) })
    } catch (error) {
      failures.push({ sources: group.map(file => file.path), name: source.name, message: error.message || '处理失败，请重试' })
    }
    report(100)
  }
  return { items, failures }
}
export async function downloadBatch(items, { save, remember, active = () => true, onProgress = () => {} }) {
  const saved = [], failures = []
  for (let index = 0; index < items.length && active(); index++) {
    const item = items[index]; onProgress(index + 1, items.length)
    try {
      const file = await save({ ...item.job, name: item.name })
      const entry = remember(file, { name: item.name, expiresAt: item.job.expiresAt || item.job.expires_at, source: 'tool', size: item.job.size_bytes, jobId: item.job.id })
      if (entry) saved.push(entry)
    } catch (error) { failures.push({ name: item.name, message: error.message || '下载失败，请重试' }) }
  }
  return { saved, failures }
}
