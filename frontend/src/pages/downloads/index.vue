<template>
  <view class="page downloads-page"
    ><view class="download-tabs"
      ><text
        v-for="tab in tabs"
        :key="tab.id"
        :class="{ active: filter === tab.id }"
        @click="filter = tab.id"
        >{{ tab.name }} {{ count(tab.id) ? '(' + count(tab.id) + ')' : '' }}</text
      ></view
    ><ListState
      :error="error"
      :loading="loading"
      :empty="!filtered.length"
      empty-text="暂无下载记录"
      hint="在资料详情页选择喜欢的文件开始下载"
      @retry="load()"
    /><view v-for="item in filtered" :key="item.id" class="download-item"
      ><view :class="['download-file', item.kind]">{{ item.kind.toUpperCase() }}</view
      ><view class="download-main"
        ><view class="download-name" @click="openResource(item.resource_id)">{{ item.name }}</view
        ><view class="download-caption"
          >{{
            item.status === 'completed'
              ? '已完成 · ' + size(item.size_bytes)
              : size(item.bytes) + ' / ' + size(item.size_bytes)
          }}<text v-if="item.status === 'paused'"> · 已暂停</text
          ><text v-if="item.status === 'failed'"> · 下载失败</text></view
        ><view v-if="item.status !== 'completed'" class="progress-track"
          ><view :style="{ width: progress(item) + '%' }"></view></view></view
      ><button class="download-control" @click="act(item)">
        {{ item.status === 'started' ? 'Ⅱ' : item.status === 'completed' ? '↗' : '▶' }}
      </button></view
    ><button
      v-if="downloadState.items.length < downloadState.total"
      class="secondary"
      :disabled="loading"
      @click="load(true)"
    >
      加载更多记录</button
    ><view v-if="downloadState.items.some((item) => item.status === 'paused')" class="download-note"
      >点击继续会重新下载该文件。</view
    ></view
  >
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import {
  downloadState,
  refreshDownloads,
  pauseDownload,
  resumeDownload,
  openDownload,
} from '../../lib/downloads'
import { openResource } from '../../lib/catalog'
import { size, toast } from '../../lib/tools'
import ListState from '../../components/ListState.vue'
const filter = ref('active'),
  loading = ref(false),
  error = ref(''),
  tabs = [
    { id: 'active', name: '下载中' },
    { id: 'completed', name: '已完成' },
    { id: 'all', name: '全部' },
  ]
const accepts = (item, value) =>
  value === 'all' ||
  (value === 'completed' ? item.status === 'completed' : item.status !== 'completed')
const filtered = computed(() => downloadState.items.filter((item) => accepts(item, filter.value)))
const count = (value) => downloadState.items.filter((item) => accepts(item, value)).length
watch(
  () => count('active'),
  (value, previous) => {
    if (previous > 0 && value === 0 && count('completed') && filter.value === 'active')
      filter.value = 'completed'
  },
)
const progress = (item) =>
  Math.min(100, Math.round(((item.bytes || 0) / Math.max(1, item.size_bytes)) * 100))
onLoad((params) => {
  if (['completed', 'all'].includes(params.filter)) filter.value = params.filter
})
onShow(() => load())
async function load(more = false) {
  loading.value = true
  error.value = ''
  try {
    await refreshDownloads(more ? downloadState.page + 1 : 1)
    if (filter.value === 'active' && !count('active') && count('completed'))
      filter.value = 'completed'
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
async function act(item) {
  try {
    if (item.status === 'started') await pauseDownload(item)
    else if (item.status === 'completed') await openDownload(item)
    else await resumeDownload(item)
  } catch (e) {
    toast(e.message)
  }
}
</script>
<style scoped>
.downloads-page {
  background: #fff;
  min-height: calc(100vh - var(--window-top, 0px));
  padding-top: 24rpx;
}
.download-tabs {
  display: flex;
  gap: 10rpx;
  background: #f7f3fc;
  border-radius: 38rpx;
  padding: 6rpx;
  margin-bottom: 30rpx;
}
.download-tabs text {
  flex: 1;
  text-align: center;
  padding: 14rpx 4rpx;
  border-radius: 34rpx;
  color: #a198ae;
  font-size: 23rpx;
}
.download-tabs .active {
  background: linear-gradient(120deg, #9c70ff, #7846f7);
  color: #fff;
}
.download-item {
  display: flex;
  gap: 22rpx;
  align-items: center;
  padding: 25rpx 4rpx;
}
.download-file {
  flex-shrink: 0;
  background: linear-gradient(140deg, #ff9e9d, #ff4553);
  width: 64rpx;
  height: 84rpx;
  border-radius: 9rpx;
  color: #fff;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}
.download-file.txt {
  background: linear-gradient(140deg, #ffca7a, #ff9c22);
}
.download-main {
  flex: 1;
  min-width: 0;
}
.download-name {
  font-size: 26rpx;
  font-weight: 650;
  line-height: 1.6;
}
.download-caption {
  font-size: 21rpx;
  color: #a39aae;
  margin: 10rpx 0;
}
.progress-track {
  height: 10rpx;
  border-radius: 8rpx;
  background: #e9e2f4;
  overflow: hidden;
}
.progress-track view {
  height: 100%;
  background: linear-gradient(90deg, #9062ff, #6e35ff);
  border-radius: 8rpx;
}
.download-control {
  flex-shrink: 0;
  border-radius: 50%;
  background: #f0e8ff;
  color: #8451f2;
  width: 52rpx;
  height: 52rpx;
  padding: 0;
  line-height: 52rpx;
  margin: 0;
  font-size: 24rpx;
}
.download-note {
  text-align: center;
  color: #a59aad;
  font-size: 22rpx;
  padding: 38rpx 0;
}
</style>
