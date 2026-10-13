<template>
  <view class="page search-page" :style="headerPadding(20, true)"
    ><view class="search-header"
      ><text class="back" @click="uni.navigateBack()">‹</text
      ><view class="search-field"
        ><image src="/static/icons/search-muted.png" /><input
          v-model="query"
          placeholder="搜索资料、真题、笔记"
          confirm-type="search"
          @confirm="load(true)"
        /><button @click="load(true)">搜索</button></view
      ></view
    ><view class="filter-tabs"
      ><text
        v-for="tab in filters"
        :key="tab.id"
        :class="{ active: topic === tab.id }"
        @click="filter(tab.id)"
        >{{ tab.name }}</text
      ></view
    ><view class="results-summary row"
      ><text>共找到 {{ total }} 个相关资料</text
      ><text @click="toggleSort">{{ sort === 'popular' ? '综合排序' : '最新上架' }} ›</text></view
    ><ListState
      :error="error"
      :loading="loading"
      :empty="!items.length"
      @retry="load(true)"
    /><ResourceCard v-for="resource in items" :key="resource.id" :resource="resource" /><button
      v-if="items.length < total"
      class="secondary"
      :disabled="loading"
      @click="load(false)"
    >
      加载更多
    </button></view
  >
</template>
<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import ResourceCard from '../../components/ResourceCard.vue'
import ListState from '../../components/ListState.vue'
import { useResourceList } from '../../lib/useResourceList'
import { headerPadding } from '../../lib/layout'
const query = ref(''),
  category = ref(''),
  topic = ref(''),
  sort = ref('popular')
const filters = [
  { id: '', name: '全部' },
  { id: 'papers', name: '真题' },
  { id: 'notes', name: '笔记' },
  { id: 'study', name: '资料' },
  { id: 'experience', name: '经验' },
]
const { items, total, error, loading, load } = useResourceList('/resources', () => ({
  q: query.value.trim(),
  category: category.value,
  topic: topic.value,
  sort: sort.value,
}))
onLoad((params) => {
  query.value = params.q || ''
  category.value = params.category || ''
  topic.value = params.topic || ''
  load(true)
})
function filter(id) {
  topic.value = id
  load(true)
}
function toggleSort() {
  sort.value = sort.value === 'popular' ? 'recent' : 'popular'
  load(true)
}
</script>
<style scoped>
.search-page {
  background: #fff;
  min-height: 100vh;
  padding: 20rpx 28rpx;
  padding-top: calc(var(--status-bar-height, 0px) + 20rpx);
}
.search-header {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.back {
  font-size: 58rpx;
  color: #362e49;
  line-height: 1;
}
.search-field {
  display: flex;
  align-items: center;
  gap: 14rpx;
  background: #f6f3fb;
  border-radius: 34rpx;
  height: 72rpx;
  flex: 1;
  min-width: 0;
  padding-left: 20rpx;
}
.search-field > image {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}
.search-field input {
  flex: 1;
  min-width: 0;
  font-size: 24rpx;
}
.search-field button {
  font-size: 24rpx;
  width: 122rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 34rpx;
  margin: 0;
  color: white;
  background: linear-gradient(130deg, #a079ff, #7438ff);
  padding: 0;
}
.filter-tabs {
  display: flex;
  justify-content: space-between;
  margin-top: 30rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #eee9f6;
  color: #9b93a8;
  font-size: 25rpx;
}
.filter-tabs text {
  position: relative;
  padding-bottom: 10rpx;
}
.filter-tabs .active {
  font-weight: 650;
  color: #713cff;
}
.filter-tabs .active::after {
  content: '';
  position: absolute;
  bottom: -11rpx;
  left: 0;
  right: 0;
  height: 4rpx;
  border-radius: 4rpx;
  background: #8554fc;
}
.results-summary {
  font-size: 22rpx;
  color: #9a91aa;
  padding: 24rpx 0 6rpx;
}
</style>
