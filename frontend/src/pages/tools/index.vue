<template>
 <view class="pdf-screen tool-list" :style="headerPadding(24,true)">
  <PdfPageHeader :vip="bannerVip" title="好工具，都在这里。" eyebrow="你的 PDF 工具箱" subtitle="找到需要的功能，选好文件就开始。" badge="基础工具永久免费"/>
  <view class="tool-search"><text class="search-mark">⌕</text><input v-model="query" confirm-type="search" placeholder="搜索工具，如：合并、压缩、转 Word" maxlength="60"/><button v-if="query" class="clear-search" aria-label="清空搜索" @click="query=''">×</button></view>
  <view class="tabs"><button :class="{active:!onlyFavorites}" @click="onlyFavorites=false">全部工具</button><button :class="{active:onlyFavorites}" @click="onlyFavorites=true">我的收藏 {{saved.length}}</button></view>
  <view class="category-tabs"><button v-for="group in groups" :key="group.id" :class="{chosen:category===group.id}" @click="category=group.id">{{group.title}}</button></view>
  <view class="list-meta">{{query?'搜索结果':onlyFavorites?'已收藏工具':'全部工具'}} · {{items.length}} 项</view>
  <view v-if="!items.length" class="tools-empty"><view>{{query?'没有找到相关工具':'还没有收藏的工具'}}</view><text>{{query?'换个关键词试试，例如「图片」或「页码」。':'点击工具右侧的星标，把常用工具放在这里。'}}</text><button v-if="query" class="ui-button ui-button-secondary" @click="query='';category='all';onlyFavorites=false">查看全部工具</button></view>
  <view v-for="tool in items" :key="tool.id" class="tool-row"><view class="tool-open" @click="openPdfTool(tool.id)"><PdfIcon :label="tool.label" :color="tool.color"/><view class="tool-copy"><view>{{tool.title}}</view><text>{{tool.hint}}</text></view></view><button :class="['favorite',{saved:saved.includes(tool.id)}]" :aria-label="(saved.includes(tool.id)?'取消收藏':'收藏')+tool.title" @click="toggle(tool.id)">{{saved.includes(tool.id)?'★':'☆'}}</button></view>
 </view>
</template>
<script setup>
import {bannerVip,refreshBannerVip} from '../../lib/vip-banner'
import {nextTick,ref,computed} from 'vue'
import {onTabItemTap,onShow,onShareAppMessage,onAddToFavorites} from '@dcloudio/uni-app'
import PdfIcon from '../../components/PdfIcon.vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import {headerPadding} from '../../lib/layout'
import {pdfTools,openPdfTool,favorites,toggleFavorite,pdfShare} from '../../lib/pdf-tools'
onShow(refreshBannerVip)
onTabItemTap(()=>nextTick(()=>uni.pageScrollTo({scrollTop:0,duration:0})))
const onlyFavorites=ref(false),query=ref(''),saved=ref([])
const category=ref('all')
const groups=[{id:'all',title:'全部'},{id:'convert',title:'格式转换'},{id:'organize',title:'页面整理'},{id:'edit',title:'优化编辑'},{id:'security',title:'安全保护'}]
const toolGroups={convert:['pdf-txt','pdf-word','word-pdf','images','pdf-images','excel-pdf','ppt-pdf','markdown','extract-images'],organize:['merge','split','rotate','extract','remove-pages','reorder'],edit:['compress','remove-watermark','watermark','page-numbers','ocr','crop','sign','repair','compare'],security:['encrypt','decrypt','redact']}
const items=computed(()=>pdfTools.filter(t=>(category.value==='all'||toolGroups[category.value].includes(t.id))&&(!onlyFavorites.value||saved.value.includes(t.id))&&(!query.value.trim()||[t.title,t.hint,t.label].join(' ').toLowerCase().includes(query.value.trim().toLowerCase()))))
onShow(()=>saved.value=favorites());onShareAppMessage(()=>pdfShare());onAddToFavorites(()=>({title:'登科喵PDF · 工具箱'}))
function toggle(id){saved.value=toggleFavorite(id)}
</script>
<style scoped>
.category-tabs{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:24rpx}.category-tabs button{margin:0;padding:12rpx 18rpx;border-radius:40rpx;background:#fff;color:#68758b;font-size:23rpx;line-height:1.5;border:1rpx solid #e3e7ed}.category-tabs button::after{border:0}.category-tabs button.chosen{background:#243c61;color:#fff;border-color:#243c61}

.tool-search{display:flex;align-items:center;gap:16rpx;margin:28rpx 0 20rpx;padding:0 20rpx;min-height:88rpx;border:1rpx solid #e5edf7;border-radius:16rpx;background:#fff}.search-mark{font-size:40rpx;color:#73839b;line-height:1}.tool-search input{flex:1;min-width:0;height:88rpx;font-size:26rpx;color:#172b4d}.clear-search{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;margin:0;padding:0;background:transparent;font-size:34rpx;color:#73839b}.tabs{display:flex;gap:8rpx;padding:6rpx;border-radius:16rpx;background:#eaf0f8}.tabs button{flex:1;min-height:76rpx;display:flex;align-items:center;justify-content:center;border-radius:12rpx;font-size:28rpx;color:#73839b;background:transparent;margin:0;padding:12rpx}.tabs .active{background:#fff;color:#246bfd;font-weight:600;box-shadow:0 2rpx 8rpx #17346508}.list-meta{font-size:24rpx;color:#73839b;margin:24rpx 2rpx 16rpx}.tool-row{display:flex;align-items:center;gap:12rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:18rpx;padding:22rpx 16rpx 22rpx 20rpx;margin-bottom:14rpx}.tool-open{display:flex;gap:20rpx;align-items:center;flex:1;min-width:0}.tool-copy{flex:1;min-width:0}.tool-copy>view{font-size:28rpx;font-weight:600}.tool-copy>text{display:block;font-size:24rpx;font-weight:400;color:#73839b;margin-top:6rpx;line-height:1.65}.favorite{width:76rpx;height:88rpx;display:flex;align-items:center;justify-content:center;margin:0;padding:0;background:transparent;color:#9aaac0;font-size:40rpx;flex-shrink:0}.favorite.saved{color:#246bfd}.tools-empty{padding:64rpx 24rpx;text-align:center;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx}.tools-empty>view{font-size:30rpx;font-weight:600}.tools-empty>text{display:block;color:#73839b;font-size:26rpx;margin-top:12rpx;line-height:1.8}
</style>
