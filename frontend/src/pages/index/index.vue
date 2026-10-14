<template>
  <view class="pdf-home" :style="headerPadding(20,true)">
    <view class="home-actions"><button open-type="share">↗ 分享</button><button @click="collectApp">☆ 收藏</button></view>
    <view class="home-hero"><view><view class="brand-title">登科喵PDF</view><view class="brand-tag">简单 · 高效 · 安全</view><view class="brand-desc">无需注册，选好文件即可处理。<br/>结果下载到手机，30分钟后自动清理。</view></view><image src="/static/brand/pdf-cat.png" mode="aspectFit"/></view>
    <view class="daily-line"><view><text>{{member?.active?'月卡会员':billingEnabled===false?'免费使用 · 不限次数':billingEnabled===true?'每日免费处理 3 次':'PDF 文件处理工具'}}</text><view v-if="member?.active">有效至 {{expiryText(member.expiresAt)}}（北京时间）</view><view v-if="member?.active && billingEnabled===true">今日剩余 {{remaining}} / 100 个文件</view><view v-if="member?.active && billingEnabled===false">当前免费开放 · 不限次数</view><view v-if="member && !member.active && member.expiresAt">月卡已于 {{expiryText(member.expiresAt)}} 到期</view></view><text v-if="billingEnabled===true" @click="credits">月卡 ¥9.90 ›</text></view>
    <view class="tool-grid"><view v-for="tool in pdfTools.slice(0,9)" :key="tool.id" @click="openPdfTool(tool.id)"><PdfIcon :label="tool.label" :color="tool.color"/><text>{{tool.title}}</text></view></view>
    <view class="more-tools" @click="allTools">PDF 解密 · 常用收藏 · 全部工具 ›</view>
    <view v-if="saved.length" class="saved-line"><text>常用工具</text><text v-for="id in saved.slice(0,3)" :key="id" @click="openPdfTool(id)">{{pdfTools.find(t=>t.id===id)?.title}}</text></view>
    <view class="resume-entry" @click="resume"><image src="/static/brand/pdf-cat.png" mode="aspectFit"/><view><view>登科喵简历助手</view><text>让你的简历更出色</text></view><text>›</text></view>
    <view class="home-note">文件临时处理 · 密码不落库 · 30 分钟自动清理</view>
  </view>
</template>
<script setup>
import {ref} from 'vue'
import {onShow,onShareAppMessage,onAddToFavorites} from '@dcloudio/uni-app'
import PdfIcon from '../../components/PdfIcon.vue'
import {pdfTools,openPdfTool,favorites,pdfShare} from '../../lib/pdf-tools'
import {headerPadding} from '../../lib/layout'
import {pdfRequest} from '../../lib/pdf-api'
const saved=ref([])
const billingEnabled=ref(null)
const member=ref(null),remaining=ref(0)
function expiryText(value){return value?value.slice(0,16).replace('T',' '):''}
onShow(async()=>{try{const state=await pdfRequest('/quota');billingEnabled.value=state.billingEnabled!==false;member.value=state.membership;remaining.value=state.remaining}catch{billingEnabled.value=null;member.value=null}})
onShow(()=>saved.value=favorites());onShareAppMessage(()=>pdfShare());onAddToFavorites(()=>({title:'登科喵PDF · 常用PDF工具'}))
function allTools(){uni.switchTab({url:'/pages/tools/index'})}
function credits(){uni.navigateTo({url:'/pages/pdf/credits'})}
function resume(){uni.navigateTo({url:'/pages/pdf/resume'})}
function collectApp(){uni.showModal({title:'收藏小程序',content:'点击右上角“…”菜单，将登科喵PDF添加到我的小程序；每个工具也可以单独收藏。',showCancel:false})}
</script>
<style scoped>
.pdf-home{min-height:calc(100vh - var(--window-bottom,0px));padding:20rpx 28rpx 24rpx;background:linear-gradient(140deg,#eff8ff,#fff 55%)}.home-actions{display:flex;justify-content:flex-end;gap:12rpx}.home-actions button{margin:0;padding:5rpx 14rpx;background:#ffffffcc;border-radius:30rpx;font-size:22rpx;color:#283b59}.home-hero{display:flex;align-items:center;justify-content:space-between;padding:14rpx 0 18rpx;gap:12rpx}.brand-title{font-size:44rpx;font-weight:900;letter-spacing:-1rpx;color:#10203d}.brand-tag{font-size:27rpx;font-weight:700;margin-top:16rpx}.brand-desc{font-size:21rpx;line-height:1.7;color:#72839c;margin-top:12rpx}.home-hero image{width:190rpx;height:220rpx;flex-shrink:0}.daily-line{display:flex;justify-content:space-between;background:#eaf5ff;border-radius:12rpx;color:#087bff;font-size:22rpx;padding:12rpx 18rpx;margin-bottom:16rpx}.tool-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14rpx}.tool-grid>view{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14rpx;height:146rpx;background:#fff;border:1rpx solid #edf3fb;box-shadow:0 3rpx 12rpx #0f447208;border-radius:16rpx}.tool-grid>view>text{font-size:24rpx;font-weight:650}.more-tools{font-size:23rpx;color:#087bff;text-align:center;padding:18rpx 0}.saved-line{display:flex;gap:12rpx;align-items:center;flex-wrap:wrap;font-size:21rpx;margin-bottom:14rpx}.saved-line text:not(:first-child){background:#edf6ff;border-radius:12rpx;padding:7rpx;color:#087bff}.resume-entry{display:flex;align-items:center;gap:14rpx;background:#f1e6ff;border-radius:16rpx;padding:10rpx 16rpx}.resume-entry image{width:72rpx;height:80rpx}.resume-entry>view{flex:1;font-size:26rpx;font-weight:700}.resume-entry>view>text{font-size:20rpx;font-weight:400;color:#9480aa}.resume-entry>text{font-size:38rpx;color:#9744f4}.home-note{text-align:center;font-size:19rpx;color:#98a9be;margin-top:16rpx}
</style>
