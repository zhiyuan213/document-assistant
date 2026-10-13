<template>
  <view class="page home-page" :style="headerPadding(32, true)">
    <view class="home-title">登科喵资料助手</view
    ><view class="home-subtitle">资料搜索 · 分类浏览 · 轻松整理</view>
    <view class="search-bar"
      ><image src="/static/icons/search-muted.png" /><input
        v-model="query"
        placeholder="搜索资料、真题、笔记…"
        confirm-type="search"
        @confirm="search" /><button @click="search">
        <image src="/static/icons/search-white.png" /></button
    ></view>
    <view class="hero" @click="searchResources()"
      ><view class="hero-copy"
        ><view>好资料，</view><view>让努力更简单！</view><view class="hero-line"></view></view
      ><text class="hero-star">✦</text><image class="hero-cat" :src="mascotSrc" mode="aspectFit"
    /></view>
    <view class="category-grid"
      ><view
        v-for="category in categories"
        :key="category.id"
        class="category-entry"
        @click="openCategory(category.id)"
        ><AppIcon :name="category.icon" :color="category.color" :size="94" /><text>{{
          category.id === 'other' ? '更多分类' : category.name
        }}</text></view
      ></view
    >
    <view class="popular-panel"
      ><view class="section-title row"
        ><text>热门资料</text><text class="more" @click="searchResources()">查看更多 ›</text></view
      ><ListState
        :error="error"
        :loading="loading"
        :empty="!items.length"
        @retry="load(true)" /><ResourceCard
        v-for="resource in items.slice(0, 4)"
        :key="resource.id"
        :resource="resource"
    /></view>
    <view class="tools-panel"
      ><view class="section-title row"
        ><text>文件处理工具</text><text class="more" @click="openTools">全部工具 ›</text></view
      ><view class="tool-strip"
        ><view v-for="tool in tools" :key="tool.id" @click="openTool(tool.id)"
          ><text class="tool-symbol">{{ tool.icon }}</text
          ><text>{{ tool.title === 'OCR文字识别' ? 'OCR识别' : tool.title }}</text></view
        ></view
      ></view
    >
    <view class="home-footer">好资料 · 好未来</view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppIcon from '../../components/AppIcon.vue'
import ResourceCard from '../../components/ResourceCard.vue'
import ListState from '../../components/ListState.vue'
import { fallbackCategories, openCategory, searchResources } from '../../lib/catalog'
import { useResourceList } from '../../lib/useResourceList'
import { request } from '../../lib/api'
import { mascotSrc } from '../../lib/brand'
import { tools, openTool } from '../../lib/tools'
import { headerPadding } from '../../lib/layout'
const query = ref(''),
  categories = ref(fallbackCategories)
const { items, error, loading, load } = useResourceList('/resources', () => ({ sort: 'popular' }))
onShow(async () => {
  load(true)
  try {
    categories.value = await request('/categories')
  } catch {}
})
function search() {
  searchResources({ q: query.value.trim() })
}
function openTools() {
  uni.navigateTo({ url: '/pages/tools/index' })
}
</script>
<style scoped>
.home-page {
  padding: 32rpx 28rpx 40rpx;
  padding-top: calc(var(--status-bar-height, 0px) + 32rpx);
  background: linear-gradient(#fff, #faf9ff);
  min-height: 100vh;
}
.home-title {
  font-size: 40rpx;
  font-weight: 800;
  letter-spacing: -1rpx;
  color: #201e31;
}
.home-subtitle {
  font-size: 22rpx;
  color: #9390a5;
  margin-top: 10rpx;
}
.search-bar {
  display: flex;
  align-items: center;
  height: 74rpx;
  background: #f6f4fb;
  border: 1rpx solid #eae5f7;
  border-radius: 18rpx;
  overflow: hidden;
  margin-top: 30rpx;
  gap: 16rpx;
  padding-left: 22rpx;
}
.search-bar > image {
  width: 30rpx;
  height: 30rpx;
  flex-shrink: 0;
}
.search-bar input {
  flex: 1;
  min-width: 0;
  font-size: 25rpx;
}
.search-bar button {
  width: 106rpx;
  height: 74rpx;
  padding: 20rpx 30rpx;
  background: linear-gradient(130deg, #9d73ff, #6935ff);
  border-radius: 16rpx;
  color: white;
  margin: 0;
}
.search-bar button image {
  width: 40rpx;
  height: 40rpx;
}
.hero {
  height: 218rpx;
  position: relative;
  background: linear-gradient(120deg, #9563ff, #6330ff);
  border-radius: 24rpx;
  margin: 42rpx 0 32rpx;
  box-shadow: 0 10rpx 24rpx #7943ec18;
}
.hero-copy {
  position: absolute;
  left: 30rpx;
  top: 42rpx;
  color: #fff;
  font-size: 32rpx;
  font-weight: 750;
  line-height: 1.65;
  transform: rotate(-8deg);
  z-index: 1;
}
.hero-line {
  width: 242rpx;
  height: 4rpx;
  border-radius: 3rpx;
  background: #ffffffbb;
  margin-top: 8rpx;
}
.hero-cat {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 290rpx;
  height: 266rpx;
  z-index: 2;
}
.hero-star {
  position: absolute;
  right: 290rpx;
  top: 24rpx;
  color: #ffd778;
  font-size: 42rpx;
}
.category-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  row-gap: 24rpx;
  margin: 0 0 30rpx;
}
.category-entry {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 12rpx;
}
.category-entry text {
  font-size: 22rpx;
  color: #625d76;
}
.popular-panel {
  background: #fff;
  border-radius: 24rpx;
  padding: 18rpx 18rpx 0;
  box-shadow: 0 4rpx 24rpx #6745ae08;
}
.section-title {
  font-size: 30rpx;
  margin: 0;
  padding: 12rpx 0 18rpx;
}
.more {
  font-size: 22rpx;
  font-weight: 400;
  color: #9993aa;
}
.tools-panel {
  margin-top: 30rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 18rpx;
}
.tool-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
}
.tool-strip > view {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  color: #736889;
  font-size: 20rpx;
  padding: 14rpx 0;
}
.tool-symbol {
  background: #f0eaff;
  color: #7b44f2;
  border-radius: 15rpx;
  width: 62rpx;
  height: 62rpx;
  text-align: center;
  line-height: 62rpx;
  font-size: 32rpx;
  font-weight: 700;
}
.home-footer {
  text-align: center;
  font-size: 22rpx;
  color: #b4a9c9;
  margin-top: 30rpx;
}
</style>
