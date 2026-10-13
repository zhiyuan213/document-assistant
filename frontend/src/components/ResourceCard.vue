<template>
  <view class="resource-row">
    <view :class="['resource-file', resource.kind]" @click="openResource(resource.id)"
      ><text>{{ label }}</text></view
    >
    <view class="resource-main" @click="openResource(resource.id)"
      ><view class="resource-name">{{ resource.name }}</view
      ><view class="resource-meta"
        ><text>{{ resource.kind.toUpperCase() }}</text
        >{{ downloadCount(resource.downloads_count) }}</view
      ></view
    >
    <view v-if="action === 'download'" class="resource-action"
      ><image src="/static/icons/cloud-purple.png" @click.stop="download" /><text>{{
        resource.is_free ? '免费' : '会员'
      }}</text></view
    >
    <text v-else-if="action === 'menu'" class="resource-menu" @click.stop="$emit('menu', resource)"
      >···</text
    >
  </view>
</template>
<script setup>
import { computed } from 'vue'
import { openResource, downloadCount } from '../lib/catalog'
import { startDownload } from '../lib/downloads'
import { toast } from '../lib/tools'
const props = defineProps({ resource: Object, action: { type: String, default: 'download' } })
defineEmits(['menu'])
const label = computed(() =>
  props.resource.kind === 'docx' ? 'W' : props.resource.kind.toUpperCase(),
)
async function download() {
  try {
    await startDownload(props.resource)
    toast('已加入下载，请在下载管理查看')
  } catch (e) {
    toast(e.message)
  }
}
</script>
<style scoped>
.resource-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 26rpx 12rpx;
  border-bottom: 1rpx solid #f0eef7;
  background: #fff;
}
.resource-file {
  position: relative;
  width: 54rpx;
  height: 70rpx;
  flex-shrink: 0;
  border-radius: 7rpx;
  color: #fff;
  background: linear-gradient(135deg, #ff797e, #fb3448);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 13rpx;
  box-shadow: 0 4rpx 8rpx #fb344819;
}
.resource-file::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 15rpx;
  height: 15rpx;
  background: #ffffff70;
  border-radius: 0 0 0 4rpx;
}
.resource-file text {
  font-size: 16rpx;
  font-weight: 800;
}
.txt {
  background: linear-gradient(135deg, #ffc269, #ff9a24);
}
.docx {
  background: linear-gradient(135deg, #79acff, #3477ff);
}
.docx text {
  font-size: 35rpx;
}
.resource-main {
  flex: 1;
  min-width: 0;
}
.resource-name {
  font-size: 25rpx;
  font-weight: 650;
  color: #252336;
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.resource-meta {
  display: flex;
  gap: 12rpx;
  align-items: center;
  color: #9390a7;
  font-size: 21rpx;
  margin-top: 8rpx;
}
.resource-meta text {
  font-size: 17rpx;
  color: #ee6a73;
  background: #fff0f1;
  border-radius: 5rpx;
  padding: 1rpx 6rpx;
}
.resource-action {
  width: 62rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5rpx;
  color: #938bb3;
  font-size: 20rpx;
}
.resource-action image {
  width: 41rpx;
  height: 41rpx;
}
.resource-menu {
  font-size: 36rpx;
  color: #9a95b4;
  padding: 8rpx;
}
</style>
