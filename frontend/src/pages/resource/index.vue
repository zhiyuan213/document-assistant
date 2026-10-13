<template>
  <view class="page detail-page"
    ><view v-if="error" class="notice"
      >{{ error }}<button class="secondary" @click="load">重试</button></view
    ><view v-else-if="!resource" class="empty muted">正在加载资料…</view
    ><view v-else
      ><view class="resource-heading"
        ><view :class="['detail-file', resource.kind]">{{ resource.kind.toUpperCase() }}</view
        ><view
          ><view class="detail-name">{{ resource.name }}</view
          ><view class="detail-meta"
            >{{ resource.kind.toUpperCase() }} · {{ size(resource.size_bytes) }} ·
            {{ downloadCount(resource.downloads_count) }}</view
          ><view class="tags"
            ><text v-for="tag in resource.tags.split(',')" :key="tag">{{ tag }}</text></view
          ></view
        ></view
      ><view class="detail-actions"
        ><button class="favorite-button" :disabled="busy" @click="favorite">
          {{ resource.favorite ? '★ 已收藏' : '☆ 收藏' }}</button
        ><button class="download-button" :disabled="busy" @click="download">
          ⇩ {{ resource.is_free ? '立即下载' : '会员下载' }}
        </button></view
      ><view class="description-card"
        ><view class="section-title">资料简介</view><text>{{ resource.description }}</text></view
      ><view class="section-title related-title">相关资料推荐</view
      ><ResourceCard v-for="item in resource.related" :key="item.id" :resource="item" /><view
        v-if="!resource.related.length"
        class="muted related-empty"
        >更多相关资料正在整理中</view
      ><view class="detail-footer" @click="uni.navigateTo({ url: '/pages/downloads/index' })"
        >查看下载管理 ›</view
      ></view
    ></view
  >
</template>
<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request, ensureLogin } from '../../lib/api'
import { downloadCount } from '../../lib/catalog'
import { size, toast } from '../../lib/tools'
import { startDownload } from '../../lib/downloads'
import ResourceCard from '../../components/ResourceCard.vue'
const resource = ref(null),
  error = ref(''),
  busy = ref(false)
let id = ''
onLoad((params) => {
  id = params.id
  load()
})
async function load() {
  error.value = ''
  try {
    resource.value = await request('/resources/' + id)
  } catch (e) {
    error.value = e.message
  }
}
async function favorite() {
  busy.value = true
  try {
    await ensureLogin()
    const result = await request(
      '/resources/' + id + '/favorite',
      resource.value.favorite ? 'DELETE' : 'POST',
    )
    resource.value.favorite = result.favorite
    toast(result.favorite ? '已收藏' : '已取消收藏')
  } catch (e) {
    toast(e.message)
  } finally {
    busy.value = false
  }
}
async function download() {
  busy.value = true
  try {
    await startDownload(resource.value)
    uni.navigateTo({ url: '/pages/downloads/index' })
  } catch (e) {
    toast(e.message)
  } finally {
    busy.value = false
  }
}
</script>
<style scoped>
.detail-page {
  background: #fff;
  min-height: calc(100vh - var(--window-top, 0px));
  padding-top: 38rpx;
}
.resource-heading {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
}
.detail-file {
  position: relative;
  flex-shrink: 0;
  color: white;
  background: linear-gradient(140deg, #ff999a, #ff384d);
  width: 86rpx;
  height: 108rpx;
  border-radius: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 700;
  margin-top: 8rpx;
}
.detail-file::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 23rpx;
  height: 23rpx;
  background: #ffffff77;
  border-radius: 0 0 0 6rpx;
}
.detail-file.txt {
  background: linear-gradient(140deg, #ffc775, #ff9b31);
}
.detail-name {
  font-size: 32rpx;
  font-weight: 700;
  line-height: 1.55;
}
.detail-meta {
  font-size: 21rpx;
  color: #a29aad;
  margin-top: 14rpx;
}
.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 16rpx;
}
.tags text {
  font-size: 19rpx;
  color: #966be8;
  background: #f1eaff;
  padding: 4rpx 12rpx;
  border-radius: 9rpx;
}
.detail-actions {
  display: flex;
  gap: 20rpx;
  margin: 40rpx 0;
}
.detail-actions button {
  border-radius: 20rpx;
  padding: 22rpx 20rpx;
  margin: 0;
  font-size: 28rpx;
}
.favorite-button {
  flex: 0.8;
  border: 2rpx solid #a37aff;
  color: #8651ef;
  background: #fff;
}
.download-button {
  flex: 1.2;
  color: #fff;
  background: linear-gradient(130deg, #9565ff, #6733fb);
}
.description-card {
  background: #f8f6fc;
  border-radius: 20rpx;
  padding: 28rpx;
}
.description-card .section-title {
  font-size: 29rpx;
  margin: 0 0 16rpx;
}
.description-card > text {
  font-size: 25rpx;
  color: #8e869e;
  line-height: 1.9;
}
.related-title {
  margin-top: 60rpx;
}
.related-empty {
  padding: 24rpx 0;
}
.detail-footer {
  margin-top: 44rpx;
  color: #9579bb;
  font-size: 24rpx;
  text-align: center;
}
</style>
