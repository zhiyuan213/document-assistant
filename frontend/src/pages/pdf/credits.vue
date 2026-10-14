<template>
 <view class="credits">
  <view class="credits-title">{{membership?.active?'月卡会员':'PDF 月卡'}}</view>
  <view v-if="membership?.active" class="muted">有效至 {{expiryText(membership.expiresAt)}}（北京时间）</view>
  <view v-if="billingEnabled===false" class="muted">当前免费开放，不限处理次数，无需购买。</view>
  <template v-else-if="billingEnabled===true">
   <view class="muted">非会员每日免费 3 次 · 处理失败不扣额度</view>
   <view class="package"><text class="price">¥9.90</text><view>一个月会员</view><view>每天最多处理 100 个文件</view><text class="muted">从购买生效时起，到下个月同一天同一时间到期；无对应日期时按月末计算。</text><button @click="unavailable">月卡支付待开通</button></view>
   <view class="terms">额度按北京时间每日 0 点重置。合并或图片转换按输入文件数量计算，水印素材不计入；失败退回额度。不自动续费。微信支付尚未接入，当前不收款、不发放会员。</view>
  </template>
  <view v-else class="muted">{{billingError || '正在获取使用状态…'}}</view>
  <button @click="back">返回工具</button>
 </view>
</template>
<script setup>
import {ref} from 'vue'
import {onShow} from '@dcloudio/uni-app'
import {pdfRequest} from '../../lib/pdf-api'
const billingEnabled=ref(null),billingError=ref(''),membership=ref(null)
onShow(async()=>{try{const state=await pdfRequest('/quota');billingEnabled.value=state.billingEnabled!==false;membership.value=state.membership;billingError.value=''}catch(e){billingError.value=e.message;billingEnabled.value=null;membership.value=null}})
function expiryText(value){return value?value.slice(0,16).replace('T',' '):''}
function back(){uni.switchTab({url:'/pages/tools/index'})}
function unavailable(){uni.showModal({title:'月卡支付尚未开通',content:'月卡价格为9.90元，微信支付接入后开放购买。当前不会扣款。',showCancel:false})}
</script>
<style scoped>.credits{padding:45rpx 28rpx}.credits-title{font-size:40rpx;font-weight:800;margin-bottom:15rpx}.package{text-align:center;border:2rpx solid #d8eaff;background:white;border-radius:22rpx;padding:28rpx 22rpx;margin:35rpx 0}.price{font-size:58rpx;color:#087bff;font-weight:800}.package>view{font-size:30rpx;font-weight:700;margin:12rpx}.package>text.muted{font-size:23rpx;line-height:1.8;display:block}.package button{background:#e8f2ff;color:#087bff;font-size:25rpx;padding:16rpx;border-radius:12rpx;margin-top:22rpx}.terms{font-size:23rpx;color:#7c8ba2;line-height:1.8;margin-top:30rpx}</style>
