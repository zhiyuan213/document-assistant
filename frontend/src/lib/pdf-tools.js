export const pdfTools = [
  { id: 'pdf-word', title: 'PDF 转 Word', label: 'W', color: '#087bff', hint: '提取 PDF 文字生成可编辑 Word，复杂排版和扫描件暂不支持。', accept: ['pdf'] },
  { id: 'word-pdf', title: 'Word 转 PDF', label: 'PDF', color: '#ff4b55', hint: '将 Word 文档转换为 PDF；不同字体可能影响排版。', accept: ['doc', 'docx'] },
  { id: 'merge', title: 'PDF 合并', label: '合', color: '#913bff', hint: '将 2 至 10 个 PDF 按选择的顺序合并。', accept: ['pdf'] },
  { id: 'split', title: 'PDF 拆分', label: '分', color: '#00bb70', hint: '逐页拆分或按页码范围拆分，打包 ZIP 下载。', accept: ['pdf'] },
  { id: 'compress', title: 'PDF 压缩', label: '压', color: '#ff941a', hint: '优化 PDF 内图片；纯文字文档可能无法继续缩小。', accept: ['pdf'] },
  { id: 'images', title: '图片转 PDF', label: '图', color: '#02bfd0', hint: '将 JPG、PNG 图片按顺序生成 PDF。', accept: ['jpg', 'jpeg', 'png'] },
  { id: 'pdf-images', title: 'PDF 转图片', label: 'IMG', color: '#ffa114', hint: '逐页生成 JPG 或 PNG，打包 ZIP 下载，最多 50 页。', accept: ['pdf'] },
  { id: 'watermark', title: 'PDF 加水印', label: '印', color: '#ff3297', hint: '为 PDF 每一页添加半透明文字或图片水印。', accept: ['pdf'] },
  { id: 'encrypt', title: 'PDF 加密', label: '锁', color: '#9a36ef', hint: '设置打开密码，使用 AES-256 加密。', accept: ['pdf'] },
  { id: 'decrypt', title: 'PDF 解密', label: '开', color: '#058aff', hint: '输入有效密码解除保护；不破解未知密码。', accept: ['pdf'] },
  { id: 'rotate', title: 'PDF 旋转', label: '转', color: '#00a6a6', hint: '顺时针旋转全部页面或指定页面，保留文字与图片。', accept: ['pdf'] },
  { id: 'extract', title: '提取 PDF 页面', label: '取', color: '#087bff', hint: '将指定页码按填写顺序提取为一个 PDF。', accept: ['pdf'] },
  { id: 'remove-pages', title: '删除 PDF 页面', label: '删', color: '#ff4b55', hint: '删除不需要的页面，至少保留一页。', accept: ['pdf'] },
  { id: 'reorder', title: '调整 PDF 页序', label: '序', color: '#913bff', hint: '填写全部页码的新顺序，例如 3,1,2；每页仅出现一次。', accept: ['pdf'] },
  { id: 'page-numbers', title: 'PDF 添加页码', label: '码', color: '#ff941a', hint: '在顶部或底部居中添加数字页码；暂不支持含旋转页面的文件。', accept: ['pdf'] },
]
export const favoriteKey = 'pdf-favorite-tools-v1'
export function favorites() { return uni.getStorageSync(favoriteKey) || [] }
export function toggleFavorite(id) {
  const ids = favorites(), next = ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]
  uni.setStorageSync(favoriteKey, next); return next
}
export function openPdfTool(id) { uni.navigateTo({ url: '/pages/pdf/work?tool=' + id }) }
export function pdfShare(tool) { return { title: tool ? `登科喵PDF · ${tool.title}` : '登科喵PDF · 简单高效的PDF工具', path: tool ? '/pages/pdf/work?tool=' + tool.id : '/pages/index/index' } }
