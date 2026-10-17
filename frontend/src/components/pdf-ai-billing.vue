<template>
 <view class="billing-rules">
  <button class="billing-toggle" :aria-expanded="expanded" @click="expanded=!expanded"><text class="billing-title">扣费说明</text><text class="billing-toggle-label">{{expanded?'收起 −':'展开查看 ＋'}}</text></button>
  <view v-if="expanded" class="billing-details">
  <view>按实际 Token 用量扣额度，不按文件数或处理次数收费。Token 是 AI 处理文字和图片的计量单位，不等于字数；输入包含文档内容、问题和处理指令，输出是 AI 生成的回答。</view>
  <view class="billing-formula">扣除额度 = 输入 Token × 1 + 输出 Token × 4</view>
  <view>例如：输入 10,000 Token、输出 1,000 Token，共扣 14,000 额度。页面标注的额度指按此公式折算后的额度。</view>
  <view>每天赠送 5,000 免费额度，北京时间零点重置，未用完不累积。先扣免费额度，再扣当前会员周期的月额度，最后扣加油包。月额度在周期结束后失效，续费后进入下一周期时刷新；加油包长期有效，会员到期后仍可使用。</view>
  <view>可用额度大于 0 即可开始处理。处理时预占额度，完成后按实际用量结算，多余部分退回原额度来源；已过期的免费额度和月额度不恢复。实际用量超过可用额度时最多扣到 0，不产生欠额，也不抵扣后续充值。</view>
  <view>发送到 AI 前失败，退回全部预占额度；已发送的请求有实际用量记录时按实际结算，即使结果异常也可能扣额度。超时等原因导致未收到用量记录时，保留预占额度，请先查看余额再重试。</view>
  <view>每个账号每日最多请求 100 次，购买加油包不增加次数上限。购买月卡和加油包均需主动支付，不自动续费、不自动扣款。基础 PDF 工具永久免费。</view>
  <view>整份文档翻译会分段调用AI，额度按各段实际输入及输出用量累计；一份任务计一次每日请求。查询进度和导出已有结果不重复扣费。</view>
  </view>
 </view>
</template>
<script setup>
import {ref} from 'vue'
const expanded=ref(false)
</script>
<style scoped>
.billing-rules{margin:24rpx 0;padding:22rpx 24rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;color:#73839b;font-size:24rpx;line-height:1.8}.billing-title{font-size:28rpx;font-weight:600;color:#172b4d;margin:0}.billing-formula{color:#246bfd;font-size:24rpx;font-weight:600;margin-bottom:10rpx}.billing-caution{margin-top:10rpx;color:#8d7449}.billing-toggle{display:flex;align-items:center;justify-content:space-between;width:100%;min-height:64rpx;font-size:24rpx;line-height:1.6;color:#246bfd;background:transparent;padding:0;margin:0}.billing-toggle-label{font-size:24rpx;font-weight:400;color:#246bfd}.billing-toggle::after{border:0}.billing-details{border-top:1rpx solid #e5edf7;margin-top:14rpx;padding-top:6rpx}.billing-details>view{margin-top:14rpx}
</style>
