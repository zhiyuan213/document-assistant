<template>
 <view class="pdf-screen orders">
  <view class="ui-heading">购买记录</view>
  <view class="ui-subtitle">月卡与加油包订单，最新购买显示在前。</view>
  <view v-if="error" class="ui-notice order-error">{{error}}</view>
  <button class="refresh" :disabled="loading || checking" @click="load(true)">{{loading?'正在加载…':'刷新订单'}}</button>
  <view v-if="loaded && !orders.length && !error" class="ui-card empty">暂无购买记录</view>
  <view v-for="order in orders" :key="order.id" class="ui-card order">
   <view class="order-heading"><text>{{order.packageId==='monthly'?'AI 月卡':order.packageId==='ai-topup'?'AI 加油包':'购买套餐'}}</text><text class="status" :class="{paid:order.status==='paid'}">{{statusLabel(order)}}</text></view>
   <view class="amount">¥{{(Number(order.amountFen)/100).toFixed(2)}}</view>
   <view class="detail">购买时间：{{time(order.createdAt)}}</view>
   <view v-if="order.paidAt" class="detail">支付时间：{{time(order.paidAt)}}</view>
   <view v-if="order.refundedFen>0" class="detail">已退款：¥{{(order.refundedFen/100).toFixed(2)}}</view>
   <view class="detail">订单编号：<text selectable>{{order.orderNo || order.id}}</text></view>
   <button v-if="order.status==='pending'" class="ui-button ui-button-secondary check-order" :disabled="loading || !!checking" @click="check(order)">{{checking===order.id?'正在确认…':'查询支付结果'}}</button>
  </view>
  <button v-if="hasMore" class="ui-button ui-button-secondary" :disabled="loading || !!checking" @click="load(false)">{{loading?'正在加载…':'加载更多'}}</button>
 </view>
</template>
<script setup>
import {ref} from 'vue'
import {onShow,onPullDownRefresh} from '@dcloudio/uni-app'
import {pdfRequest} from '../../lib/pdf-api'
const orders=ref([]),error=ref(''),loading=ref(false),loaded=ref(false),hasMore=ref(false),checking=ref('')
let page=0,identityReady=false
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
function time(value){return String(value||'').replace('T',' ').slice(0,19)}
function statusLabel(order){if(order.status==='paid'&&order.refundedFen>0)return '部分退款';return {paid:'支付成功',pending:'待确认',refunded:'已退款',closed:'已关闭',failed:'支付失败'}[order.status]||'处理中'}
async function ensureIdentity(){
 if(identityReady)return
 const config=await pdfRequest('/quota')
 if(miniProgram&&config.wechatLoginEnabled){
  const code=await new Promise((resolve,reject)=>wx.login({success:r=>r.code?resolve(r.code):reject(new Error('微信身份校验失败，请重试')),fail:()=>reject(new Error('微信登录失败，请重试'))}))
  await pdfRequest('/login','POST',{code})
 }
 identityReady=true
}
async function load(reset=true){
 if(loading.value || checking.value)return
 loading.value=true;error.value=''
 try{
  await ensureIdentity()
  const next=reset?1:page+1,result=await pdfRequest('/purchases?page='+next)
  const rows=reset?result.items:[...orders.value,...result.items]
  orders.value=Array.from(new Map(rows.map(order=>[order.id,order])).values());page=next;hasMore.value=result.hasMore;loaded.value=true
 }catch(e){error.value=e.message||'订单加载失败，请重试';identityReady=false}finally{loading.value=false}
}
async function check(order){
 if(checking.value || loading.value)return
 checking.value=order.id;error.value=''
 try{
  const result=await pdfRequest('/purchase/'+encodeURIComponent(order.id))
  if(result.paid){if(uni.getStorageSync('pdf-pending-purchase-v1')?.orderId===order.id)uni.removeStorageSync('pdf-pending-purchase-v1');uni.switchTab({url:'/pages/pdf/ai',success:()=>uni.showToast({title:'充值成功，额度已到账',icon:'none'})})}
  else uni.showToast({title:result.status==='pending'?'支付结果仍在确认，请稍后刷新':statusLabel(result),icon:'none'})
 }catch(e){error.value=e.message||'支付结果查询失败，请稍后重试'}finally{checking.value=''}
 await load(true)
}
onShow(()=>load(true))
onPullDownRefresh(async()=>{try{await load(true)}finally{uni.stopPullDownRefresh()}})
</script>
<style scoped>
.refresh{margin:20rpx 0;padding:10rpx 0;color:#246bfd;font-size:26rpx;background:transparent;text-align:left}.order{margin-bottom:20rpx}.order-heading{display:flex;align-items:center;justify-content:space-between;gap:16rpx;font-size:30rpx;font-weight:600}.status{font-size:24rpx;color:#73839b;font-weight:400}.status.paid{color:#16845c}.amount{font-size:40rpx;font-weight:700;color:#253858;margin:18rpx 0}.detail{font-size:24rpx;color:#73839b;line-height:1.9;word-break:break-all}.empty{text-align:center;color:#73839b;margin-top:24rpx}.order-error{color:#c64b4b}.check-order{margin-top:20rpx}
</style>
