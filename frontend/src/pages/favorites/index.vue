<template>
  <view class="page favorites-page"
    ><view class="row favorite-heading"
      ><text class="collection-label">我的资料收藏</text
      ><text class="manage" @click="manage = !manage">{{ manage ? '完成' : '管理' }}</text></view
    ><view class="favorite-tabs"
      ><text :class="{ active: !category }" @click="selectCategory('')">全部</text
      ><text :class="{ active: category === 'graduate' }" @click="selectCategory('graduate')"
        >考研资料</text
      ><text :class="{ active: category === 'templates' }" @click="selectCategory('templates')"
        >实用模板</text
      ></view
    ><ListState
      :error="error"
      :loading="loading"
      :empty="!items.length"
      empty-text="还没有收藏资料"
      hint="找到喜欢的资料，点一下星星就能收藏"
      @retry="load(true)"
    /><ResourceCard
      v-for="resource in items"
      :key="resource.id"
      :resource="resource"
      :action="manage ? 'menu' : 'download'"
      @menu="remove"
    /><button
      v-if="items.length < total"
      class="secondary"
      :disabled="loading"
      @click="load(false)"
    >
      加载更多</button
    ><button
      v-if="!items.length && !error && !loading"
      class="secondary"
      @click="uni.switchTab({ url: '/pages/categories/index' })"
    >
      去发现资料</button
    ><view v-if="items.length" class="favorite-total">已收藏 {{ total }} 份资料</view></view
  >
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ResourceCard from '../../components/ResourceCard.vue'
import ListState from '../../components/ListState.vue'
import { useResourceList } from '../../lib/useResourceList'
import { request } from '../../lib/api'
import { toast } from '../../lib/tools'
const category = ref(''),
  manage = ref(false)
const { items, total, error, loading, load } = useResourceList(
  '/favorites',
  () => ({ category: category.value }),
  true,
)
onShow(() => load(true))
function selectCategory(id) {
  category.value = id
  load(true)
}
function remove(resource) {
  uni.showActionSheet({
    itemList: ['取消收藏'],
    success: async () => {
      try {
        await request('/resources/' + resource.id + '/favorite', 'DELETE')
        load(true)
      } catch (e) {
        toast(e.message)
      }
    },
  })
}
</script>
<style scoped>
.favorites-page {
  background: #fff;
  min-height: calc(100vh - var(--window-top, 0px) - var(--window-bottom, 0px));
  padding-top: 26rpx;
}
.favorite-heading {
  font-size: 24rpx;
  color: #a098af;
}
.collection-label {
  font-weight: 500;
}
.manage {
  color: #8c67d1;
}
.favorite-tabs {
  display: flex;
  background: #f6f2fc;
  border-radius: 40rpx;
  margin: 26rpx 0;
  padding: 6rpx;
  gap: 10rpx;
}
.favorite-tabs text {
  flex: 1;
  text-align: center;
  border-radius: 36rpx;
  color: #9a91ac;
  font-size: 23rpx;
  padding: 13rpx 6rpx;
}
.favorite-tabs .active {
  background: linear-gradient(120deg, #a071ff, #7542f8);
  color: #fff;
}
.favorite-total {
  text-align: center;
  color: #b2a6c0;
  font-size: 23rpx;
  margin: 34rpx 0;
}
</style>
