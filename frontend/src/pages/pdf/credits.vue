<template>
 <view class="pdf-screen credits">
  <view class="ui-heading">我的 AI 额度</view><view class="ui-subtitle">按需使用，让每一份文档更有价值。</view>
  <view class="balance-card ui-card"><view class="balance-heading"><text>{{membership?.active?'AI 月卡生效中':'当前可用额度'}}</text><text v-if="membership?.active" class="member-tag">月卡会员</text></view><view class="balances"><view><text>会员月额度</text><view>{{formatCredits(aiStatus.monthlyRemainingTokenCredits)}}</view></view><view><text>加油包额度</text><view>{{formatCredits(aiStatus.topupRemainingTokenCredits)}}</view></view></view><view v-if="membership?.active" class="balance-foot">月卡有效至 {{expiryText(membership.expiresAt)}}（北京时间）</view><view v-else class="balance-foot">加油包长期有效，会员到期后仍可使用。</view></view>
  <view v-if="aiStatus.testMode" class="ui-notice">测试模式已开启，无需购买即可使用 AI，不扣用户额度。</view>
  <view v-if="billingEnabled===false" class="ui-notice">购买暂未开放。基础 PDF 工具永久免费，不限次数。</view>
  <template v-else-if="billingEnabled===true">
   <view class="ui-section-title">选择适合你的额度</view>
   <view :class="['package',{selected:selectedPackage==='monthly'}]" @click="selectedPackage='monthly'"><view class="package-top"><view class="package-name">AI 月卡<text>适合日常使用</text></view><view class="package-price"><text>¥</text>{{monthlyPrice}}<text>/ 月</text></view></view><view class="package-bottom"><view>每周期 {{formatCredits(aiStatus.monthlyTokenCredits ?? 2000000)}} 加权额度<text>随会员周期刷新，到期剩余额度不结转</text></view><view class="package-check">{{selectedPackage==='monthly'?'✓':''}}</view></view></view>
   <view :class="['package',{selected:selectedPackage==='ai-topup'}]" @click="selectedPackage='ai-topup'"><view class="package-top"><view class="package-name">AI 加油包<text>适合临时加量</text></view><view class="package-price"><text>¥</text>9.90<text>/ 包</text></view></view><view class="package-bottom"><view>增加 2,000,000 加权额度<text>长期有效，可叠加，会员到期后仍可使用</text></view><view class="package-check">{{selectedPackage==='ai-topup'?'✓':''}}</view></view></view>
   <PdfAiBilling />
   <button class="ui-button" :disabled="!paymentEnabled || purchasing || aiStatus.testMode" :loading="purchasing" @click="buy(selectedPackage)">{{purchasing?'正在发起支付…':aiStatus.testMode?'测试模式 · 无需购买':paymentEnabled?'微信支付 ¥'+(selectedPackage==='monthly'?monthlyPrice:'9.90'):'购买暂未开放'}}</button>
   <view class="purchase-note">主动购买 · 不自动续费 · 不自动扣款</view><view class="ui-caption limits">每个账号每日最多 50 次。单次最多 30 页、15,000 个文字字符；扫描件最多 5 页。</view>
  </template>
  <view v-else class="ui-notice">{{billingError || '正在获取额度信息…'}}</view>
  <button class="ui-button ui-button-secondary" @click="back">返回 AI 读 PDF</button>
 </view>
</template>
<script setup>
import {ref} from 'vue'
import PdfAiBilling from '../../components/pdf-ai-billing.vue'
import {onShow} from '@dcloudio/uni-app'
import {pdfRequest} from '../../lib/pdf-api'
const selectedPackage=ref('monthly')
const billingEnabled=ref(null),billingError=ref(''),membership=ref(null),paymentEnabled=ref(false),purchasing=ref(false),monthlyPrice=ref('9.90'),aiStatus=ref({})
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
onShow(refresh)
async function refresh(){try{let state=await pdfRequest('/quota');billingEnabled.value=state.billingEnabled!==false;paymentEnabled.value=state.paymentEnabled===true&&miniProgram
  if(paymentEnabled.value){try{await pdfRequest('/login','POST',{code:await login()});state=await pdfRequest('/quota')}catch{}}
  aiStatus.value=await pdfRequest('/ai/status');membership.value=state.membership;monthlyPrice.value=(Number(state.monthlyPriceFen||990)/100).toFixed(2);billingError.value=''
 }catch(e){billingError.value=e.message;billingEnabled.value=null;membership.value=null;paymentEnabled.value=false}}
