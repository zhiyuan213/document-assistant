<template>
 <view class="pdf-screen ai-page" :style="headerPadding(24,true)">
  <PdfPageHeader title="读得更少，
懂得更多。" eyebrow="AI 文档工作台" subtitle="总结、问答、提取，读懂每一份文档。" badge="按用量扣额度 · 先估算再处理"/>
  <view class="ui-section-row"><text class="ui-section-title">AI 文档助手</text><text class="ui-link" @click="openCredits">我的额度 ›</text></view>
  <view v-if="status.testMode" class="ui-notice">测试模式 · 不扣用户额度。实际调用仍产生接口费用，每日上限仍有效。</view>
  <view v-if="!status.enabled" class="ui-notice">AI 服务暂未开放，请稍后再试。</view>
  <view v-else-if="!status.canUse" class="ui-notice">额度不足，开通月卡或购买加油包后即可使用。<text class="notice-link" @click="openCredits">查看方案 ›</text></view>
  <view v-else class="quota-card"><view><text>会员月额度</text><view>{{formatCredits(status.monthlyRemainingTokenCredits)}}</view></view><view><text>加油包额度</text><view>{{formatCredits(status.topupRemainingTokenCredits)}}</view></view><text class="quota-foot">今日还可处理 {{status.dailyRemaining}} 次{{status.testMode?' · 测试模式免费体验':''}}</text></view>
  <view class="ui-section-title">01 <text class="step-name">选择 AI 功能</text></view>
  <view class="ui-tool-grid"><PdfToolTile v-for="(item,index) in modes" :key="item.id" :label="modeLabels[index]" :color="modeColors[index]" :title="item.title" :caption="item.brief" :selected="mode===item.id" :disabled="busy" @click="selectMode(item.id)"/></view>
  <view class="mode-hint">{{current.hint}}</view>
  <view class="ui-section-title">02 <text class="step-name">添加 PDF 文件</text></view>
  <view class="picker" @click="chooseFiles"><view class="picker-icon">＋</view><view>{{selected.length?'继续添加 PDF':pickerText}}</view><text>{{mode==='compare'?'选择两个版本进行比较':'每次选择一个 PDF'}} · 单个不超过 20MB</text></view>
  <view v-for="(file,index) in selected" :key="file.path" class="file"><view class="file-label">PDF</view><text>{{file.name}}</text><button class="file-remove" :disabled="busy" aria-label="移除文件" @click="selected.splice(index,1)">×</button></view>
  <!-- #ifdef MP-WEIXIN -->
  <view class="ui-caption file-help">手机文件可先发到文件传输助手，再从微信聊天中选择。</view>
  <!-- #endif -->
  <view v-if="['chat','explain'].includes(mode)" class="ui-section-title">你的问题</view>
  <textarea v-if="['chat','explain'].includes(mode)" v-model="prompt" :disabled="busy" class="ui-input prompt" maxlength="1500" :placeholder="mode==='chat'?'例如：这份报告有哪些主要结论？':'描述你的问题，或粘贴需要解释的段落'" />
  <view v-if="['translate','extract'].includes(mode)" class="ui-section-title">补充要求<text class="optional">选填</text></view>
  <textarea v-if="['translate','extract'].includes(mode)" v-model="prompt" :disabled="busy" class="ui-input prompt" maxlength="1000" :placeholder="mode==='translate'?'例如：翻译成英文，保留专业术语':'例如：重点提取日期、金额与付款方式'" />
  <view v-if="['vision','watermark'].includes(mode)" class="ui-section-title">要分析的页码</view>
  <input v-if="['vision','watermark'].includes(mode)" v-model="page" :disabled="busy" class="ui-input" type="number" placeholder="输入页码，例如 1" />
  <view v-if="estimate" class="estimate"><view class="estimate-title">用量预估</view><view>预计输入 {{formatCredits(estimate.estimatedInputTokens)}} Token</view><view v-if="estimate.testMode">测试模式：预占和实际扣用户额度均为 0。</view><view v-else>预计预占 {{formatCredits(estimate.reservedTokenCredits)}} 加权额度；完成后按实际用量结算，多余预占退回，实际用量可能超过预估。</view><view v-if="!estimate.canRun" class="error-inline">{{estimate.testMode?'今日次数已用完，请明日再试。':'额度或今日次数不足，请查看我的额度。'}}</view><text v-if="!estimate.canRun && !estimate.testMode" class="notice-link" @click="openCredits">查看我的额度 ›</text></view>
  <PdfAiBilling v-if="!status.testMode" />
  <button class="ui-button" :disabled="busy || !status.enabled || !status.canUse || status.dailyRemaining<1 || !selected.length || (estimate && !estimate.canRun)" :loading="busy" @click="run">{{busy?busyLabel:!status.enabled?'AI 服务暂未开放':!status.canUse?'请先获取 AI 额度':!status.dailyRemaining?'今日次数已用完':estimate?(estimate.testMode?'开始测试 · 不扣额度':'确认并'+current.action):'免费估算用量'}}</button>
  <view class="privacy">估算仅在本服务提取文字；确认分析后，文档内容或页面图像才会发送至 DeepSeek。AI 结果请核对原文，原 PDF 不会被修改。</view>
  <view v-if="error" class="error" role="alert">{{error}}</view>
  <view v-if="answer" class="answer ui-card"><view class="ui-section-title">分析结果</view><view v-if="chargedCredits!==null" class="charged">{{usageDetails?.testMode?'测试模式 · 本次不扣用户额度':'本次扣除 '+formatCredits(chargedCredits)+' 加权额度'}}</view><view v-if="usageDetails" class="usage-details">输入 {{formatCredits(usageDetails.inputTokens)}} Token · 输出 {{formatCredits(usageDetails.outputTokens)}} Token<view v-if="!usageDetails.testMode">月额度扣 {{formatCredits(usageDetails.monthlyChargedCredits)}} · 加油包扣 {{formatCredits(usageDetails.topupChargedCredits)}}</view></view><text class="answer-text" selectable>{{answer}}</text><button class="ui-button ui-button-secondary" @click="copy">复制结果</button></view>
 </view>
