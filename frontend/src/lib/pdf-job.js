// Keep the previous compiled module path available for Devtools incremental caches.
import { pollPdfJob as poll } from './pdf-api'
export function pollPdfJob(read, options) { return poll(read, options) }
