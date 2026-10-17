<template>
 <view class="pdf-screen ai-page" :style="headerPadding(24,true)">
  <PdfPageHeader title="读得更少，
懂得更多。" eyebrow="AI 文档工作台" subtitle="整理重点报告，翻译常用语言。" badge="按用量扣额度 · 先估算再处理"/>
  <view class="ui-section-row"><text class="ui-section-title">AI 文档助手</text><text class="ui-link" @click="openCredits">我的额度 ›</text></view>
  <view v-if="status.testMode" class="ui-notice">测试模式 · 不扣用户额度。实际调用仍产生接口费用，每日上限仍有效。</view>
  <view v-if="statusError" class="ui-notice">{{statusError}}<text class="notice-link" @click="refreshStatus">重新检查 ›</text></view>
  <view v-else-if="!statusLoaded" class="ui-notice">正在检查 AI 服务…</view>
  <view v-else-if="!status.enabled" class="ui-notice">AI 服务配置尚未生效，请联系管理员检查后端配置。</view>
  <view v-else-if="!status.canUse" class="ui-notice">额度不足，开通月卡或购买加油包后即可使用。<text class="notice-link" @click="openCredits">查看方案 ›</text></view>
  <view v-else class="quota-card"><view><text>会员月额度</text><view>{{formatCredits(status.monthlyRemainingTokenCredits)}}</view></view><view><text>加油包额度</text><view>{{formatCredits(status.topupRemainingTokenCredits)}}</view></view><text class="quota-foot">今日还可处理 {{status.dailyRemaining}} 次{{status.testMode?' · 测试模式免费体验':''}}</text></view>
  <view class="ui-section-title">01 <text class="step-name">选择 AI 功能</text></view>
  <view class="ui-tool-grid"><PdfToolTile v-for="(item,index) in modes" :key="item.id" :label="modeLabels[index]" :color="modeColors[index]" :title="item.title" :caption="item.brief" :selected="mode===item.id" :disabled="busy || translationPending" @click="selectMode(item.id)"/></view>
  <view class="ui-section-title">02 <text class="step-name">添加 PDF 文件</text></view>
  <view class="picker" @click="chooseFiles"><view class="picker-icon">＋</view><view>{{selected.length?'更换 PDF':pickerText}}</view><text>每次选择一个 PDF · 单个不超过 20MB</text></view>
  <view v-for="(file,index) in selected" :key="file.path" class="file"><view class="file-label">PDF</view><text>{{file.name}}</text><button class="file-remove" :disabled="busy || translationPending" aria-label="移除文件" @click="removeFile(index)">×</button></view>
  <view v-if="mode==='translate'" class="ui-section-title">03 选择目标语言</view>
  <picker v-if="mode==='translate'" :disabled="busy || translationPending" :range="languages" range-key="title" :value="languageIndex" @change="languageIndex=Number($event.detail.value)"><view class="ui-input language-picker">{{languages[languageIndex].title}} <text>点击选择 ›</text></view></picker>
  <view v-if="mode==='translate'" class="ui-caption">自动识别原文语言。提供文本、Word及保留版式的PDF；扫描页需OCR重建，复杂底纹和表格请核对。</view>
  <textarea v-if="mode==='translate'" v-model="prompt" :disabled="busy || translationPending" class="ui-input prompt" maxlength="1000" placeholder="补充要求（选填），例如：保留产品名称、使用正式语气" />
  <view v-if="estimate" class="estimate"><view class="estimate-title">用量预估</view><view v-if="!estimate.documentTranslation">预计输入 {{formatCredits(estimate.estimatedInputTokens)}} Token</view><view v-else>整份文档共 {{estimate.pages}} 页，按分段翻译的累计用量估算。</view><view v-for="warning in estimate.warnings||[]" :key="warning">{{warning}}</view><view v-if="estimate.testMode">测试模式：预占和实际扣用户额度均为 0。</view><view v-else>预计预占 {{formatCredits(estimate.reservedTokenCredits)}} 加权额度；完成后按实际用量结算，多余预占退回，实际用量可能超过预估。</view><view v-if="!estimate.canRun" class="error-inline">{{estimate.testMode?'今日次数已用完，请明日再试。':'额度或今日次数不足，请查看我的额度。'}}</view><text v-if="!estimate.canRun && !estimate.testMode" class="notice-link" @click="openCredits">查看我的额度 ›</text></view>
  <PdfAiBilling v-if="!status.testMode" />
  <button class="ui-button" :disabled="busy || exporting || !statusLoaded || !status.enabled || (status.canUse && (status.dailyRemaining<1 || !selected.length || (estimate && !estimate.canRun)))" :loading="busy" @click="primaryAction">{{busy?busyLabel:statusError?'AI 服务连接异常':!statusLoaded?'正在检查 AI 服务':!status.enabled?'AI 服务配置未生效':!status.canUse?'充值 AI 额度':!status.dailyRemaining?'今日次数已用完':estimate?(estimate.testMode?'开始测试 · 不扣额度':'确认并'+current.action):'免费估算用量'}}</button>
  <view class="privacy">估算仅在本服务提取文字；确认分析后，文档文字才会发送至 DeepSeek。AI 结果请核对原文，原 PDF 不会被修改。</view>
  <view v-if="error" class="error" role="alert">{{error}}</view>
  <view v-if="answer" class="answer ui-card"><view class="ui-section-title">{{mode==='summary'?'总结报告':'翻译结果'}}</view><view v-if="chargedCredits!==null" class="charged">{{usageDetails?.testMode?'测试模式 · 本次不扣用户额度':'本次扣除 '+formatCredits(chargedCredits)+' 加权额度'}}</view><view v-if="usageDetails" class="usage-details">输入 {{formatCredits(usageDetails.inputTokens)}} Token · 输出 {{formatCredits(usageDetails.outputTokens)}} Token<view v-if="!usageDetails.testMode">月额度扣 {{formatCredits(usageDetails.monthlyChargedCredits)}} · 加油包扣 {{formatCredits(usageDetails.topupChargedCredits)}}</view></view><view class="export-label">文件格式</view><view class="export-actions"><button class="ui-button ui-button-secondary" :class="{'format-selected':exportFormat==='docx'}" :disabled="exporting" @click="chooseExportFormat('docx')">Word {{exportFormat==='docx'?'✓':''}}</button><button class="ui-button ui-button-secondary" :class="{'format-selected':exportFormat==='pdf'}" :disabled="exporting || pdfUnavailable" @click="chooseExportFormat('pdf')">PDF {{exportFormat==='pdf'?'✓':''}}</button></view>
   <view v-if="pdfUnavailable" class="ui-notice">译文超出原文字区域或字体无法还原，本次未生成保留版式的 PDF，可选择 Word。</view>
   <view class="export-label">导出方式</view><view class="export-actions"><button class="ui-button" :disabled="exporting" @click="deliverReport('share')">{{exporting?'正在准备文件…':'转发给微信好友'}}</button><button class="ui-button ui-button-secondary" :disabled="exporting" @click="deliverReport('download')">下载到本机</button></view>
   <view class="ui-caption">导出使用本次结果，不重复扣 AI 额度。结果保留 30 分钟。</view><view v-for="warning in translationWarnings" :key="warning" class="ui-notice">{{warning}}</view></view>
 </view>
