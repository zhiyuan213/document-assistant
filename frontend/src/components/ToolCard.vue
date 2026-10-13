<template>
  <view class="tool" @click="openTool(tool.id)"
    ><view :class="['icon', tool.color]">{{ tool.icon }}</view
    ><view
      ><view class="tool-title">{{ tool.title }}</view
      ><view class="description">{{ tool.description }}</view></view
    ></view
  >
</template>
<script setup>
import { openTool } from '../lib/tools'
defineProps({ tool: Object })
</script>
<style scoped>
.tool {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 26rpx 20rpx;
  border-radius: 22rpx;
  background: linear-gradient(120deg, #f0ebff, #f9f7ff);
  min-height: 140rpx;
}
.icon {
  flex-shrink: 0;
  width: 66rpx;
  height: 66rpx;
  line-height: 66rpx;
  text-align: center;
  border-radius: 16rpx;
  color: #fff;
  font-size: 36rpx;
  font-weight: bold;
}
.blue {
  background: #7b4dff;
}
.orange {
  background: #ffa647;
}
.green {
  background: #25c994;
}
.tool-title {
  font-size: 26rpx;
  font-weight: 650;
}
.description {
  color: #7c8eaa;
  font-size: 20rpx;
  margin-top: 8rpx;
}
</style>
