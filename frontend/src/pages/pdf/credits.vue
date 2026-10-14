<template><view class="credits"><view v-if="billingEnabled===false"><view class="credits-title">免费使用 · 不限次数</view><view class="muted">当前免费使用，无需充值。</view><button @click="back">返回工具</button></view><template v-else-if="billingEnabled===true"><view class="credits-title">更多处理次数</view><view class="muted">每天免费 1 次 · 处理失败不扣次数</view><view class="package-grid"><view v-for="p in packages" :key="p.id" class="package"><text class="price">¥{{p.price}}</text><view>{{p.credits}} 次处理</view><text class="unit">每次 ¥{{p.unit}}</text><button @click="unavailable('支付')">支付待开通</button></view></view><view class="terms">当前仅每日免费次数可使用，支付尚未开通，当前不收款。免费次数按当前设备凭证计算，上海时间每日 0 点重置。套餐有效期将在支付正式开放时公布。</view></template><view v-else class="muted">{{billingError || "正在获取使用状态…"}}</view></view></template>
<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {pdfConfig} from '../../lib/pdf-api'
const billingEnabled=ref(null),billingError=ref('')
onShow(async()=>{try{billingEnabled.value=(await pdfConfig()).billingEnabled!==false}catch(e){billingError.value=e.message}})
function back(){uni.switchTab({url:'/pages/tools/index'})}
const packages=[{id:'six',price:6,credits:6,unit:'1.00'},{id:'eighteen',price:12,credits:18,unit:'0.67'}]
function unavailable(type){uni.showModal({title:type+'尚未开通',content:'正在准备微信支付商户配置，当前不会扣款。',showCancel:false})}
</script>
<style scoped>.credits{padding:45rpx 28rpx}.credits-title{font-size:40rpx;font-weight:800;margin-bottom:15rpx}.package-grid{display:grid;grid-template-columns:1fr 1fr;gap:20rpx;margin:35rpx 0}.package{text-align:center;border:2rpx solid #d8eaff;background:white;border-radius:22rpx;padding:28rpx 15rpx}.price{font-size:58rpx;color:#087bff;font-weight:800}.package>view{font-size:30rpx;font-weight:700;margin:12rpx}.unit{font-size:22rpx;color:#8a9bb1}.package button{background:#e8f2ff;color:#087bff;font-size:25rpx;padding:16rpx;border-radius:12rpx;margin-top:22rpx}.terms{font-size:23rpx;color:#7c8ba2;line-height:1.8;margin-top:30rpx}</style>
