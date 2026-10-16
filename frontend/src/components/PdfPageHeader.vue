<template>
 <view class="pdf-page-header">
  <view class="brand-row"><view class="brand-lockup"><text class="brand-mark">PDF</text><text class="brand-name">登科喵<text class="brand-light">PDF</text></text></view><PdfActions @favorite="collectApp"/></view>
  <view class="page-hero"><view class="hero-copy"><text class="hero-eyebrow">{{eyebrow}}</text><view class="hero-title">{{title}}</view><view class="hero-subtitle">{{subtitle}}</view><text v-if="badge" class="hero-badge">✓ {{badge}}</text></view><view class="hero-art"><view class="art-orbit"></view><image class="hero-mascot" src="/static/brand/pdf-cat.png" mode="aspectFit"/><text class="art-star">✦</text></view></view>
 </view>
</template>
<script setup>
import PdfActions from './PdfActions.vue'
defineProps({title:String,subtitle:String,badge:String,eyebrow:{type:String,default:'轻松处理每一份文档'}})
function collectApp(){uni.showModal({title:'收藏小程序',content:'点击右上角“…”菜单，将登科喵PDF添加到我的小程序；工具也可以单独收藏。',showCancel:false})}
</script>
<style scoped>
.brand-row{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-bottom:28rpx}.brand-lockup{display:flex;align-items:center;gap:12rpx}.brand-mark{display:flex;align-items:center;justify-content:center;width:58rpx;height:64rpx;background:#246bfd;border-radius:12rpx;color:#fff;font-size:19rpx;font-weight:800;transform:rotate(-6deg)}.brand-name{font-size:32rpx;font-weight:800;color:#202b40}.brand-light{font-weight:500;margin-left:4rpx;color:#246bfd}.page-hero{position:relative;display:flex;align-items:center;justify-content:space-between;gap:8rpx;min-height:270rpx;padding:30rpx 26rpx;margin-bottom:28rpx;background:linear-gradient(120deg,#e8f0ff,#f0f5ff 65%,#e2edff);border:1rpx solid #dbe6fa;border-radius:26rpx;overflow:hidden;box-sizing:border-box}.hero-copy{flex:1;min-width:0;z-index:1}.hero-eyebrow{font-size:21rpx;letter-spacing:2rpx;color:#5876a3}.hero-title{font-size:40rpx;line-height:1.35;font-weight:800;letter-spacing:-1rpx;color:#18345f;margin-top:12rpx;max-width:420rpx}.hero-subtitle{font-size:24rpx;line-height:1.7;color:#617594;margin-top:12rpx}.hero-badge{display:inline-block;margin-top:20rpx;padding:5rpx 12rpx;border-radius:8rpx;background:#ffffffb8;color:#2764b5;font-size:21rpx;line-height:1.6}.hero-art{position:relative;width:142rpx;height:190rpx;flex-shrink:0}.hero-mascot{position:relative;width:142rpx;height:190rpx;z-index:1}.art-orbit{position:absolute;width:180rpx;height:180rpx;border-radius:50%;background:#ffffff80;top:16rpx;left:-18rpx;border:1rpx solid #fff}.art-star{position:absolute;right:-6rpx;top:0;color:#f2ae4e;font-size:34rpx;z-index:2}
</style>
