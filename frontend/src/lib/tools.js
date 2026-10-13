export const tools = [
  { id: 'images', title: '图片转PDF', description: '多张图片合成PDF', icon: '▧', color: 'blue' },
  { id: 'compress', title: 'PDF压缩', description: '减小文件大小', icon: '⇣', color: 'orange' },
  { id: 'merge', title: 'PDF合并', description: '多个文件合为一份', icon: '▤', color: 'blue' },
  { id: 'ocr', title: 'OCR文字识别', description: '提取图片中的文字', icon: '文', color: 'green' },
]
export function openTool(id) {
  uni.navigateTo({ url: '/pages/process/index?tool=' + id })
}
export function size(bytes = 0) {
  return bytes >= 1048576 ? (bytes / 1048576).toFixed(1) + ' MB' : (bytes / 1024).toFixed(1) + ' KB'
}
export function toast(message) {
  uni.showToast({ title: message, icon: 'none', duration: 2500 })
}
