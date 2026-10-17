<template>
 <view class="pdf-screen credits">
  <view class="ui-heading">我的 AI 额度</view><view class="ui-subtitle">按需使用，让每一份文档更有价值。</view>
  <view class="balance-card ui-card"><view class="balance-heading"><text>{{membership?.active?'AI 月卡生效中':'当前可用额度'}}</text><text v-if="membership?.active" class="member-tag">月卡会员</text></view><view class="balances"><view><text>会员月额度</text><view>{{formatCredits(aiStatus.monthlyRemainingTokenCredits)}}</view></view><view><text>加油包额度</text><view>{{formatCredits(aiStatus.topupRemainingTokenCredits)}}</view></view></view><view v-if="membership?.active" class="balance-foot">月卡有效至 {{expiryText(membership.expiresAt)}}（北京时间）</view><view v-else class="balance-foot">加油包长期有效，会员到期后仍可使用。</view></view>
  <view v-if="aiStatus.testMode" class="ui-notice">测试模式已开启，无需购买即可使用 AI，不扣用户额度。</view>
  <view v-if="billingEnabled!==null && paymentUnavailableReason" class="ui-notice">{{paymentUnavailableReason}}<text class="refresh-link" @click="refresh">刷新购买状态 ›</text></view>
  <template v-if="billingEnabled!==null">
   <view class="ui-section-title">选择适合你的额度</view>
   <view :class="['package',{selected:selectedPackage==='monthly'}]" @click="selectedPackage='monthly'"><view class="package-top"><view class="package-name">AI 月卡<text>适合日常使用</text></view><view class="package-price"><text>¥</text>{{monthlyPrice}}<text>/ 月</text></view></view><view class="package-bottom"><view>每周期 {{formatCredits(aiStatus.monthlyTokenCredits ?? 2000000)}} 加权额度<text>随会员周期刷新，到期剩余额度不结转</text></view><view class="package-check">{{selectedPackage==='monthly'?'✓':''}}</view></view></view>
   <view :class="['package',{selected:selectedPackage==='ai-topup'}]" @click="selectedPackage='ai-topup'"><view class="package-top"><view class="package-name">AI 加油包<text>适合临时加量</text></view><view class="package-price"><text>¥</text>9.90<text>/ 包</text></view></view><view class="package-bottom"><view>增加 2,000,000 加权额度<text>长期有效，可叠加，会员到期后仍可使用</text></view><view class="package-check">{{selectedPackage==='ai-topup'?'✓':''}}</view></view></view>
   <PdfAiBilling />
   <button class="ui-button" :disabled="!billingEnabled || !paymentEnabled || purchasing || aiStatus.testMode" :loading="purchasing" @click="buy(selectedPackage)">{{purchasing?(pendingOrder?'正在确认支付结果…':'正在发起支付…'):aiStatus.testMode?'测试模式 · 无需购买':billingEnabled && paymentEnabled?(pendingOrder?'查询支付结果':'微信支付 ¥'+(selectedPackage==='monthly'?monthlyPrice:'9.90')):'购买暂未开放'}}</button>
   <view class="purchase-note">主动购买 · 不自动续费 · 不自动扣款</view><view class="ui-caption limits">每个账号每日最多 50 次。单次最多 30 页、15,000 个文字字符；扫描件最多 5 页。</view>
  </template>
  <view v-else class="ui-notice">{{billingError || '正在获取额度信息…'}}</view>
  <view v-if="pendingOrder" class="ui-notice">有一笔购买结果待确认，请先查询，避免重复付款。<button class="ui-button ui-button-secondary" :disabled="purchasing" @click="checkPending">查询支付结果</button></view>
  <button class="ui-button ui-button-secondary" @click="back">返回 AI 读 PDF</button>
 </view>
</template>
<script setup>
import {ref,computed} from 'vue'
import PdfAiBilling from '../../components/pdf-ai-billing.vue'
import {onShow} from '@dcloudio/uni-app'
import {pdfRequest} from '../../lib/pdf-api'
import {requestPdfPayment} from '../../lib/pdf-payment'
const selectedPackage=ref('monthly'),paymentProvider=ref(''),pendingOrder=ref(null)
const pendingKey='pdf-pending-purchase-v1'
const billingEnabled=ref(null),billingError=ref(''),membership=ref(null),paymentEnabled=ref(false),purchasing=ref(false),monthlyPrice=ref('9.90'),aiStatus=ref({})
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
const paymentUnavailableReason=computed(()=>aiStatus.value.testMode?'':!miniProgram?'请在微信小程序中购买 AI 额度。':billingEnabled.value===false?'AI 额度购买暂未开启，请稍后再试。':!paymentEnabled.value?'微信支付暂不可用，请稍后再试。':'')
onShow(()=>{pendingOrder.value=uni.getStorageSync(pendingKey)||null;refresh()})
async function refresh(){billingError.value='';try{let state=await pdfRequest('/quota');billingEnabled.value=state.billingEnabled!==false;paymentProvider.value=state.paymentProvider||'';paymentEnabled.value=state.paymentEnabled===true&&miniProgram&&['virtual','wechat'].includes(paymentProvider.value)
  if(miniProgram&&state.wechatLoginEnabled){try{await pdfRequest('/login','POST',{code:await login()});state=await pdfRequest('/quota')}catch{}}
  aiStatus.value=await pdfRequest('/ai/status');membership.value=state.membership;monthlyPrice.value=(Number(state.monthlyPriceFen||990)/100).toFixed(2);billingError.value=''
 }catch(e){billingError.value=e.message;billingEnabled.value=null;membership.value=null;paymentEnabled.value=false}}
