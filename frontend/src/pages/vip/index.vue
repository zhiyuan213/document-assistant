<template>
  <view class="page"
    ><view class="vip-hero"
      ><image :src="mascotSrc" class="cat" mode="aspectFit" /><view class="title"
        >解锁登科喵高级工具</view
      ><view class="subtitle">让文件处理更高效</view></view
    ><view class="card"
      ><view class="benefits"
        ><view v-for="b in benefits" :key="b"><text class="check">✓</text>{{ b }}</view></view
      ><view class="price"><text>¥9.9</text><text class="price-note">高级工具包</text></view
      ><button class="purchase" disabled>即将开放</button
      ><view class="muted payment-note"
        >微信支付尚未接通，当前版本暂不收款。<br />权益以正式开放时的说明为准。</view
      ></view
    ><view class="notice">现在即可使用免费工具处理日常资料，每日免费次数在「我的」页面查看。</view
    ><button class="secondary" @click="uni.navigateTo({ url: '/pages/tools/index' })">
      先使用免费工具
    </button></view
  >
</template>
<script setup>
import { mascotSrc } from '../../lib/brand'
const benefits = ['高清PDF导出', '更多OCR次数', '高级处理功能', '更多资料整理工具']
</script>
<style scoped>
.vip-hero {
  text-align: center;
  padding: 30rpx 0 44rpx;
}
.cat {
  width: 230rpx;
  height: 230rpx;
  border-radius: 50%;
}
.vip-hero .title {
  font-size: 36rpx;
  margin-top: 22rpx;
}
.benefits {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28rpx 16rpx;
  font-size: 24rpx;
  padding: 18rpx 0 30rpx;
}
.check {
  display: inline-block;
  background: #3387ff;
  color: #fff;
  border-radius: 50%;
  width: 30rpx;
  height: 30rpx;
  line-height: 30rpx;
  text-align: center;
  margin-right: 12rpx;
  font-size: 21rpx;
}
.price {
  text-align: center;
  color: #b8770d;
  margin: 20rpx 0;
  font-size: 54rpx;
  font-weight: 750;
}
.price-note {
  font-size: 27rpx;
  margin-left: 24rpx;
  color: #8d652a;
}
.purchase[disabled] {
  background: #ffcf76;
  color: #80560f;
  padding: 22rpx;
  border-radius: 20rpx;
  font-weight: 650;
}
.payment-note {
  text-align: center;
  margin-top: 26rpx;
  font-size: 22rpx;
}
</style>
