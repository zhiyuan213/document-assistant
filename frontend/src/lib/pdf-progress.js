// Animate estimated stage progress one integer at a time. Only finish() permits 100%.
export function createSmoothProgress({ onChange, schedule = setTimeout, cancel = clearTimeout }) {
  let value = 0, target = 0, ceiling = 0, timer, running = false, paused = false, ending = false, resolveFinish
  function clear() { if (timer !== undefined) cancel(timer); timer = undefined }
  function settle() { const resolve = resolveFinish; resolveFinish = undefined; if (resolve) resolve() }
  function tick() {
    timer = undefined
    if (!running || paused) return
    if (value < target || value < ceiling) { value++; onChange(value) }
    if (ending && value === 100) { running = false; settle(); return }
    const delay = ending ? 20 : value < target ? 50 : 600 + Math.round(value / Math.max(1, ceiling) * 900)
    timer = schedule(tick, delay)
  }
  function wake() { clear(); if (running && !paused) timer = schedule(tick, ending ? 20 : 50) }
  return {
    start() { clear(); settle(); value = 1; target = 1; ceiling = 5; ending = false; running = true; onChange(value); wake() },
    update(next, limit = next) {
      if (!running || ending) return
      const bounded = n => Number.isFinite(Number(n)) ? Math.max(0, Math.min(99, Math.floor(Number(n)))) : 0
      target = Math.max(target, bounded(next))
      ceiling = Math.max(value, target, bounded(limit))
      // Frequent polling must not postpone a scheduled frame indefinitely.
      if (timer === undefined && !paused) wake()
    },
    finish() {
      if (!running) return Promise.resolve()
      if (ending) return Promise.reject(new Error('Progress already finishing'))
      ending = true; target = ceiling = 100
      if (paused) { value = 100; onChange(value); running = false; clear(); return Promise.resolve() }
      return new Promise(resolve => { resolveFinish = resolve; wake() })
    },
    pause() { paused = true; clear(); if (ending) { running = false; settle() } },
    resume() { paused = false; wake() },
    stop() { running = false; clear(); settle() }
  }
}
