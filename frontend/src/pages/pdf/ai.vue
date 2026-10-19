<template>
 <view class="pdf-screen ai-page" :style="headerPadding(24,true)">
  <PdfPageHeader :vip="status.vip===true" title="读得更少，
懂得更多。" eyebrow="AI 文档工作台" subtitle="整理重点报告，翻译常用语言。" badge="按实际用量扣额度"/>
  <view class="ui-section-row"><text class="ui-section-title">AI 文档助手</text><text class="ui-link" @click="openCredits">我的额度 ›</text></view>
  <view v-if="status.testMode" class="ui-notice">测试模式 · 不扣用户额度。实际调用仍产生接口费用，每日上限仍有效。</view>
  <view v-if="statusError" class="ui-notice">{{statusError}}<text class="notice-link" @click="refreshStatus">重新检查 ›</text></view>
  <view v-else-if="!statusLoaded" class="ui-notice">正在检查 AI 服务…</view>
  <view v-else-if="!status.enabled" class="ui-notice">AI 服务配置尚未生效，请联系管理员检查后端配置。</view>
  <view v-else-if="!status.canUse" class="ui-notice">额度不足，开通月卡或购买加油包后即可使用。<text class="notice-link" @click="openCredits">查看方案 ›</text></view>
  <view v-else class="quota-card"><view><text>今日免费额度</text><view>{{formatCredits(status.freeRemainingTokenCredits)}}</view></view><view><text>会员月额度</text><view>{{formatCredits(status.monthlyRemainingTokenCredits)}}</view></view><view><text>加油包额度</text><view>{{formatCredits(status.topupRemainingTokenCredits)}}</view></view><text class="quota-foot">今日还可处理 {{status.dailyRemaining}} 次{{status.testMode?' · 测试模式免费体验':''}}</text></view>
  <button class="step-header" :aria-expanded="activeStep==='mode'" @click="toggleStep('mode')"><text class="step-title">01 选择 AI 功能</text><text v-if="activeStep!=='mode'" class="step-summary">{{current.title}}</text><text class="step-arrow">{{activeStep==='mode'?'−':'＋'}}</text></button>
  <PdfStepPanel :open="activeStep==='mode'"><view class="ui-tool-grid"><PdfToolTile v-for="(item,index) in modes" :key="item.id" :label="modeLabels[index]" :color="modeColors[index]" :title="item.title" :caption="item.brief" :selected="mode===item.id" :disabled="busy || translationPending" @click="selectMode(item.id)"/></view></PdfStepPanel>
  <button class="step-header" :aria-expanded="activeStep==='files'" @click="toggleStep('files')"><text class="step-title">02 添加 PDF 文件</text><text v-if="activeStep!=='files'" class="step-summary">{{selected.length?'已选择 '+selected.length+' 个文件':'未选择'}}</text><text class="step-arrow">{{activeStep==='files'?'−':'＋'}}</text></button>
  <PdfStepPanel :open="activeStep==='files'">
  <view class="picker" @click="chooseFiles"><view class="picker-icon">＋</view><view>{{selected.length?'继续添加 PDF':pickerText}}</view><text>支持批量处理 · 最多20个 · 单个不超过20MB</text></view>
  <view v-for="(file,index) in selected" :key="file.path" class="file"><view class="file-label">PDF</view><text>{{file.name}}</text><button class="file-remove" :disabled="busy || translationPending" aria-label="移除文件" @click="removeFile(index)">×</button></view>
  </PdfStepPanel>
  <button v-if="mode==='translate'" class="step-header" :aria-expanded="activeStep==='language'" @click="toggleStep('language')"><text class="step-title">03 选择目标语言</text><text v-if="activeStep!=='language'" class="step-summary">{{languages[languageIndex].title}}</text><text class="step-arrow">{{activeStep==='language'?'−':'＋'}}</text></button>
  <PdfStepPanel v-if="mode==='translate'" :open="activeStep==='language'"><picker :disabled="busy || translationPending" :range="languages" range-key="title" :value="languageIndex" @change="languageIndex=Number($event.detail.value)"><view class="ui-input language-picker">{{languages[languageIndex].title}} <text>点击选择 ›</text></view></picker></PdfStepPanel>
  <button v-if="(mode && selected.length && !answer) || busy || translationPending" class="ui-button process-button" :disabled="busy || exporting || (!translationPending && (!statusLoaded || !status.enabled || (status.canUse && (status.dailyRemaining<1 || !selected.length))))" :loading="busy" @click="primaryAction">{{busy?busyLabel:translationPending?'恢复翻译进度':statusError?'AI 服务连接异常':!statusLoaded?'正在检查 AI 服务':!status.enabled?'AI 服务配置未生效':!status.canUse?'充值 AI 额度':!status.dailyRemaining?'今日次数已用完':current.action}}</button>
  <view v-if="error" class="error" role="alert">{{error}}</view>
  <view v-if="stopReason" class="ui-notice">{{stopReason}}。已完成 {{batchResults.length}} 个，未处理 {{remainingFiles.length}} 个。<text v-if="batchResults.length">可下载已完成结果。</text></view>
  <view v-for="item in batchFailures" :key="item.file.path" class="error">{{item.file.name}}：{{item.message}}</view>
  <button v-if="answer" class="step-header" :aria-expanded="activeStep==='result'" @click="toggleStep('result')"><text class="step-title">{{mode==='translate'?'04 翻译结果':mode==='mindmap'?'03 脑图结果':mode==='ppt'?'03 PPT结果':'03 总结结果'}}</text><text class="step-arrow">{{activeStep==='result'?'−':'＋'}}</text></button>
  <PdfStepPanel v-if="answer" :open="activeStep==='result'"><view class="answer ui-card">
   <view v-for="item in batchResults" :key="item.id" class="completed-file"><text>✓ {{item.file.name}}</text></view>
   <view class="export-label">文件格式</view><view class="export-actions"><button v-for="format in exportFormats" :key="format.id" class="ui-button ui-button-secondary" :class="{'format-selected':exportFormat===format.id}" :disabled="exporting || busy" @click="chooseExportFormat(format.id)">{{format.title}}</button></view>
   <view class="export-actions delivery-actions"><button class="ui-button" :disabled="exporting || busy" :loading="exporting" @click="deliverReport">{{exporting?downloadLabel:'下载'}}</button></view>
   <button v-if="remainingFiles.length && !translationPending" class="ui-button ui-button-secondary" :disabled="busy || exporting" @click="continueBatch">继续处理剩余 {{remainingFiles.length}} 个文件</button>
   <button class="ui-button ui-button-secondary" :disabled="busy || exporting || translationPending" @click="resetWorkspace">处理新一批</button>
  </view></PdfStepPanel>
  <PdfAiBilling v-if="!status.testMode" />
 </view>
