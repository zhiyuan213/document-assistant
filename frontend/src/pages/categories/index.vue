<template>
  <view class="category-page"
    ><view v-if="error" class="notice">{{ error }}<text @click="load"> 重试</text></view
    ><view class="category-layout"
      ><scroll-view scroll-y :show-scrollbar="false" class="sidebar"
        ><view
          v-for="category in categories"
          :key="category.id"
          :class="['side-item', { active: selected === category.id }]"
          @click="selected = category.id"
          >{{ category.name }}</view
        ></scroll-view
      ><view class="topic-area"
        ><view class="topic-grid"
          ><view
            v-for="topic in topics"
            :key="topic.id"
            class="topic-card"
            @click="searchResources({ category: selected, topic: topic.id })"
            ><AppIcon :name="topic.icon" :color="topic.color" :size="86" /><view>{{
              selected === 'graduate' && ['papers', 'notes'].includes(topic.id)
                ? current.prefix + topic.suffix
                : topic.name
            }}</view></view
          ></view
        ><view class="category-count"
          >{{ current.resource_count ?? '—' }} 份资料 · 持续整理更新</view
        ><button class="secondary" @click="searchResources({ category: selected })">
          查看全部资料
        </button></view
      ></view
    ></view
  >
</template>
<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '../../components/AppIcon.vue'
import { fallbackCategories, topics, searchResources } from '../../lib/catalog'
import { request } from '../../lib/api'
const categories = ref(fallbackCategories),
  selected = ref('graduate'),
  error = ref('')
const current = computed(
  () => categories.value.find((item) => item.id === selected.value) || categories.value[0],
)
onShow(() => {
  const value = uni.getStorageSync('libraryCategory')
  if (value) selected.value = value
  load()
})
async function load() {
  error.value = ''
  try {
    categories.value = await request('/categories')
  } catch (e) {
    error.value = e.message
  }
}
</script>
<style scoped>
.category-page {
  background: #fff;
  height: calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px));
}
.category-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}
.sidebar {
  width: 208rpx;
  background: #f8f7fc;
  flex-shrink: 0;
  padding-top: 20rpx;
  height: 100%;
  box-sizing: border-box;
}
.side-item {
  font-size: 25rpx;
  color: #7e7890;
  padding: 25rpx 20rpx;
  margin: 4rpx 0;
  position: relative;
}
.side-item.active {
  color: #783ef4;
  background: linear-gradient(90deg, #ece2ff, #f5f1ff);
  font-weight: 650;
  border-radius: 0 24rpx 24rpx 0;
  margin-right: 14rpx;
}
.side-item.active::before {
  content: '';
  width: 5rpx;
  background: #7945ff;
  position: absolute;
  left: 0;
  top: 12rpx;
  bottom: 12rpx;
  border-radius: 5rpx;
}
.topic-area {
  flex: 1;
  padding: 30rpx 24rpx;
  min-width: 0;
}
.topic-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 26rpx 18rpx;
}
.topic-card {
  background: #faf9fc;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 28rpx 4rpx;
  gap: 24rpx;
  font-size: 25rpx;
  color: #3e394f;
}
.category-count {
  font-size: 21rpx;
  color: #ada4be;
  text-align: center;
  margin-top: 38rpx;
}
.topic-area button {
  font-size: 24rpx;
  padding: 18rpx;
}
</style>
