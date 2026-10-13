import { ref } from 'vue'
import { request, ensureLogin } from './api'
import { params } from './catalog'
export function useResourceList(endpoint, filters, authenticated = false) {
  const items = ref([]),
    total = ref(0),
    page = ref(0),
    loading = ref(false),
    error = ref('')
  let generation = 0
  async function load(reset = true) {
    if (!reset && loading.value) return
    const sequence = reset ? ++generation : generation,
      next = reset ? 1 : page.value + 1
    loading.value = true
    error.value = ''
    try {
      if (authenticated) await ensureLogin()
      const response = await request(endpoint + '?' + params({ ...filters(), page: next }))
      if (sequence !== generation) return
      items.value = reset ? response.items : [...items.value, ...response.items]
      total.value = response.total
      page.value = next
    } catch (e) {
      if (sequence === generation) error.value = e.message
    } finally {
      if (sequence === generation) loading.value = false
    }
  }
  return { items, total, page, loading, error, load }
}
