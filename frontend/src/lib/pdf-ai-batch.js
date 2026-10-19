// Sequential model requests: always obtain a fresh authoritative balance before the next file.
export async function runAiBatch({ files, readStatus, process, onResult = () => {}, onProgress = () => {}, active = () => true }) {
  const completed = [], failed = []
  let stopReason = '', remaining = []
  for (let index = 0; index < files.length; index++) {
    if (!active()) { remaining = files.slice(index); break }
    const file = files[index]
    onProgress(index + 1, files.length, file)
    try {
      let status
      try { status = await readStatus() } catch { stopReason = '无法确认当前额度，已暂停后续处理'; remaining = files.slice(index); break }
      if (status.enabled === false) { stopReason = 'AI 服务尚未就绪，已暂停后续处理'; remaining = files.slice(index); break }
      if (status.dailyRemaining != null && Number(status.dailyRemaining) < 1) { stopReason = '今日处理次数已用完，已停止后续文件'; remaining = files.slice(index); break }
      if (!status.testMode && (status.canUse === false || status.remainingTokenCredits != null && Number(status.remainingTokenCredits) <= 0)) {
        stopReason = '积分额度不足，已停止后续文件'; remaining = files.slice(index); break
      }
      const result = await process(file, index)
      completed.push({ file, result }); await onResult(result, file)
      if (index + 1 < files.length && !status.testMode && result.remainingTokenCredits != null && Number(result.remainingTokenCredits) <= 0) {
        stopReason = '积分额度已用完，已停止后续文件'; remaining = files.slice(index + 1); break
      }
      if (index + 1 < files.length && result.dailyRemaining != null && Number(result.dailyRemaining) < 1) {
        stopReason = '今日处理次数已用完，已停止后续文件'; remaining = files.slice(index + 1); break
      }
    } catch (error) {
      if (error.status === 402 || error.status === 429 || error.uncertain || !error.status || error.status >= 500) {
        stopReason = error.status === 402 ? '积分额度不足，已停止后续文件' : error.message || '处理状态未确认，已暂停后续文件'
        remaining = files.slice(index); break
      }
      failed.push({ file, message: error.message || '处理失败，请重试' })
    }
  }
  return { completed, failed, remaining, stopReason }
}
