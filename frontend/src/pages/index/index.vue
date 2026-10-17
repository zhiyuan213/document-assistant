<template>
 <view class="pdf-screen pdf-home" :style="headerPadding(24,true)">
  <PdfPageHeader :vip="member?.active===true" title="PDF 的事，
轻松搞定。" subtitle="免费转换与编辑，常用工具都在这里。" badge="基础工具永久免费"/>
  <view class="ui-section-row"><text class="ui-section-title">常用工具</text><text class="ui-link" @click="allTools">全部工具 ›</text></view>
  <view class="ui-tool-grid"><PdfToolTile v-for="tool in pdfTools.slice(0,6)" :key="tool.id" :label="tool.label" :color="tool.color" :title="tool.title" :caption="toolCaptions[tool.id]" @click="openPdfTool(tool.id)"/></view>
  <view class="ai-entry" @click="openAi"><view class="ai-symbol">✦</view><view class="ai-entry-copy"><view>让 AI 帮你读懂 PDF <text class="ai-tag">AI</text></view><text>总结成报告，常用语言轻松翻译</text></view><text class="entry-arrow">›</text></view>
  <view class="membership-line"><text>{{member?.active?'AI 月卡有效至 '+expiryText(member.expiresAt):'AI 月卡与加油包，按用量扣额度'}}</text><text @click="credits">查看额度 ›</text></view>
  <view v-if="saved.length" class="saved-line"><text class="ui-caption">我的收藏</text><text v-for="id in saved.slice(0,3)" :key="id" @click="openPdfTool(id)">{{pdfTools.find(t=>t.id===id)?.title}}</text></view>
  <view class="home-note">无需注册 · 文件临时处理 · 结果 30 分钟后清理</view>
 </view>
</template>
<script setup>
import {nextTick,ref} from 'vue'
import {onTabItemTap,onShow,onShareAppMessage,onAddToFavorites} from '@dcloudio/uni-app'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import PdfToolTile from '../../components/PdfToolTile.vue'
import {pdfTools,openPdfTool,favorites,pdfShare} from '../../lib/pdf-tools'
import {headerPadding} from '../../lib/layout'
import {pdfRequest} from '../../lib/pdf-api'
onTabItemTap(()=>nextTick(()=>uni.pageScrollTo({scrollTop:0,duration:0})))
const toolCaptions={'pdf-word':'提取文字','word-pdf':'文档转换',merge:'多份合一',split:'按页拆分',compress:'减小体积',images:'图片成册','pdf-images':'逐页导出',watermark:'添加标记',encrypt:'密码保护'}
const saved=ref([])
const member=ref(null),monthlyPrice=ref('9.90')
function expiryText(value){return value?value.slice(0,16).replace('T',' '):''}
onShow(async()=>{try{const state=await pdfRequest('/quota');member.value=state.membership;monthlyPrice.value=(Number(state.monthlyPriceFen||990)/100).toFixed(2)}catch{member.value=null}})
onShow(()=>saved.value=favorites());onShareAppMessage(()=>pdfShare());onAddToFavorites(()=>({title:'登科喵PDF · 常用PDF工具'}))
function allTools(){uni.switchTab({url:'/pages/tools/index'})}
function credits(){uni.navigateTo({url:'/pages/pdf/credits'})}
function openAi(){uni.switchTab({url:'/pages/pdf/ai'})}
</script>
<style scoped>
.pdf-home{background:#f5f6f9}.ai-entry{display:flex;align-items:center;gap:20rpx;margin-top:24rpx;padding:24rpx;background:linear-gradient(120deg,#1d407e,#2868c9);border:1rpx solid #315c9c;border-radius:20rpx}.ai-symbol{width:72rpx;height:72rpx;display:flex;align-items:center;justify-content:center;flex-shrink:0;background:#246bfd;color:#fff;border-radius:18rpx;font-size:28rpx;font-weight:700}.ai-entry-copy{flex:1;min-width:0}.ai-entry-copy>view{font-size:30rpx;font-weight:600;color:#fff}.ai-entry-copy>text{font-size:24rpx;color:#c5d8f6}.entry-arrow{font-size:38rpx;color:#fff}.ai-tag{font-size:19rpx;padding:2rpx 8rpx;background:#ffffff25;border-radius:6rpx;margin-left:6rpx}.membership-line{display:flex;align-items:center;justify-content:space-between;gap:16rpx;padding:20rpx 2rpx;color:#73839b;font-size:24rpx}.membership-line>text:last-child{color:#246bfd;flex-shrink:0;min-height:64rpx;display:flex;align-items:center}.saved-line{display:flex;align-items:center;gap:12rpx;flex-wrap:wrap;margin:4rpx 0 24rpx}.saved-line>text:not(:first-child){font-size:24rpx;padding:8rpx 14rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:10rpx;color:#37649a}.home-note{font-size:24rpx;text-align:center;color:#8b99ae;margin:8rpx 0 0;line-height:1.8}
</style>
