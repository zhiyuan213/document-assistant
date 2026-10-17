// Explicit grouping also works in WeChat runtimes without full Intl support.
export function formatCount(value) {
 const number=Number(value)
 const integer=Number.isFinite(number)?Math.max(0,Math.trunc(number)):0
 return String(integer).replace(/\B(?=(\d{3})+(?!\d))/g,',')
}