</template>
<script setup>
import {formatCount as formatCredits} from '../../lib/format-number'
import {nextTick,ref,computed,watch} from 'vue'
import {rememberExport,exportExpiry} from '../../lib/pdf-export-history'
import PdfAiBilling from '../../components/pdf-ai-billing.vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import PdfToolTile from '../../components/PdfToolTile.vue'
import PdfStepPanel from '../../components/PdfStepPanel.vue'
import {headerPadding} from '../../lib/layout'
import {onTabItemTap,onShow,onUnload} from '@dcloudio/uni-app'
import {pdfRequest,submitPdfAi,exportAiReport} from '../../lib/pdf-api'
import {appendSelection} from '../../lib/pdf-batch'
import {runAiBatch} from '../../lib/pdf-ai-batch'

onTabItemTap(()=>{if(!batchResults.value.length)resetWorkspace();nextTick(()=>uni.pageScrollTo({scrollTop:0,duration:0}))})
const activeStep=ref('mode')
function toggleStep(step){if(step!=='mode'&&!mode.value){activeStep.value='mode';return}activeStep.value=activeStep.value===step?'':step}
const modeColors=['#5875df','#388bcb','#198b83','#d77932'],modeLabels=['总','译','图','演']
const modes=[
 {id:'summary',title:'AI 总结',brief:'重点 · 结论 · 待办',action:'生成总结报告'},
 {id:'translate',title:'AI 翻译',brief:'常用语言互译',action:'翻译文档'},
 {id:'mindmap',title:'AI 脑图',brief:'梳理主题 · 提炼分支',action:'生成脑图'},
 {id:'ppt',title:'AI 生成 PPT',brief:'提炼重点 · 汇报演示',action:'生成 PPT'}
]
const languages=[{id:'zh-CN',title:'简体中文'},{id:'zh-TW',title:'繁体中文'},{id:'en',title:'英语'},{id:'ja',title:'日语'},{id:'ko',title:'韩语'},{id:'fr',title:'法语'},{id:'de',title:'德语'},{id:'es',title:'西班牙语'},{id:'pt',title:'葡萄牙语'},{id:'ru',title:'俄语'},{id:'ar',title:'阿拉伯语'},{id:'it',title:'意大利语'}]
const languageIndex=ref(0),exporting=ref(false),exportFile=ref(null)
const translationJob=ref(''),translationWarnings=ref([]),translationPending=ref(false)
const translationKey='pdf-translation-job-v1',translationRequestKey='pdf-translation-request-v1'
const mode=ref(''),selected=ref([]),page=ref('1'),answer=ref(''),error=ref(''),busy=ref(false),busyLabel=ref('正在处理…'),chargedCredits=ref(null),usageDetails=ref(null)
const batchResults=ref([]),batchFailures=ref([]),remainingFiles=ref([]),stopReason=ref(''),downloadLabel=ref('正在下载…')
const translationContextKey='pdf-translation-context-v1'
let alive=true,runningLabel=''
onUnload(()=>{alive=false})
let resultExpiresAt=0
const exportFormat=ref('pdf')
const exportFormats=computed(()=>mode.value==='ppt'?[{id:'pptx',title:'PPTX'}]:mode.value==='mindmap'?[{id:'png',title:'PNG'},{id:'pdf',title:'PDF'},{id:'xmind',title:'XMind'}]:[{id:'docx',title:'Word'},{id:'pdf',title:'PDF'}])
const preparedFiles=new Map()
let exportGeneration=0
const statusLoaded=ref(false),statusError=ref('')
const status=ref({enabled:false,vip:false,testMode:false,canUse:false,freeRemainingTokenCredits:0,monthlyRemainingTokenCredits:0,topupRemainingTokenCredits:0,dailyLimit:100,dailyRemaining:0,monthlyTokenCredits:2000000,usedTokenCredits:0,remainingTokenCredits:0})
const current=computed(()=>modes.find(x=>x.id===mode.value)||{title:'未选择',action:'请选择 AI 功能'})
let miniProgram=false
// #ifdef MP-WEIXIN
miniProgram=true
// #endif
const pickerText=computed(()=>miniProgram?'从微信聊天选择 PDF':'点击选择 PDF 文件')
watch([mode,languageIndex],()=>{if(!busy.value&&!translationPending.value&&!exporting.value){clearResults();clearTranslation()}})
onShow(()=>{refreshStatus();if(!busy.value&&(uni.getStorageSync(translationKey)||uni.getStorageSync(translationRequestKey)))resumeTranslation(true)})
let statusRequestId=0
async function refreshStatus(strict=false){
 const requestId=++statusRequestId
 statusError.value=''
 try{
  const latest=await pdfRequest('/ai/status')
  if(requestId!==statusRequestId)return latest
  status.value=latest;statusLoaded.value=true;return latest
 }catch(e){
  if(requestId!==statusRequestId){if(strict===true)throw e;return}
  statusError.value=e.status===404?'当前服务器尚未提供 AI 接口，请部署包含 AI 功能的后端版本。':'无法更新 AI 额度，请点击重新检查。'
  if(strict===true)throw e
 }
}
function resetWorkspace(){
 if(busy.value||translationPending.value||exporting.value)return
 mode.value='';activeStep.value='mode';selected.value=[];error.value='';clearResults();clearTranslation()
}
function clearResults(){exportGeneration++;preparedFiles.clear();batchResults.value=[];batchFailures.value=[];remainingFiles.value=[];stopReason.value='';answer.value='';exportFile.value=null;usageDetails.value=null;chargedCredits.value=null}
function clearTranslation(){translationJob.value='';translationWarnings.value=[];translationPending.value=false;uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey);uni.removeStorageSync(translationContextKey)}
function selectMode(id){if(busy.value||exporting.value||translationPending.value)return;activeStep.value='files';mode.value=id;error.value='';clearResults();clearTranslation()}
function chooseFiles(){
 if(!mode.value){activeStep.value='mode';return}
 if(busy.value||exporting.value||translationPending.value)return
 if(selected.value.length>=20){error.value='每批最多选择20个PDF';return}
 const success=r=>{const files=(r.tempFiles||[]).map(f=>({path:f.path||f.tempFilePath,name:f.name||'document.pdf',size:f.size||0}));if(!files.length)return;try{selected.value=appendSelection(selected.value,files,'ai');activeStep.value=mode.value==='translate'?'language':'';error.value='';clearResults();clearTranslation()}catch(e){error.value=e.message}}
 // #ifdef H5
 uni.chooseFile({count:20-selected.value.length,type:'file',extension:['.pdf'],success,fail:()=>{}})
 // #endif
 // #ifdef MP-WEIXIN
 wx.chooseMessageFile({count:20-selected.value.length,type:'file',extension:['pdf'],success,fail:r=>{if(!String(r.errMsg||'').includes('cancel'))error.value='选择 PDF 失败，请重试'}})
 // #endif
}
function removeFile(index){if(busy.value||exporting.value||translationPending.value)return;selected.value.splice(index,1);activeStep.value='files';clearResults();clearTranslation()}
function primaryAction(){if(translationPending.value){resumeTranslation();return}if(!status.value.canUse){openCredits();return}run()}
async function run(continuing=false){
 if(!mode.value){activeStep.value='mode';return}
 if(busy.value)return
 if(translationPending.value){resumeTranslation();return}
 if(exporting.value)return
 error.value=''
 if(continuing!==true)clearResults()
 if(!selected.value.length){error.value='请选择PDF文件';return}
 busy.value=true;activeStep.value=''
 try{
  const batch=await runAiBatch({files:[...selected.value],readStatus:()=>refreshStatus(true),process:processFile,onResult:applyResult,active:()=>alive,
   onProgress:(index,total,file)=>{runningLabel=index+' / '+total+'：'+file.name;busyLabel.value='正在处理 '+runningLabel}})
  batchFailures.value=[...batchFailures.value,...batch.failed];remainingFiles.value=batch.remaining;stopReason.value=batch.stopReason
  if(batchResults.value.length)activeStep.value='result'
  if(batch.stopReason&&!batchResults.value.length)error.value=batch.stopReason
 }
 catch(e){error.value=e.message||'AI 分析失败，请重试'}
 finally{await refreshStatus();busy.value=false}
}
async function processFile(file){
 let requestId=''
 if(mode.value==='translate'){
  translationJob.value=''
  requestId=Date.now()+'-'+Math.random().toString(36).slice(2);uni.setStorageSync(translationRequestKey,requestId)
  const completed=batchResults.value.map(item=>({id:item.id,file:item.file,mode:item.mode,translationJobId:item.translationJobId,expiresAt:item.expiresAt,answer:'',usage:{inputTokens:item.usage?.inputTokens,outputTokens:item.usage?.outputTokens}}))
  uni.setStorageSync(translationContextKey,{file,language:languages[languageIndex.value].id,completed});translationPending.value=true
 }
 try{
  let result=await submitPdfAi(mode.value,[file],'',1,languages[languageIndex.value].id,requestId)
  if(result.translationJobId){translationJob.value=result.translationJobId;translationPending.value=true;uni.setStorageSync(translationKey,result.translationJobId);result=await waitTranslation(result.translationJobId)}
  return result
 }catch(e){
  if(mode.value==='translate'&&e.status>=400&&e.status<500&&!translationJob.value)clearTranslation()
  if(translationPending.value)e.uncertain=true
  throw e
 }
}
function applyResult(result,file=uni.getStorageSync(translationContextKey)?.file||selected.value[0]||{name:'AI文档.pdf',path:''}){
 activeStep.value='result';exportFormat.value=mode.value==='ppt'?'pptx':'pdf';resultExpiresAt=exportExpiry(result.expiresAt)
 if(result.targetLanguage){const index=languages.findIndex(l=>l.id===result.targetLanguage);if(index>=0)languageIndex.value=index}
 const jobId=result.translationJobId||translationJob.value
 const id=mode.value==='translate'&&jobId?jobId:Date.now()+'-'+Math.random().toString(36).slice(2)
 if(!batchResults.value.some(item=>item.id===id))batchResults.value.push({id,file:{...file},mode:mode.value,answer:result.answer,translationJobId:mode.value==='translate'?jobId:'',expiresAt:resultExpiresAt,usage:result})
 answer.value=result.answer;chargedCredits.value=result.usedTokenCredits;usageDetails.value=result;translationWarnings.value=result.warnings||[]
 translationPending.value=false;uni.removeStorageSync(translationKey);uni.removeStorageSync(translationRequestKey);uni.removeStorageSync(translationContextKey)
}
function continueBatch(){if(busy.value||exporting.value)return;if(!status.value.canUse){openCredits();return}selected.value=[...remainingFiles.value];remainingFiles.value=[];stopReason.value='';return run(true)}
async function waitTranslation(id){
 for(let i=0;i<180;i++){
  if(!alive)throw Object.assign(new Error('翻译仍在后台处理，可重新进入页面恢复'),{uncertain:true})
  if(i)await new Promise(resolve=>setTimeout(resolve,5000))
  const state=await pdfRequest('/ai/translations/'+encodeURIComponent(id))
  busyLabel.value=runningLabel+' 正在翻译并排版 '+Number(state.progress||0)+'%…'
  if(state.status==='completed')return state
  if(state.status==='failed'){clearTranslation();throw Object.assign(new Error(state.message||'翻译未完成，请重试'),{status:422})}
 }
 throw new Error('任务仍在后台处理中，重新进入本页会自动恢复进度，请勿重复提交。')
}
async function resumeTranslation(onlyPending=false){
 if(busy.value)return
 let id=translationJob.value||uni.getStorageSync(translationKey)
 const requestId=uni.getStorageSync(translationRequestKey)
 if(!id&&!requestId)return
 busy.value=true;translationPending.value=true;error.value=''
 try{
  const context=uni.getStorageSync(translationContextKey)
  if(!batchResults.value.length&&Array.isArray(context?.completed)){
   batchResults.value=context.completed.filter(item=>item.expiresAt>Date.now())
   if(batchResults.value.length)answer.value='已生成翻译文件'
  }
  mode.value='translate'
  if(!id){const state=await pdfRequest('/ai/translations/by-request/'+encodeURIComponent(requestId));id=state.translationJobId;uni.setStorageSync(translationKey,id)}
  if(onlyPending){
   const state=await pdfRequest('/ai/translations/'+encodeURIComponent(id))
   if(state.status==='completed'){
    translationJob.value=id;applyResult(state,context?.file)
    remainingFiles.value=remainingFiles.value.filter(file=>file.path!==context?.file?.path);stopReason.value=''
    return
   }
  }
  translationJob.value=id;applyResult(await waitTranslation(id),context?.file)
  remainingFiles.value=remainingFiles.value.filter(file=>file.path!==context?.file?.path);stopReason.value=''
 }catch(e){
  error.value=e.message
  if(e.status===404){
   translationPending.value=false;translationJob.value='';uni.removeStorageSync(translationKey)
   if(e.message==='请求路径不存在')error.value='服务器尚未提供翻译任务查询接口，请部署最新后端版本后重试。'
   else if(id){error.value='';uni.removeStorageSync(translationRequestKey);uni.removeStorageSync(translationContextKey);if(!selected.value.length&&!batchResults.value.length){mode.value='';activeStep.value='mode'}}
  }
 }finally{await refreshStatus();busy.value=false}
}