</template>
<script setup>
import {ref,computed,watch} from 'vue'
import PdfAiBilling from '../../components/pdf-ai-billing.vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import PdfToolTile from '../../components/PdfToolTile.vue'
import {headerPadding} from '../../lib/layout'
import {onLoad,onShow} from '@dcloudio/uni-app'
import {pdfRequest,estimatePdfAi,submitPdfAi,exportAiReport,shareSavedFile,saveFileToPhone} from '../../lib/pdf-api'

const modeColors=['#5875df','#388bcb'],modeLabels=['总','译']
const modes=[
 {id:'summary',title:'AI 总结',brief:'重点 · 结论 · 待办',action:'生成总结报告'},
 {id:'translate',title:'AI 翻译',brief:'常用语言互译',action:'翻译文档'}
]
const languages=[{id:'zh-CN',title:'简体中文'},{id:'zh-TW',title:'繁体中文'},{id:'en',title:'英语'},{id:'ja',title:'日语'},{id:'ko',title:'韩语'},{id:'fr',title:'法语'},{id:'de',title:'德语'},{id:'es',title:'西班牙语'},{id:'pt',title:'葡萄牙语'},{id:'ru',title:'俄语'},{id:'ar',title:'阿拉伯语'},{id:'it',title:'意大利语'}]
const languageIndex=ref(0),exporting=ref(false),exportFile=ref(null)
const translationJob=ref(''),translationWarnings=ref([]),translationPending=ref(false)
const translationKey='pdf-translation-job-v1',translationRequestKey='pdf-translation-request-v1'
const mode=ref('summary'),selected=ref([]),prompt=ref(''),page=ref('1'),answer=ref(''),error=ref(''),busy=ref(false),busyLabel=ref('正在处理…'),estimate=ref(null),chargedCredits=ref(null),usageDetails=ref(null)
const exportFormat=ref('docx')
const pdfUnavailable=computed(()=>mode.value==='translate' && usageDetails.value?.pdfAvailable===false)
watch(answer,()=>{exportFile.value=null;exportFormat.value='docx';if(answer.value&&miniProgram)exportReport('docx')})
const statusLoaded=ref(false),statusError=ref('')
const status=ref({enabled:false,vip:false,testMode:false,canUse:false,monthlyRemainingTokenCredits:0,topupRemainingTokenCredits:0,dailyLimit:50,dailyRemaining:0,monthlyTokenCredits:2000000,usedTokenCredits:0,remainingTokenCredits:0})
const current=computed(()=>modes.find(x=>x.id===mode.value)||modes[0])
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
const pickerText=computed(()=>miniProgram?'从微信聊天选择 PDF':'点击选择 PDF 文件')
watch([mode,prompt,page,languageIndex,()=>selected.value.map(f=>`${f.path}:${f.size}`).join('|')],()=>{estimate.value=null})
watch([mode,prompt,languageIndex],()=>{if(!busy.value&&!translationPending.value){answer.value='';translationJob.value='';translationWarnings.value=[];exportFile.value=null;uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey)}})
onLoad(options=>{if(modes.some(item=>item.id===options?.mode))mode.value=options.mode})
onShow(()=>{refreshStatus();if(!busy.value&&!answer.value&&(uni.getStorageSync(translationKey)||uni.getStorageSync(translationRequestKey)))resumeTranslation()})
async function refreshStatus(){statusError.value='';statusLoaded.value=false;try{status.value=await pdfRequest('/ai/status');statusLoaded.value=true;estimate.value=null}catch(e){status.value={...status.value,enabled:false};statusError.value=e.status===404?'当前服务器尚未提供 AI 接口，请部署包含 AI 功能的后端版本。':'无法获取 AI 服务状态，请稍后重试。'}}
function selectMode(id){if(busy.value||exporting.value||translationPending.value)return;exportFile.value=null;mode.value=id;answer.value='';error.value='';estimate.value=null;translationJob.value='';translationWarnings.value=[];uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey);if(id!=='compare'&&selected.value.length>1)selected.value=selected.value.slice(0,1)}
function chooseFiles(){
 if(busy.value||exporting.value||translationPending.value)return
 const success=r=>{const files=(r.tempFiles||[]).map(f=>({path:f.path||f.tempFilePath,name:f.name||'document.pdf',size:f.size||0}));if(files.some(f=>f.size>20*1024*1024)){error.value='单个 PDF 不能超过20MB';return}if(!files.length)return;selected.value=files.slice(0,1);error.value='';estimate.value=null;answer.value='';exportFile.value=null;chargedCredits.value=null;usageDetails.value=null;translationJob.value='';translationWarnings.value=[];uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey)}
 // #ifdef H5
 uni.chooseFile({count:1,type:'file',extension:['.pdf'],success,fail:()=>{}})
 // #endif
 // #ifdef MP-WEIXIN
 wx.chooseMessageFile({count:1,type:'file',extension:['pdf'],success,fail:r=>{if(!String(r.errMsg||'').includes('cancel'))error.value='选择 PDF 失败，请重试'}})
 // #endif
}
function removeFile(index){if(busy.value||exporting.value||translationPending.value)return;selected.value.splice(index,1);answer.value='';translationJob.value='';translationWarnings.value=[];exportFile.value=null;estimate.value=null;uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey)}
function primaryAction(){if(translationPending.value){resumeTranslation();return}if(!status.value.canUse){openCredits();return}run()}
async function run(){
 if(busy.value)return
 if(translationPending.value){resumeTranslation();return}
 error.value='';answer.value='';exportFile.value=null;chargedCredits.value=null;usageDetails.value=null
 const expected=mode.value==='compare'?2:1
 if(selected.value.length!==expected){error.value=mode.value==='compare'?'请选择两个 PDF':'请选择一个 PDF';return}
 if(['chat','explain'].includes(mode.value)&&!prompt.value.trim()){error.value='请先填写问题';return}
 busy.value=true
 try{
  if(!estimate.value){busyLabel.value='正在估算用量…';estimate.value=await estimatePdfAi(mode.value,selected.value,prompt.value,1,languages[languageIndex.value].id);status.value={...status.value,remainingTokenCredits:estimate.value.remainingTokenCredits};return}
  let requestId=''
  if(mode.value==='translate'){requestId=uni.getStorageSync(translationRequestKey)||Date.now()+'-'+Math.random().toString(36).slice(2);uni.setStorageSync(translationRequestKey,requestId);translationPending.value=true}
  busyLabel.value='正在分析…';let result=await submitPdfAi(mode.value,selected.value,prompt.value,1,languages[languageIndex.value].id,requestId)
  if(result.translationJobId){translationJob.value=result.translationJobId;translationPending.value=true;uni.setStorageSync(translationKey,result.translationJobId);result=await waitTranslation(result.translationJobId)}
  applyResult(result);estimate.value=null;await refreshStatus()
 }
 catch(e){error.value=e.message||'AI 分析失败，请重试';if(e.status===402)uni.navigateTo({url:'/pages/pdf/credits'})}
 finally{busy.value=false}
}
function applyResult(result){if(result.targetLanguage){const index=languages.findIndex(l=>l.id===result.targetLanguage);if(index>=0)languageIndex.value=index}answer.value=result.answer;chargedCredits.value=result.usedTokenCredits;usageDetails.value=result;translationWarnings.value=result.warnings||[];translationPending.value=false}
async function waitTranslation(id){
 for(let i=0;i<180;i++){
  if(i)await new Promise(resolve=>setTimeout(resolve,5000))
  const state=await pdfRequest('/ai/translations/'+encodeURIComponent(id))
  busyLabel.value='正在翻译并排版 '+Number(state.progress||0)+'%…'
  if(state.status==='completed')return state
  if(state.status==='failed'){translationPending.value=false;uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey);throw new Error(state.message||'翻译未完成，请重试')}
 }
 throw new Error('任务仍在后台处理中，重新进入本页会自动恢复进度，请勿重复提交。')
}
async function resumeTranslation(){if(busy.value)return;let id=translationJob.value||uni.getStorageSync(translationKey);const requestId=uni.getStorageSync(translationRequestKey);if(!id&&!requestId)return;busy.value=true;mode.value='translate';translationPending.value=true;error.value=''
 try{if(!id){const state=await pdfRequest('/ai/translations/by-request/'+encodeURIComponent(requestId));id=state.translationJobId;uni.setStorageSync(translationKey,id)}translationJob.value=id;applyResult(await waitTranslation(id));await refreshStatus()}catch(e){error.value=e.message;if(e.status===404){translationPending.value=false;translationJob.value='';uni.removeStorageSync(translationKey);if(e.message==='请求路径不存在'){error.value='服务器尚未提供翻译任务查询接口，请部署最新后端版本后重试。'}else if(id){uni.removeStorageSync(translationRequestKey)}}}finally{busy.value=false}}