function expiryText(value){return value?value.slice(0,16).replace('T',' '):''}
function back(){uni.switchTab({url:'/pages/pdf/ai'})}
function login(){return new Promise((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('微信身份校验失败，请重试')),fail:()=>reject(new Error('微信登录失败，请重试'))}))}
function requestPayment(params){return new Promise((resolve,reject)=>wx.requestPayment({...params,success:resolve,fail:reject}))}
function formatCredits(value){return Number(value||0).toLocaleString('zh-CN')}
async function buy(packageId){if(purchasing.value||!paymentEnabled.value)return;purchasing.value=true
 try{await pdfRequest('/login','POST',{code:await login()});const order=await pdfRequest('/purchase','POST',{packageId});const {orderId,...paymentParams}=order
  try{await requestPayment(paymentParams)}catch(e){if(String(e?.errMsg||'').toLowerCase().includes('cancel'))return;throw new Error('支付流程未完成，可稍后刷新会员状态')}
  let paid=false;for(let i=0;i<12;i++){await new Promise(resolve=>setTimeout(resolve,1000));const state=await pdfRequest('/purchase/'+order.orderId);if(state.paid){paid=true;break}}
  await refresh();uni.showModal({title:paid?'购买成功':'正在确认支付',content:paid?(packageId==='ai-topup'?'200 万加油包额度已到账，可继续使用 AI。':'月卡会员已开通。'):'支付结果正在由微信确认，请稍后刷新此页面查看额度。',showCancel:false})
 }catch(e){uni.showModal({title:'无法完成购买',content:e.message||'请稍后重试',showCancel:false})}finally{purchasing.value=false}}
</script>
<style scoped>
.balance-card{margin-top:28rpx}.balance-heading{display:flex;align-items:center;justify-content:space-between;gap:12rpx;font-size:28rpx;font-weight:600}.member-tag{background:#edf4ff;color:#246bfd;border-radius:8rpx;padding:4rpx 12rpx;font-size:24rpx;font-weight:400}.balances{display:flex;gap:20rpx;margin:22rpx 0}.balances>view{flex:1;min-width:0}.balances>view>text{font-size:24rpx;color:#73839b}.balances>view>view{font-size:36rpx;line-height:1.6;font-weight:700;color:#246bfd}.balance-foot{font-size:24rpx;color:#73839b;border-top:1rpx solid #edf2f8;padding-top:16rpx;line-height:1.8}.package{padding:24rpx;border:2rpx solid #e5edf7;border-radius:20rpx;background:#fff;margin-bottom:16rpx}.package.selected{background:#edf4ff;border-color:#246bfd}.package-top{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.package-name{font-size:30rpx;font-weight:600}.package-name>text{display:block;font-size:24rpx;font-weight:400;color:#73839b;margin-top:4rpx}.package-price{font-size:40rpx;font-weight:700;color:#246bfd;white-space:nowrap}.package-price>text{font-size:24rpx;font-weight:400;margin:0 4rpx}.package-price>text:last-child{color:#73839b}.package-bottom{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-top:22rpx;padding-top:18rpx;border-top:1rpx solid #e1eaf7}.package-bottom>view:first-child{font-size:26rpx}.package-bottom text{display:block;font-size:24rpx;color:#73839b;margin-top:4rpx}.package-check{display:flex;align-items:center;justify-content:center;flex-shrink:0;width:40rpx;height:40rpx;border:2rpx solid #cad7e9;border-radius:50%;font-size:26rpx;color:transparent}.selected .package-check{background:#246bfd;border-color:#246bfd;color:#fff}.purchase-note{text-align:center;font-size:24rpx;color:#73839b;margin:16rpx 0}.limits{text-align:center;margin:18rpx 0 24rpx}
</style>
