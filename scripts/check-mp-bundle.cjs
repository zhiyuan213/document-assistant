const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '../frontend/dist/build/mp-weixin')
const config = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
const failures = []
for (const page of config.pages) {
  for (const ext of ['js', 'json', 'wxml', 'wxss']) {
    if (!fs.existsSync(path.join(root, page + '.' + ext))) failures.push('Missing page: ' + page + '.' + ext)
  }
}
// Animated accordions must unmount their closed wrappers; hidden alone can
// be overridden by layout styles on mini-program view elements.
const stepPanel = fs.readFileSync(path.join(root, 'components/PdfStepPanel.wxml'), 'utf8')
if (!/wx:if=/.test(stepPanel) || /\shidden=/.test(stepPanel)) failures.push('AI step panel must use conditional rendering, not hidden')
let checked = 0
function scan(dir) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const file = path.join(dir, entry.name)
    if (entry.isDirectory()) { scan(file); continue }
    if (entry.name.endsWith('.wxss')) {
      const css = fs.readFileSync(file, 'utf8')
      // WXSS rejects an implicit universal selector before a pseudo-class.
      // Vue/Vite accepts it, so catch it in the compiled mini-program bundle.
      if (/[>+~]\s*:/.test(css)) failures.push('Unsupported WXSS selector: ' + path.relative(root, file))
    }
    if (!entry.name.endsWith('.js')) continue
    checked++
    const source = fs.readFileSync(file, 'utf8')
    for (const match of source.matchAll(/require\(["'](\.[^"']+)["']\)/g)) {
      const target = path.resolve(path.dirname(file), match[1])
      if (!fs.existsSync(target) && !fs.existsSync(target + '.js')) failures.push('Missing module: ' + path.relative(root, file) + ' -> ' + match[1])
    }
  }
}
scan(root)
for (const tab of config.tabBar?.list || []) {
  for (const icon of [tab.iconPath, tab.selectedIconPath]) if (!fs.existsSync(path.join(root, icon))) failures.push('Missing tab icon: ' + icon)
}
if (!fs.existsSync(path.join(root, 'lib/pdf-job.js'))) failures.push('Missing compatibility module lib/pdf-job.js')
if (failures.length) { console.error(failures.join('\n')); process.exit(1) }
console.log(`Mini program bundle verified: ${config.pages.length} pages, ${checked} JS modules, all relative requires resolved`)