async function exportReport(format){if(exporting.value||!answer.value)return;exporting.value=true;error.value='';try{exportFile.value=await exportAiReport(answer.value,format,mode.value==='translate'?translationJob.value:'')||null;uni.showToast({title:'导出完成',icon:'none'})}catch(e){error.value=e.message}finally{exporting.value=false}}
function chooseExportFormat(format){if(exporting.value || (format==='pdf'&&pdfUnavailable.value))return;exportFormat.value=format;exportFile.value=null;if(miniProgram)exportReport(format)}
function deliverReport(action){
 if(exporting.value||!answer.value)return
 if(!exportFile.value || exportFile.value.extension!==exportFormat.value){
  exportReport(exportFormat.value).then(()=>{if(exportFile.value)uni.showToast({title:'文件已准备好，请再次点击'+(action==='share'?'转发':'下载'),icon:'none'})});return
 }
 const operation=action==='share'?shareSavedFile(exportFile.value):saveFileToPhone(exportFile.value)
 operation?.catch(e=>{if(!e.cancelled)error.value=e.message})
}
function formatCredits(value){return Number(value||0).toLocaleString('zh-CN')}
function openCredits(){uni.navigateTo({url:'/pages/pdf/credits'})}
function back(){uni.switchTab({url:'/pages/tools/index'})}
</script>
<style scoped>
.export-label{font-size:26rpx;font-weight:600;color:#253858;margin:24rpx 0 12rpx}.format-selected{background:#edf4ff!important;border-color:#246bfd!important;color:#246bfd!important}
.language-picker{display:flex;justify-content:space-between}.language-picker text{color:#246bfd;font-size:24rpx}.export-actions{display:flex;gap:16rpx}.export-actions button{flex:1}.prompt{margin-top:18rpx}

.step-name{margin-left:12rpx}.quota-card{box-shadow:0 5rpx 18rpx #172b4d04}

.quota-card{display:flex;flex-wrap:wrap;gap:12rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;padding:24rpx}.quota-card>view{flex:1;min-width:0}.quota-card>view>text{font-size:24rpx;color:#73839b}.quota-card>view>view{font-size:34rpx;font-weight:700;color:#246bfd;margin-top:4rpx}.quota-foot{display:block;width:100%;border-top:1rpx solid #edf2f8;padding-top:14rpx;margin-top:6rpx;font-size:24rpx;color:#73839b}.picker{background:#fff;border:2rpx dashed #c5d8f3;border-radius:20rpx;padding:30rpx 16rpx;text-align:center;color:#246bfd}.picker-icon{font-size:44rpx;line-height:1.2;margin-bottom:10rpx}.picker>view:not(.picker-icon){font-size:28rpx;font-weight:600}.picker>text{display:block;font-size:24rpx;color:#73839b;margin-top:8rpx}.file{display:flex;align-items:center;gap:16rpx;padding:14rpx 16rpx;margin-top:14rpx;border:1rpx solid #e5edf7;border-radius:16rpx;background:#fff}.file-label{font-size:20rpx;background:#edf4ff;color:#246bfd;padding:8rpx;border-radius:8rpx}.file>text{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:26rpx}.file-remove{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;background:transparent;color:#73839b;font-size:34rpx;padding:0;margin:0}.prompt{min-height:180rpx}.optional{font-size:24rpx;color:#8b99ae;font-weight:400;margin-left:12rpx}.estimate{padding:24rpx;border:1rpx solid #d5e5fc;border-radius:20rpx;background:#edf4ff;color:#47668f;font-size:24rpx;line-height:1.8;margin:24rpx 0}.estimate-title{font-size:28rpx;font-weight:600;color:#246bfd;margin-bottom:8rpx}.notice-link{color:#246bfd;display:inline-block;min-height:48rpx;padding-top:8rpx}.privacy{font-size:24rpx;line-height:1.8;color:#8b99ae;text-align:center;margin:18rpx 6rpx}.error{padding:20rpx 24rpx;background:#fff0f0;color:#b54747;border-radius:16rpx;margin-top:20rpx;font-size:26rpx}.error-inline{color:#b54747}.answer{margin-top:32rpx}.answer>.ui-section-title{margin:0 0 12rpx}.charged{color:#246bfd;font-size:26rpx;font-weight:600}.usage-details{font-size:24rpx;color:#73839b;margin:8rpx 0 24rpx;line-height:1.8}.answer-text{display:block;white-space:pre-wrap;font-size:28rpx;line-height:1.9;word-break:break-word}
</style>