</template>
<script setup>
import {ref,computed,watch} from 'vue'
import PdfAiBilling from '../../components/pdf-ai-billing.vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import PdfToolTile from '../../components/PdfToolTile.vue'
import {headerPadding} from '../../lib/layout'
import {onLoad,onShow} from '@dcloudio/uni-app'
import {pdfRequest,estimatePdfAi,submitPdfAi} from '../../lib/pdf-api'

const modeColors=['#5875df','#26a69a','#e5a133','#9270d6','#388bcb','#dc7c63','#5c9b61','#4f9fae','#a87ab0']
const modeLabels=['总','问','取','比','译','解','学','图','印']
const modes=[
 {id:'summary',title:'AI 总结',brief:'结论与待办',action:'生成文档总结',hint:'按章节梳理重点、结论、关键数据和待办，并尽量标注页码。'},
 {id:'chat',title:'问 PDF',brief:'带页码出处',action:'提问文档',hint:'根据文档回答问题，并给出相关页码和原文线索。'},
 {id:'extract',title:'信息提取',brief:'整理成清单',action:'提取关键信息',hint:'提取日期、金额、人物、机构、结论和待办事项。'},
 {id:'compare',title:'双文档对比',brief:'找出版本变化',action:'比较两个 PDF',hint:'选择两个版本，列出新增、删除和修改内容，并标注文档页码。'},
 {id:'translate',title:'AI 翻译',brief:'保留页码结构',action:'翻译 PDF 内容',hint:'可指定目标语言；较长文件建议先拆分或按章节处理。'},
 {id:'explain',title:'通俗解释',brief:'解释专业内容',action:'解释文档内容',hint:'针对一个问题或段落解释术语和逻辑，结论附页码。'},
 {id:'study',title:'学习助手',brief:'提纲与测验',action:'生成学习资料',hint:'生成章节提纲、概念闪卡和自测题，答案依据 PDF 内容。'},
 {id:'vision',title:'页面识别',brief:'分析指定页',action:'识别页面内容',hint:'对指定页的扫描文字、表格或图表做视觉理解。每次分析一页。'},
 {id:'watermark',title:'水印识别',brief:'定位与评估',action:'分析水印页面',hint:'识别指定页水印位置和遮挡情况。当前只能分析，不能恢复被遮挡内容或生成去水印文件。'},
]
const mode=ref('summary'),selected=ref([]),prompt=ref(''),page=ref('1'),answer=ref(''),error=ref(''),busy=ref(false),busyLabel=ref('正在处理…'),estimate=ref(null),chargedCredits=ref(null),usageDetails=ref(null)
const status=ref({enabled:false,vip:false,testMode:false,canUse:false,monthlyRemainingTokenCredits:0,topupRemainingTokenCredits:0,dailyLimit:50,dailyRemaining:0,monthlyTokenCredits:2000000,usedTokenCredits:0,remainingTokenCredits:0})
const current=computed(()=>modes.find(x=>x.id===mode.value)||modes[0])
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
const pickerText=computed(()=>miniProgram?'从微信聊天选择 PDF':'点击选择 PDF 文件')
watch([mode,prompt,page,()=>selected.value.map(f=>`${f.path}:${f.size}`).join('|')],()=>{estimate.value=null})
onLoad(options=>{if(modes.some(item=>item.id===options?.mode))mode.value=options.mode})
onShow(refreshStatus)
async function refreshStatus(){try{status.value=await pdfRequest('/ai/status');estimate.value=null}catch(e){error.value=e.message}}
function selectMode(id){if(busy.value)return;mode.value=id;answer.value='';error.value='';estimate.value=null;if(id!=='compare'&&selected.value.length>1)selected.value=selected.value.slice(0,1)}
function chooseFiles(){
 if(busy.value)return
 const count=mode.value==='compare'?2:1
 if(selected.value.length>=count){uni.showToast({title:mode.value==='compare'?'最多选择两个 PDF':'请先移除当前文件',icon:'none'});return}
 const success=r=>{const files=(r.tempFiles||[]).map(f=>({path:f.path||f.tempFilePath,name:f.name||'document.pdf',size:f.size||0}));if(files.some(f=>f.size>20*1024*1024)){error.value='单个 PDF 不能超过20MB';return}selected.value=[...selected.value,...files].slice(0,count);error.value='';estimate.value=null}
 // #ifdef H5
 uni.chooseFile({count:count-selected.value.length,type:'file',extension:['.pdf'],success,fail:()=>{}})
 // #endif
 // #ifdef MP-WEIXIN
 wx.chooseMessageFile({count:count-selected.value.length,type:'file',extension:['pdf'],success,fail:r=>{if(!String(r.errMsg||'').includes('cancel'))error.value='选择 PDF 失败，请重试'}})
 // #endif
}
async function run(){
 if(busy.value)return
 error.value='';answer.value='';chargedCredits.value=null;usageDetails.value=null
 const expected=mode.value==='compare'?2:1
 if(selected.value.length!==expected){error.value=mode.value==='compare'?'请选择两个 PDF':'请选择一个 PDF';return}
 if(['chat','explain'].includes(mode.value)&&!prompt.value.trim()){error.value='请先填写问题';return}
 busy.value=true
 try{
  if(!estimate.value){busyLabel.value='正在估算用量…';estimate.value=await estimatePdfAi(mode.value,selected.value,prompt.value,Math.max(1,Number(page.value)||1));status.value={...status.value,remainingTokenCredits:estimate.value.remainingTokenCredits};return}
  busyLabel.value='正在分析…';const result=await submitPdfAi(mode.value,selected.value,prompt.value,Math.max(1,Number(page.value)||1));answer.value=result.answer;chargedCredits.value=result.usedTokenCredits;usageDetails.value=result;estimate.value=null;await refreshStatus()
 }
 catch(e){error.value=e.message||'AI 分析失败，请重试';if(e.status===402)uni.navigateTo({url:'/pages/pdf/credits'})}
 finally{busy.value=false}
}
function formatCredits(value){return Number(value||0).toLocaleString('zh-CN')}
function copy(){uni.setClipboardData({data:answer.value,success:()=>uni.showToast({title:'已复制',icon:'none'})})}
function openCredits(){uni.navigateTo({url:'/pages/pdf/credits'})}
function back(){uni.switchTab({url:'/pages/tools/index'})}
</script>
<style scoped>
.step-name{margin-left:12rpx}.quota-card{box-shadow:0 5rpx 18rpx #172b4d04}

.quota-card{display:flex;flex-wrap:wrap;gap:12rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;padding:24rpx}.quota-card>view{flex:1;min-width:0}.quota-card>view>text{font-size:24rpx;color:#73839b}.quota-card>view>view{font-size:34rpx;font-weight:700;color:#246bfd;margin-top:4rpx}.quota-foot{display:block;width:100%;border-top:1rpx solid #edf2f8;padding-top:14rpx;margin-top:6rpx;font-size:24rpx;color:#73839b}.mode-hint{color:#73839b;font-size:24rpx;line-height:1.7;margin:16rpx 0}.picker{background:#fff;border:2rpx dashed #c5d8f3;border-radius:20rpx;padding:30rpx 16rpx;text-align:center;color:#246bfd}.picker-icon{font-size:44rpx;line-height:1.2;margin-bottom:10rpx}.picker>view:not(.picker-icon){font-size:28rpx;font-weight:600}.picker>text{display:block;font-size:24rpx;color:#73839b;margin-top:8rpx}.file{display:flex;align-items:center;gap:16rpx;padding:14rpx 16rpx;margin-top:14rpx;border:1rpx solid #e5edf7;border-radius:16rpx;background:#fff}.file-label{font-size:20rpx;background:#edf4ff;color:#246bfd;padding:8rpx;border-radius:8rpx}.file>text{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:26rpx}.file-remove{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;background:transparent;color:#73839b;font-size:34rpx;padding:0;margin:0}.file-help{margin-top:14rpx}.prompt{min-height:180rpx}.optional{font-size:24rpx;color:#8b99ae;font-weight:400;margin-left:12rpx}.estimate{padding:24rpx;border:1rpx solid #d5e5fc;border-radius:20rpx;background:#edf4ff;color:#47668f;font-size:24rpx;line-height:1.8;margin:24rpx 0}.estimate-title{font-size:28rpx;font-weight:600;color:#246bfd;margin-bottom:8rpx}.notice-link{color:#246bfd;display:inline-block;min-height:48rpx;padding-top:8rpx}.privacy{font-size:24rpx;line-height:1.8;color:#8b99ae;text-align:center;margin:18rpx 6rpx}.error{padding:20rpx 24rpx;background:#fff0f0;color:#b54747;border-radius:16rpx;margin-top:20rpx;font-size:26rpx}.error-inline{color:#b54747}.answer{margin-top:32rpx}.answer>.ui-section-title{margin:0 0 12rpx}.charged{color:#246bfd;font-size:26rpx;font-weight:600}.usage-details{font-size:24rpx;color:#73839b;margin:8rpx 0 24rpx;line-height:1.8}.answer-text{display:block;white-space:pre-wrap;font-size:28rpx;line-height:1.9;word-break:break-word}
</style>