function reports(){return batchResults.value.length?batchResults.value:answer.value?[{id:'legacy',file:selected.value[0]||{name:'AI文档.pdf'},mode:mode.value,answer:answer.value,translationJobId:mode.value==='translate'?translationJob.value:'',expiresAt:resultExpiresAt,usage:usageDetails.value}]:[]}
function exportIdentity(item){return JSON.stringify([exportGeneration,item.id,item.mode,item.translationJobId,item.answer])}
async function exportReport(format,item=reports()[0]){
 if(!item||!item.answer&&!item.translationJobId)return null
 if(item.expiresAt && item.expiresAt<=Date.now()){error.value='文件已超过30分钟，请重新处理';return null}
 const generation=exportGeneration,identity=exportIdentity(item),key=identity+format
 const details={name:(item.file.name||'AI文档').replace(/\.[^.]+$/,'')+(item.mode==='translate'?'_译文':item.mode==='mindmap'?'_脑图':item.mode==='ppt'?'_演示':'_总结')+'.'+format,expiresAt:item.expiresAt||undefined,inputTokens:item.usage?.inputTokens,outputTokens:item.usage?.outputTokens}
 try{
  let pending=preparedFiles.get(key)
  if(!pending){pending=exportAiReport(item.answer,format,item.translationJobId,item.mode).then(file=>generation===exportGeneration?rememberExport(file,details):null);preparedFiles.set(key,pending)}
  const file=await pending||null
  if(generation!==exportGeneration)return null
  if(exportFormat.value===format)exportFile.value=file
  return file
 }catch(e){preparedFiles.delete(key);if(generation===exportGeneration)error.value=e.message;return null}
}
function chooseExportFormat(format){
 if(exporting.value||busy.value)return
 exportFormat.value=format;exportFile.value=null
 if(miniProgram&&reports().length===1)exportReport(format)
}
async function deliverReport(){
 if(exporting.value||busy.value||!reports().length)return
 error.value=''
 exporting.value=true
 try{
  const items=[...reports()],saved=[],failed=[]
  for(let index=0;index<items.length&&alive;index++){
   downloadLabel.value='正在下载 '+(index+1)+' / '+items.length
   const file=await exportReport(exportFormat.value,items[index])
   if(file)saved.push(file);else if(miniProgram)failed.push(items[index].file.name)
  }
  if(failed.length)error.value='以下文件下载失败，可重新下载：'+failed.join('、')
  if(miniProgram&&saved.length&&alive){uni.setStorageSync('pdf-export-selected',saved.map(file=>file.path));await uni.switchTab({url:'/pages/pdf/exports'})}
  if(!miniProgram)uni.showToast({title:'已开始下载',icon:'none'})
 }catch(e){error.value=e.message}finally{exporting.value=false}
}
function openCredits(){uni.navigateTo({url:'/pages/pdf/credits'})}
function back(){uni.switchTab({url:'/pages/tools/index'})}
</script>
<style scoped>
.step-header{display:flex;align-items:center;gap:16rpx;width:100%;padding:24rpx 0;margin:14rpx 0 0;background:transparent;text-align:left;color:#253858;line-height:1.5}.step-title{font-size:29rpx;font-weight:600;flex-shrink:0}.step-summary{margin-left:auto;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:#73839b;font-size:23rpx}.step-arrow{margin-left:auto;flex-shrink:0;color:#246bfd;font-size:28rpx}.step-summary+.step-arrow{margin-left:0}.process-button{margin-top:24rpx}

.delivery-actions{margin-top:24rpx}
.completed-file{font-size:26rpx;color:#47668f;padding:14rpx 0;line-height:1.7;word-break:break-all;border-bottom:1rpx solid #edf2f8}
.export-label{font-size:26rpx;font-weight:600;color:#253858;margin:24rpx 0 12rpx}.format-selected{background:#edf4ff!important;border-color:#246bfd!important;color:#246bfd!important}
.language-picker{display:flex;justify-content:space-between}.language-picker text{color:#246bfd;font-size:24rpx}.export-actions{display:flex;gap:16rpx}.export-actions button{flex:1}

.step-name{margin-left:12rpx}.quota-card{box-shadow:0 5rpx 18rpx #172b4d04}

.quota-card{display:flex;flex-wrap:wrap;gap:12rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;padding:24rpx}.quota-card>view{flex:1;min-width:0}.quota-card>view>text{font-size:24rpx;color:#73839b}.quota-card>view>view{font-size:34rpx;font-weight:700;color:#246bfd;margin-top:4rpx}.quota-foot{display:block;width:100%;border-top:1rpx solid #edf2f8;padding-top:14rpx;margin-top:6rpx;font-size:24rpx;color:#73839b}.picker{background:#fff;border:2rpx dashed #c5d8f3;border-radius:20rpx;padding:30rpx 16rpx;text-align:center;color:#246bfd}.picker-icon{font-size:44rpx;line-height:1.2;margin-bottom:10rpx}.picker>view:not(.picker-icon){font-size:28rpx;font-weight:600}.picker>text{display:block;font-size:24rpx;color:#73839b;margin-top:8rpx}.file{display:flex;align-items:center;gap:16rpx;padding:14rpx 16rpx;margin-top:14rpx;border:1rpx solid #e5edf7;border-radius:16rpx;background:#fff}.file-label{font-size:20rpx;background:#edf4ff;color:#246bfd;padding:8rpx;border-radius:8rpx}.file>text{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:26rpx}.file-remove{display:flex;align-items:center;justify-content:center;width:64rpx;height:64rpx;background:transparent;color:#73839b;font-size:34rpx;padding:0;margin:0}.optional{font-size:24rpx;color:#8b99ae;font-weight:400;margin-left:12rpx}.estimate{padding:24rpx;border:1rpx solid #d5e5fc;border-radius:20rpx;background:#edf4ff;color:#47668f;font-size:24rpx;line-height:1.8;margin:24rpx 0}.estimate-title{font-size:28rpx;font-weight:600;color:#246bfd;margin-bottom:8rpx}.notice-link{color:#246bfd;display:inline-block;min-height:48rpx;padding-top:8rpx}.privacy{font-size:24rpx;line-height:1.8;color:#8b99ae;text-align:center;margin:18rpx 6rpx}.error{padding:20rpx 24rpx;background:#fff0f0;color:#b54747;border-radius:16rpx;margin-top:20rpx;font-size:26rpx}.error-inline{color:#b54747}.answer{margin-top:0}.answer>.ui-section-title{margin:0 0 12rpx}.charged{color:#246bfd;font-size:26rpx;font-weight:600}.usage-details{font-size:24rpx;color:#73839b;margin:8rpx 0 24rpx;line-height:1.8}.answer-text{display:block;white-space:pre-wrap;font-size:28rpx;line-height:1.9;word-break:break-word}
</style>