function expiryText(value){return value?value.slice(0,16).replace('T',' '):''}
function back(){uni.switchTab({url:'/pages/pdf/ai'})}
function login(){return new Promise((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('微信身份校验失败，请重试')),fail:()=>reject(new Error('微信登录失败，请重试'))}))}
function formatCredits(value){return Number(value||0).toLocaleString('zh-CN')}
function storePending(order){pendingOrder.value=order;if(order)uni.setStorageSync(pendingKey,order);else uni.removeStorageSync(pendingKey)}
async function pollPurchase(order){
 for(let i=0;i<15;i++){if(i)await new Promise(resolve=>setTimeout(resolve,1500));const state=await pdfRequest('/purchase/'+order.orderId);if(state.paid || ['closed','refunded','failed'].includes(state.status)){storePending(null);return state}}
 return {paid:false,status:'pending'}
}
async function checkPending(){if(!pendingOrder.value||purchasing.value)return;purchasing.value=true
 try{const result=await pollPurchase(pendingOrder.value);await refresh();uni.showModal({title:result.paid?'购买成功':'支付结果',content:result.paid?'AI 额度已到账。':result.status==='pending'?'微信正在确认支付结果，请稍后再次查询。':'订单已关闭或退款，请核对微信账单。',showCancel:false})}catch(e){uni.showModal({title:'暂未确认支付',content:'网络暂不可用，请稍后查询，避免重复付款。',showCancel:false})}finally{purchasing.value=false}}
async function buy(packageId){if(purchasing.value||!billingEnabled.value||!paymentEnabled.value||aiStatus.value.testMode)return;if(pendingOrder.value){checkPending();return}purchasing.value=true
 try{
  if(paymentProvider.value==='virtual' && typeof wx.requestVirtualPayment!=='function')throw new Error('当前微信版本不支持虚拟支付，请更新微信后重试')
  const code=await login()
  if(paymentProvider.value==='wechat')await pdfRequest('/login','POST',{code})
  const order=await pdfRequest('/purchase','POST',{packageId,...(paymentProvider.value==='virtual'?{code}:{})})
  storePending({orderId:order.orderId,packageId})
  try{await requestPdfPayment(order,wx)}catch(e){
   if(String(e?.errMsg||'').toLowerCase().includes('cancel')){storePending(null);await refresh();return}
   // A cashier error or missing callback is not proof of non-payment. Query the saved order below.
  }
  const state=await pollPurchase(pendingOrder.value);await refresh()
  uni.showModal({title:state.paid?'购买成功':'正在确认支付',content:state.paid?(packageId==='ai-topup'?'200 万加油包额度已到账，可继续使用 AI。':'月卡会员已开通。'):['refunded','closed','failed'].includes(state.status)?'订单已关闭或退款，请核对微信账单。':'支付结果正在由微信确认，请稍后查询订单查看额度。',showCancel:false})
 }catch(e){uni.showModal({title:pendingOrder.value?'支付结果待确认':'无法完成购买',content:e.message||'请稍后重试',showCancel:false})}finally{purchasing.value=false}}
</script>
<style scoped>
.refresh-link{display:block;color:#246bfd;min-height:64rpx;line-height:64rpx}

.balance-card{margin-top:28rpx}.balance-heading{display:flex;align-items:center;justify-content:space-between;gap:12rpx;font-size:28rpx;font-weight:600}.member-tag{background:#edf4ff;color:#246bfd;border-radius:8rpx;padding:4rpx 12rpx;font-size:24rpx;font-weight:400}.balances{display:flex;gap:20rpx;margin:22rpx 0}.balances>view{flex:1;min-width:0}.balances>view>text{font-size:24rpx;color:#73839b}.balances>view>view{font-size:36rpx;line-height:1.6;font-weight:700;color:#246bfd}.balance-foot{font-size:24rpx;color:#73839b;border-top:1rpx solid #edf2f8;padding-top:16rpx;line-height:1.8}.package{padding:24rpx;border:2rpx solid #e5edf7;border-radius:20rpx;background:#fff;margin-bottom:16rpx}.package.selected{background:#edf4ff;border-color:#246bfd}.package-top{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.package-name{font-size:30rpx;font-weight:600}.package-name>text{display:block;font-size:24rpx;font-weight:400;color:#73839b;margin-top:4rpx}.package-price{font-size:40rpx;font-weight:700;color:#246bfd;white-space:nowrap}.package-price>text{font-size:24rpx;font-weight:400;margin:0 4rpx}.package-price>text:last-child{color:#73839b}.package-bottom{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-top:22rpx;padding-top:18rpx;border-top:1rpx solid #e1eaf7}.package-bottom>view:first-child{font-size:26rpx}.package-bottom text{display:block;font-size:24rpx;color:#73839b;margin-top:4rpx}.package-check{display:flex;align-items:center;justify-content:center;flex-shrink:0;width:40rpx;height:40rpx;border:2rpx solid #cad7e9;border-radius:50%;font-size:26rpx;color:transparent}.selected .package-check{background:#246bfd;border-color:#246bfd;color:#fff}.purchase-note{text-align:center;font-size:24rpx;color:#73839b;margin:16rpx 0}.limits{text-align:center;margin:18rpx 0 24rpx}
</style>
