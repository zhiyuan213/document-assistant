export const pdfTools = [
  { id: 'pdf-word', title: 'PDF 转 Word', label: 'W', color: '#087bff', hint: '提取 PDF 文字生成可编辑 Word，复杂排版和扫描件暂不支持。', accept: ['pdf'] },
  { id: 'word-pdf', title: 'Word 转 PDF', label: 'PDF', color: '#ff4b55', hint: '将 Word 文档转换为 PDF；不同字体可能影响排版。', accept: ['doc', 'docx'] },
  { id: 'merge', title: 'PDF 合并', label: '合', color: '#913bff', hint: '将 2 至 10 个 PDF 按选择的顺序合并。', accept: ['pdf'] },
  { id: 'split', title: 'PDF 拆分', label: '分', color: '#00bb70', hint: '逐页拆分或按页码范围拆分，打包 ZIP 下载。', accept: ['pdf'] },
  { id: 'compress', title: 'PDF 压缩', label: '压', color: '#ff941a', hint: '优化 PDF 内图片；纯文字文档可能无法继续缩小。', accept: ['pdf'] },
  { id: 'images', title: '图片转 PDF', label: '图', color: '#02bfd0', hint: '将 JPG、PNG 图片按顺序生成 PDF。', accept: ['jpg', 'jpeg', 'png'] },
  { id: 'pdf-images', title: 'PDF 转图片', label: 'IMG', color: '#ffa114', hint: '逐页生成 JPG 或 PNG，打包 ZIP 下载，最多 50 页。', accept: ['pdf'] },
  { id: 'pdf-txt', title: 'PDF 转 TXT', label: 'TXT', color: '#388bcb', hint: '提取文字生成TXT文件，保留基本换行，不保留图片与排版；无文字层页面自动OCR，扫描识别最多20页。', accept: ['pdf'] },
  { id: 'watermark', title: 'PDF 加水印', label: '印', color: '#ff3297', hint: '为 PDF 每一页添加半透明文字或图片水印。', accept: ['pdf'] },
  { id: 'remove-watermark', title: 'PDF 去水印', label: '净', color: '#399a9a', hint: '选择独立水印候选对象，预览确认后生成新PDF；不支持扫描件融合水印。', accept: ['pdf'] },
  { id: 'encrypt', title: 'PDF 加密', label: '锁', color: '#9a36ef', hint: '设置打开密码，使用 AES-256 加密。', accept: ['pdf'] },
  { id: 'decrypt', title: 'PDF 解密', label: '开', color: '#058aff', hint: '输入有效密码解除保护；不破解未知密码。', accept: ['pdf'] },
  { id: 'rotate', title: 'PDF 旋转', label: '转', color: '#00a6a6', hint: '顺时针旋转全部页面或指定页面，保留文字与图片。', accept: ['pdf'] },
  { id: 'extract', title: '提取 PDF 页面', label: '取', color: '#087bff', hint: '将指定页码按填写顺序提取为一个 PDF。', accept: ['pdf'] },
  { id: 'remove-pages', title: '删除 PDF 页面', label: '删', color: '#ff4b55', hint: '删除不需要的页面，至少保留一页。', accept: ['pdf'] },
  { id: 'reorder', title: '调整 PDF 页序', label: '序', color: '#913bff', hint: '填写全部页码的新顺序，例如 3,1,2；每页仅出现一次。', accept: ['pdf'] },
  { id: 'page-numbers', title: 'PDF 添加页码', label: '码', color: '#ff941a', hint: '在顶部或底部居中添加数字页码；暂不支持含旋转页面的文件。', accept: ['pdf'] },
  { id: 'excel-pdf', title: 'Excel 转 PDF', label: 'X', color: '#00a968', hint: '转换 XLS/XLSX，按工作簿打印设置分页，字体可能影响布局。', accept: ['xls','xlsx'] },
  { id: 'ppt-pdf', title: 'PPT 转 PDF', label: 'P', color: '#ff7b32', hint: '转换 PPT/PPTX 幻灯片；动画不保留，字体可能影响布局。', accept: ['ppt','pptx'] },
  { id: 'ocr', title: 'PDF 文字识别', label: 'OCR', color: '#087bff', hint: '识别中英文扫描件，生成可搜索 PDF，最多20页；模糊文字可能识别错误。', accept: ['pdf'] },
  { id: 'crop', title: 'PDF 裁剪', label: '裁', color: '#00a6a6', hint: '按四边百分比裁剪可见范围；不删除底层内容，不用于隐藏敏感信息。', accept: ['pdf'] },
  { id: 'sign', title: 'PDF 图片签名', label: '签', color: '#913bff', hint: '在指定页添加签名图片，不属于数字证书签名。暂不支持旋转页面。', accept: ['pdf'] },
  { id: 'extract-images', title: '提取 PDF 图片', label: '取图', color: '#02bfd0', hint: '提取嵌入图片为PNG并打包ZIP，最多200张；与逐页转图片不同。', accept: ['pdf'] },
  { id: 'repair', title: 'PDF 基础修复', label: '修', color: '#ff941a', hint: '尝试读取并重写PDF结构；不能保证修复严重损坏或找回缺失内容。', accept: ['pdf'] },
  { id: 'markdown', title: 'PDF 转 Markdown', label: 'MD', color: '#087bff', hint: '按页提取文字并打包MD文件；不重建表格、标题层级或图片，扫描件需先OCR。', accept: ['pdf'] },
  { id: 'compare', title: 'PDF 可视比较', label: '比', color: '#087bff', hint: '两个PDF按页并排比较，右侧像素变化标粉色；最多20页，输出图片ZIP，不做语义比较。', accept: ['pdf'] },
  { id: 'redact', title: 'PDF 永久遮盖', label: '遮', color: '#ff4b55', hint: '在每页同一区域永久涂黑并生成图片PDF，最多20页；文字不再可搜索，请保留原件。', accept: ['pdf'] },
]
export const favoriteKey = 'pdf-favorite-tools-v1'
export function favorites() { return uni.getStorageSync(favoriteKey) || [] }
export function toggleFavorite(id) {
  const ids = favorites(), next = ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]
  uni.setStorageSync(favoriteKey, next); return next
}
export function openPdfTool(id) { uni.navigateTo({ url: '/pages/pdf/work?tool=' + id }) }
export function pdfShare(tool) { return { title: tool ? `登科喵PDF · ${tool.title}` : '登科喵PDF · 简单高效的PDF工具', path: tool ? '/pages/pdf/work?tool=' + tool.id : '/pages/index/index' } }
