<template>
  <view class="page"
    ><view class="row"
      ><view class="title">我的文件</view
      ><text class="refresh" @click="load(true)">刷新 ↻</text></view
    ><view class="tabs"
      ><text
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ active: kind === tab.id }"
        @click="filter(tab.id)"
        >{{ tab.name }}</text
      ></view
    ><view v-if="error" class="notice"
      >{{ error }}<button class="secondary" @click="retry">登录并重试</button></view
    ><view v-else-if="loading && !items.length" class="empty muted">正在加载文件…</view
    ><view v-else-if="!items.length" class="card empty"
      ><view class="empty-icon">▤</view><view>还没有处理过的文件</view
      ><view class="subtitle">生成PDF或识别文字后，会自动保存在这里</view
      ><button class="secondary" @click="uni.navigateTo({ url: '/pages/tools/index' })">
        去处理资料
      </button></view
    ><view v-for="file in items" :key="file.id" class="file-card"
      ><view :class="['file-icon', file.kind]">{{ file.kind.toUpperCase() }}</view
      ><view class="file-info" @click="open(file)"
        ><view class="file-name">{{ file.name }}</view
        ><view class="muted">{{ size(file.size_bytes) }} · {{ date(file.created_at) }}</view></view
      ><text class="dots" @click="menu(file)">···</text></view
    ><button
      v-if="items.length < total"
      class="secondary"
      :loading="loading"
      :disabled="loading"
      @click="load(false)"
    >
      加载更多</button
    ><view v-if="items.length" class="end">共 {{ total }} 份资料</view></view
  >
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request, ensureLogin, login, download } from '../../lib/api'
import { size, toast } from '../../lib/tools'
const tabs = [
    { id: 'all', name: '全部' },
    { id: 'pdf', name: 'PDF' },
    { id: 'txt', name: '文字' },
  ],
  kind = ref('all'),
  items = ref([]),
  total = ref(0),
  page = ref(1),
  loading = ref(false),
  error = ref('')
onShow(() => load(true))
async function load(reset) {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    await ensureLogin()
    const next = reset ? 1 : page.value + 1,
      r = await request('/files?kind=' + kind.value + '&page=' + next)
    items.value = reset ? r.items : [...items.value, ...r.items]
    total.value = r.total
    page.value = next
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
async function retry() {
  try {
    await login()
    await load(true)
  } catch (e) {
    toast(e.message)
  }
}
function filter(value) {
  if (loading.value) return
  kind.value = value
  items.value = []
  load(true)
}
function date(value) {
  return String(value).replace('T', ' ').slice(0, 16)
}
async function open(file) {
  try {
    await download(file)
  } catch (e) {
    toast(e.message)
  }
}
function menu(file) {
  uni.showActionSheet({
    itemList: ['下载 / 打开', '删除文件'],
    success: (r) => {
      if (r.tapIndex === 0) open(file)
      else
        uni.showModal({
          title: '删除文件',
          content: '删除后无法恢复，确定删除这份资料？',
          success: async (c) => {
            if (c.confirm)
              try {
                await request('/files/' + file.id, 'DELETE')
                await load(true)
              } catch (e) {
                toast(e.message)
              }
          },
        })
    },
  })
}
</script>
<style scoped>
.refresh {
  font-size: 24rpx;
  color: #7e95b5;
}
.tabs {
  display: flex;
  gap: 30rpx;
  margin: 30rpx 0;
}
.tabs text {
  padding: 14rpx 30rpx;
  color: #8195b4;
  border-radius: 40rpx;
  font-size: 25rpx;
}
.tabs .active {
  background: #eee6ff;
  color: #713cff;
  font-weight: 600;
}
.file-card {
  display: flex;
  align-items: center;
  padding: 24rpx;
  background: #fff;
  border-radius: 20rpx;
  gap: 22rpx;
  margin-bottom: 18rpx;
}
.file-icon {
  font-size: 20rpx;
  background: #ff6371;
  color: white;
  padding: 22rpx 10rpx;
  border-radius: 10rpx;
}
.txt {
  background: #8d68ff;
}
.file-info {
  flex: 1;
  min-width: 0;
}
.file-name {
  font-weight: 600;
  font-size: 26rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 8rpx;
}
.dots {
  padding: 12rpx;
  color: #96abc7;
  font-weight: bold;
}
.end {
  color: #a0aec3;
  font-size: 23rpx;
  text-align: center;
  padding: 24rpx;
}
</style>
