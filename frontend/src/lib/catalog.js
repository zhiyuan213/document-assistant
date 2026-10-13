export const fallbackCategories = [
  { id: 'graduate', name: '考研资料', prefix: '考研', color: 'red', icon: 'exam' },
  { id: 'civil', name: '公考资料', prefix: '公考', color: 'blue', icon: 'briefcase' },
  { id: 'teacher', name: '教师资格', prefix: '教资', color: 'orange', icon: 'badge' },
  { id: 'english', name: '英语资料', prefix: '英语', color: 'purple', icon: 'language' },
  { id: 'college', name: '大学资料', prefix: '大学', color: 'green', icon: 'cap' },
  { id: 'career', name: '职业考试', prefix: '职业', color: 'violet', icon: 'book' },
  { id: 'templates', name: '实用模板', prefix: '实用', color: 'indigo', icon: 'document' },
  { id: 'other', name: '其他资料', prefix: '资料', color: 'gray', icon: 'grid' },
]
export const topics = [
  { id: 'papers', name: '真题试卷', suffix: '真题', icon: 'document', color: 'purple' },
  { id: 'notes', name: '学习笔记', suffix: '笔记', icon: 'notes', color: 'violet' },
  { id: 'study', name: '复习资料', suffix: '资料', icon: 'book', color: 'pink' },
  { id: 'schools', name: '院校资料', suffix: '院校', icon: 'school', color: 'green' },
  { id: 'courses', name: '专业课资料', suffix: '课程', icon: 'school', color: 'red' },
  { id: 'experience', name: '经验分享', suffix: '经验', icon: 'badge', color: 'orange' },
]
export function params(values) {
  return Object.entries(values)
    .filter(([, value]) => value !== '' && value !== undefined)
    .map(([key, value]) => encodeURIComponent(key) + '=' + encodeURIComponent(value))
    .join('&')
}
export function searchResources(values = {}) {
  uni.navigateTo({ url: '/pages/search/index?' + params(values) })
}
export function openResource(id) {
  uni.navigateTo({ url: '/pages/resource/index?id=' + encodeURIComponent(id) })
}
export function openCategory(id) {
  uni.setStorageSync('libraryCategory', id)
  uni.switchTab({ url: '/pages/categories/index' })
}
export function downloadCount(value = 0) {
  const n = Number(value)
  return n >= 10000 ? (n / 10000).toFixed(1) + '万次下载' : n + '次下载'
}
