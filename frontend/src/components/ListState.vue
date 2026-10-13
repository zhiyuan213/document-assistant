<template>
  <view v-if="error" class="list-state"
    ><view>{{ error }}</view
    ><button class="secondary" @click="$emit('retry')">重试</button></view
  ><view v-else-if="loading && empty" class="list-state muted">正在加载资料…</view
  ><view v-else-if="empty" class="list-state"
    ><view class="empty-star">✧</view><view>{{ emptyText }}</view
    ><view class="muted">{{ hint }}</view></view
  >
</template>
<script setup>
defineProps({
  error: String,
  loading: Boolean,
  empty: Boolean,
  emptyText: { type: String, default: '暂时没有相关资料' },
  hint: { type: String, default: '试试其他关键词或分类' },
})
defineEmits(['retry'])
</script>
<style scoped>
.list-state {
  text-align: center;
  padding: 64rpx 28rpx;
  color: #8b849f;
  font-size: 26rpx;
  line-height: 1.8;
}
.empty-star {
  font-size: 70rpx;
  color: #bba2f5;
}
.list-state .muted {
  font-size: 23rpx;
  margin-top: 12rpx;
}
.list-state button {
  max-width: 300rpx;
}
</style>
