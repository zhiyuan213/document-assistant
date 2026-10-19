<template>
  <view class="pdf-screen pdf-work">
    <PdfActions :liked="liked" @favorite="favorite"/>
    <view v-if="error" class="error-message" role="alert">{{error}}</view>
    <template v-if="!busy && !result">
      <view class="intro"><view class="intro-icon"><PdfIcon :label="tool.label" :color="tool.color"/></view><view class="intro-title">{{ tool.title }}</view><text>{{ tool.hint }}</text></view>
      <view class="upload-box" @click="choose"><view class="cloud-upload">☁ ↑</view><view class="upload-name">{{ selected.length ? '继续添加文件' : pickerLabel }}</view><text>支持 {{ tool.accept.join('、').toUpperCase() }} · 单个最大 20MB · 已选 {{selected.length}} / {{selectionLimit(tool.id)}}</text></view>
      <view v-if="!['merge','images','compare'].includes(tool.id)" class="pdf-tip">支持批量处理，每个文件单独生成结果；当前设置应用于本批文件。</view>
      <view v-if="tool.id==='merge'" class="pdf-tip">带密码的 PDF 无法直接合并，请先使用“PDF 解密”，再选择解密后的文件合并。<text class="decrypt-link" @click="uni.navigateTo({url:'/pages/pdf/work?tool=decrypt'})">去解密 ›</text></view>
      <!-- #ifdef MP-WEIXIN -->
      <view v-if="tool.id !== 'images'" class="pdf-tip">从微信聊天选择文件；手机文件可先发到文件传输助手。</view>
      <!-- #endif -->
      <view v-for="(file,i) in selected" :key="file.path" class="input-file"><image v-if="tool.id==='images'" :src="file.path" mode="aspectFill"/><PdfIcon v-else :label="tool.id==='word-pdf'?'W':'PDF'" :color="tool.color"/><view class="file-info"><text>{{ file.name }}</text><text class="muted">{{ size(file.size) }}</text></view><button v-if="i" class="file-control" aria-label="上移文件" @click="move(i)">↑</button><button class="file-control" :disabled="watermarkBusy" aria-label="移除文件" @click="selected.splice(i,1)">×</button></view>
      <view v-if="tool.id==='split'" class="options"><view class="option-title">拆分方式</view><radio-group @change="splitMode=$event.detail.value"><label><radio value="pages" :checked="splitMode==='pages'" color="#087bff"/>逐页拆分</label><label><radio value="ranges" :checked="splitMode==='ranges'" color="#087bff"/>自定义范围</label></radio-group><input v-if="splitMode==='ranges'" v-model="ranges" placeholder="例如 1-3,5,8-10"/></view>
      <view v-if="['rotate','extract','remove-pages','reorder'].includes(tool.id)" class="options"><view class="option-title">{{tool.id==='reorder'?'全部页码的新顺序':'处理页码'}}</view><input v-model="pages" maxlength="1000" :placeholder="tool.id==='rotate'?'留空处理全部页面，例如 1-3,5':tool.id==='reorder'?'例如 3,1,2；填写全部页码':'例如 1-3,5'"/></view>
      <view v-if="tool.id==='rotate'" class="options"><view class="option-title">顺时针旋转</view><view class="choices"><text v-for="degree in [90,180,270]" :key="degree" :class="{active:angle===degree}" @click="angle=degree">{{degree}}°</text></view></view>
      <view v-if="tool.id==='page-numbers'" class="options"><view class="option-title">起始页码</view><input v-model="startNumber" type="number" maxlength="4" placeholder="1 至 9999"/><view class="choices"><text :class="{active:numberPosition==='bottom'}" @click="numberPosition='bottom'">底部居中</text><text :class="{active:numberPosition==='top'}" @click="numberPosition='top'">顶部居中</text></view></view>
      <view v-if="tool.id==='crop'" class="options"><view class="option-title">裁剪边距（页面宽高的百分比）</view><view v-for="edge in cropEdges" :key="edge.id"><text>{{edge.title}}</text><input v-model="cropMargins[edge.id]" type="digit" placeholder="0 至 100，例如5"/></view></view>
      <view v-if="tool.id==='redact'" class="options"><view class="option-title">每页遮盖范围（百分比）</view><view v-for="edge in redactFields" :key="edge.id"><text>{{edge.title}}</text><input v-model="redactRegion[edge.id]" type="digit" placeholder="0 至 100"/></view><view class="pdf-tip">所有页面都会应用同一区域。下载后请打开检查遮盖位置，确认无遗漏再发送。</view></view>
      <view v-if="tool.id==='sign'" class="options"><view class="pdf-tip" @click="chooseWatermark">{{watermarkFile?'已选择签名图片，点击更换':'选择签名 JPG / PNG，建议使用透明背景PNG'}}</view><view class="option-title">签名页码</view><input v-model="signPage" type="number"/><view class="option-title">位置：从左、从上百分比 / 占页宽百分比</view><input v-model="signX" type="digit" placeholder="从左百分比"/><input v-model="signY" type="digit" placeholder="从上百分比"/><input v-model="signWidth" type="digit" placeholder="宽度百分比"/></view>
      <view v-if="tool.id==='images'" class="options"><view class="option-title">页面方向 · 按当前图片顺序生成</view><view class="choices"><text :class="{active:orientation==='portrait'}" @click="orientation='portrait'">A4 竖版</text><text :class="{active:orientation==='landscape'}" @click="orientation='landscape'">A4 横版</text></view></view>
      <view v-if="tool.id==='pdf-images'" class="options"><view class="option-title">输出格式</view><view class="choices"><text :class="{active:format==='jpg'}" @click="format='jpg'">JPG</text><text :class="{active:format==='png'}" @click="format='png'">PNG</text></view></view>
      <view v-if="tool.id==='compress'" class="options"><view class="option-title">压缩强度</view><view class="choices"><text v-for="p in compressionLevels" :key="p.id" :class="{active:compression===p.id}" @click="compression=p.id">{{p.title}}</text></view></view>
      <view v-if="tool.id==='images'" class="options"><view class="option-title">输出方式</view><view class="choices"><text :class="{active:imageOutput==='merged'}" @click="imageOutput='merged'">合成一个 PDF</text><text :class="{active:imageOutput==='separate'}" @click="imageOutput='separate'">每张一个 PDF</text></view></view>
      <view v-if="tool.id==='watermark'" class="options"><view class="choices"><text :class="{active:watermarkMode==='text'}" @click="watermarkMode='text'">文字水印</text><text :class="{active:watermarkMode==='image'}" @click="watermarkMode='image'">图片水印</text></view><input v-if="watermarkMode==='text'" v-model="watermark" maxlength="40" placeholder="输入水印文字，最多40字"/><view v-else class="pdf-tip" @click="chooseWatermark">{{watermarkFile?'已选择水印图片，点击更换':'点击选择 JPG / PNG 水印图片'}}</view></view>
      <view v-if="['encrypt','decrypt'].includes(tool.id)" class="options"><view class="option-title">{{tool.id==='encrypt'?'设置密码':'输入有效密码'}}</view><input v-model="password" password maxlength="64" placeholder="请输入密码"/><input v-if="tool.id==='encrypt'" v-model="confirmation" password placeholder="再次输入密码"/></view>
      <view v-if="tool.id==='remove-watermark' && selected.length" class="options">
       <button class="ui-button ui-button-secondary" :disabled="watermarkBusy" @click="inspectMarks">{{watermarkBusy?'正在处理…':'检测独立水印'}}</button>
       <view v-for="plan in markFiles" :key="plan.path" class="mark-file">
        <view class="option-title">{{plan.name}}</view><view class="pdf-tip">{{plan.message}}</view>
        <view v-for="item in plan.candidates" :key="item.id" class="mark-option" @click="toggleMark(plan,item.id)"><text>{{plan.ids.includes(item.id)?'☑':'☐'}} 第 {{item.page}} 页 · {{item.kind}}</text></view>
        <button v-if="plan.ids.length" class="ui-button ui-button-secondary" :disabled="watermarkBusy" @click="previewMarks(plan)">预览此文件去除效果</button>
        <view v-for="item in plan.previews" :key="item.page"><view class="option-title">第 {{item.page}} 页：处理前 / 处理后</view><view class="mark-previews"><image :src="item.before" mode="widthFix" @click="showMark(item.before)"/><image :src="item.after" mode="widthFix" @click="showMark(item.after)"/></view></view>
        <view v-if="plan.proof" class="pdf-tip">预览已准备好，请放大核对正文、图表是否完整。所有文件预览无误后再确认处理。</view>
       </view>
      </view>
      <view class="free-tip">基础 PDF 工具永久免费 · 不限次数</view>
      <button class="ui-button pdf-primary" :disabled="!selected.length || watermarkBusy || (tool.id==='remove-watermark' && !marksReady)" @click="run">{{tool.id==='remove-watermark'?'确认去除并生成新 PDF':'开始'+(tool.id==='merge'?'合并':tool.id==='split'?'拆分':'处理')}}</button>
      <view class="pdf-tip">文件仅用于本次处理，结果 30 分钟后自动删除。请及时保存到手机。密码不写入数据库。</view>
    </template>
    <view v-else-if="busy" class="processing">
      <image class="processing-cat" src="/static/brand/pdf-cat.png" mode="aspectFit"/>
      <view class="progress-card">
        <view class="progress-circle">
          <view class="progress-half progress-right"><view class="progress-fill" :style="{transform:'rotate('+(-180+Math.min(stagePercent,50)*3.6)+'deg)'}"/></view>
          <view class="progress-half progress-left"><view class="progress-fill" :style="{transform:'rotate('+(Math.max(stagePercent-50,0)*3.6)+'deg)'}"/></view>
          <view class="progress-center"><view class="progress-number"><text>{{stagePercent}}</text><text class="progress-unit">%</text></view><text class="progress-caption">处理进度</text></view>
        </view>
        <view class="processing-title">{{stage}}</view>
        <view class="processing-count">第 {{stageIndex}} / {{stageTotal}} 个文件</view>
        <view class="processing-filename">{{stageName}}</view>
      </view>
      <view class="processing-note">复杂文件请耐心等待<br/>请保持页面打开，处理后及时下载</view>
    </view>
    <view v-else class="result">
      <view class="success-mark">{{result.items.length?'✓':'!'}}</view><view class="intro-title">{{result.failures.length?'本批处理结束':'处理完成！'}}</view>
      <view class="muted">成功 {{result.items.length}} 个，失败 {{result.failures.length}} 个 · 结果保留30分钟</view>
      <view v-for="item in result.items" :key="item.job.id" class="result-file"><PdfIcon :label="item.job.extension.toUpperCase()" :color="tool.color"/><view><text>{{item.name}}</text><view class="muted">{{size(item.job.size_bytes)}}</view></view></view>
      <view v-for="item in result.failures" :key="item.sources.join('|')" class="error-message">{{item.name}}：{{item.message}}</view>
      <button v-if="result.items.length" class="ui-button pdf-primary" :loading="saving" :disabled="saving" @click="save">{{saving?downloadStage:'下载文件'}}</button>
      <button class="ui-button ui-button-secondary pdf-secondary" :disabled="saving" @click="again">{{selected.length?'重试未完成文件':'再处理一批'}}</button>
      <button class="ui-button ui-button-secondary pdf-secondary" :disabled="saving" @click="openTools">转换其他格式</button>
    </view>
  </view>
</template>
<script setup>
import {ref,computed,watch} from 'vue'
import {onLoad,onUnload,onHide,onShow,onShareAppMessage,onAddToFavorites} from '@dcloudio/uni-app'
import PdfIcon from '../../components/PdfIcon.vue'
import PdfActions from '../../components/PdfActions.vue'
import {pdfTools,favorites,toggleFavorite,pdfShare} from '../../lib/pdf-tools'
import {pdfRequest,submitPdf,savePdf} from '../../lib/pdf-api'
import {selectionLimit,appendSelection,runPdfBatch,downloadBatch} from '../../lib/pdf-batch'
import {createSmoothProgress} from '../../lib/pdf-progress'
import {rememberExport,queueDownloads,completeDownloads} from '../../lib/pdf-export-history'
import {pollPdfJob} from '../../lib/pdf-job'
const tool=ref(pdfTools[0]), selected=ref([]), liked=ref(false), busy=ref(false), result=ref(null), error=ref(''),saving=ref(false),stagePercent=ref(0),stage=ref('正在上传文件…')
const stageIndex=ref(1),stageTotal=ref(1),stageName=ref('')
const progressAnimation=createSmoothProgress({onChange:value=>{stagePercent.value=value}})
onHide(()=>progressAnimation.pause())
onShow(()=>progressAnimation.resume())
const pages=ref(''),angle=ref(90),startNumber=ref('1'),numberPosition=ref('bottom')
const cropMargins=ref({left:'5',right:'5',top:'5',bottom:'5'}),cropEdges=[{id:'left',title:'左边'},{id:'right',title:'右边'},{id:'top',title:'上边'},{id:'bottom',title:'下边'}]
const signPage=ref('1'),signX=ref('60'),signY=ref('60'),signWidth=ref('25')
const redactRegion=ref({redactX:'10',redactY:'10',redactWidth:'30',redactHeight:'10'}),redactFields=[{id:'redactX',title:'从左'},{id:'redactY',title:'从上'},{id:'redactWidth',title:'宽度'},{id:'redactHeight',title:'高度'}]
const downloadStage=ref('正在下载…')
const previewPaths=[]
function clearPreviewFiles(){
 // #ifdef MP-WEIXIN
 for(const filePath of previewPaths.splice(0))wx.getFileSystemManager().unlink({filePath,fail:()=>{}})
 // #endif
}
const markFiles=ref([]),watermarkBusy=ref(false)
const marksReady=computed(()=>selected.value.length>0&&selected.value.every(file=>markFiles.value.some(plan=>plan.path===file.path&&plan.proof)))
watch(()=>selected.value.map(f=>f.path).join('|'),()=>{clearPreviewFiles();markFiles.value=[]})
function toggleMark(plan,id){if(watermarkBusy.value)return;plan.ids=plan.ids.includes(id)?plan.ids.filter(x=>x!==id):[...plan.ids,id];plan.previews=[];plan.proof=''}
async function inspectMarks(){
 if(watermarkBusy.value)return
 watermarkBusy.value=true;error.value='';clearPreviewFiles();markFiles.value=[]
 try{for(const file of selected.value){
  if(!alive)break
  const plan={path:file.path,name:file.name,candidates:[],ids:[],previews:[],proof:'',message:''}
  try{const r=await submitPdf('remove-watermark',[file],{},'/watermarks/inspect');plan.candidates=r.candidates;plan.message=r.message}catch(e){plan.message=e.message}
  markFiles.value.push(plan)
 }}finally{watermarkBusy.value=false}
}
async function previewMarks(plan){
 if(watermarkBusy.value)return
 watermarkBusy.value=true;error.value='';plan.proof='';plan.previews=[]
 try{const file=selected.value.find(file=>file.path===plan.path);if(!file)return;const r=await submitPdf('remove-watermark',[file],{candidateIds:plan.ids.join(',')},'/watermarks/preview');plan.previews=await Promise.all(r.previews.map(async item=>({...item,before:await previewImage(item.before),after:await previewImage(item.after)})));plan.proof=r.proof}catch(e){error.value=e.message}finally{watermarkBusy.value=false}
}
async function previewImage(data){
 // #ifdef MP-WEIXIN
 const path=wx.env.USER_DATA_PATH+'/watermark-preview-'+Date.now()+'-'+Math.random().toString(36).slice(2)+'.png'
 await new Promise((resolve,reject)=>wx.getFileSystemManager().writeFile({filePath:path,data:data.split(',')[1],encoding:'base64',success:resolve,fail:reject}));previewPaths.push(path);if(!alive)clearPreviewFiles();return path
 // #endif
 // #ifdef H5
 return data
 // #endif
}
function showMark(url){uni.previewImage({urls:[url]})}
const splitMode=ref('pages'),ranges=ref(''),orientation=ref('portrait'),format=ref('jpg'),watermark=ref(''),password=ref(''),confirmation=ref('')
const compression=ref('standard'),imageOutput=ref('merged'),watermarkMode=ref('text'),watermarkFile=ref(null),compressionLevels=[{id:'light',title:'轻量 · 高清'},{id:'standard',title:'标准'},{id:'strong',title:'强力 · 小文件'}]
const pickerLabel=computed(()=>{
 // #ifdef MP-WEIXIN
 return tool.value.id==='images'?'从相册选择图片':'从微信聊天选择文件'
 // #endif
 // #ifdef H5
 return '点击选择文件'
 // #endif
})
let alive=true
onLoad(options=>{tool.value=pdfTools.find(t=>t.id===options.tool)||pdfTools[0];uni.setNavigationBarTitle({title:tool.value.title});liked.value=favorites().includes(tool.value.id)})
onUnload(()=>{alive=false;progressAnimation.stop();clearPreviewFiles();password.value='';confirmation.value=''})
onShareAppMessage(()=>pdfShare(tool.value));onAddToFavorites(()=>({title:tool.value.title,query:'tool='+tool.value.id}))
function size(n){return n>1024*1024?(n/1024/1024).toFixed(1)+' MB':Math.ceil((n||0)/1024)+' KB'}
function favorite(){liked.value=toggleFavorite(tool.value.id).includes(tool.value.id);uni.showToast({title:liked.value?'已收藏常用工具':'已取消收藏',icon:'none'})}
function choose(){
 if(watermarkBusy.value||busy.value)return
 const max=selectionLimit(tool.value.id)
 const success=r=>{const files=r.tempFiles.map(f=>({path:f.path||f.tempFilePath,name:f.name||('image.'+(f.path?.split('.').pop()||'jpg')),size:f.size}));try{selected.value=appendSelection(selected.value,files,tool.value.id);error.value=''}catch(e){error.value=e.message}}
 if(selected.value.length>=max&&max>1){uni.showToast({title:'已达到文件数量上限',icon:'none'});return}
 if(tool.value.id==='images'){uni.chooseImage({count:Math.min(9,max-selected.value.length),success});return}
 // #ifdef H5
 uni.chooseFile({count:Math.max(1,max-selected.value.length),type:'file',extension:tool.value.accept.map(e=>'.'+e),success})
 // #endif
 // #ifdef MP-WEIXIN
 wx.chooseMessageFile({count:Math.max(1,max-selected.value.length),type:'file',extension:tool.value.accept,success,fail:r=>{if(!String(r.errMsg||'').includes('cancel'))error.value='文件选择失败，请确认文件已下载到微信聊天中，再重试'}})
 // #endif
}
function move(i){if(watermarkBusy.value)return;selected.value.splice(i-1,0,selected.value.splice(i,1)[0])}
function chooseWatermark(){uni.chooseImage({count:1,success:r=>{const f=r.tempFiles[0];if(f.size>20*1024*1024){error.value='水印图片不能超过20MB';return}watermarkFile.value={path:f.path,size:f.size,name:'watermark.'+(f.path.split('.').pop()||'png')}}})}
async function run(){if(busy.value||watermarkBusy.value||!selected.value.length)return;if(tool.value.id==='remove-watermark'&&!marksReady.value)return;error.value='';if(tool.value.id==='encrypt'&&(password.value.length<6||password.value.length>64)){error.value='密码需为 6 至 64 位';uni.showToast({title:error.value,icon:'none'});return}if(tool.value.id==='encrypt'&&password.value!==confirmation.value){error.value='两次密码不一致';return}if(['merge','compare'].includes(tool.value.id)&&selected.value.length<2){error.value='请选择至少两个PDF';return}if(tool.value.id==='split'&&splitMode.value==='ranges'&&!ranges.value.trim()){error.value='请输入页码范围';return}
 busy.value=true;progressAnimation.start();stage.value='正在上传文件';stageIndex.value=1;stageTotal.value=selected.value.length;stageName.value=selected.value[0].name
 try{
  const options={...redactRegion.value,...cropMargins.value,signPage:signPage.value,signX:signX.value,signY:signY.value,signWidth:signWidth.value,pages:pages.value,angle:angle.value,start:startNumber.value,position:numberPosition.value,ranges:splitMode.value==='ranges'?ranges.value:'',orientation:orientation.value,format:format.value,text:watermark.value,password:password.value,compression:compression.value,imageOutput:imageOutput.value,watermarkMode:watermarkMode.value}
  const plans=Object.fromEntries(markFiles.value.map(plan=>[plan.path,{ids:[...plan.ids],proof:plan.proof}]))
  const batch=await runPdfBatch({tool:tool.value.id,title:tool.value.title,files:[...selected.value],options,imageFile:watermarkFile.value,watermarkPlans:plans,submit:submitPdf,active:()=>alive,
   poll:(id,onProgress)=>pollPdfJob(()=>pdfRequest('/jobs/'+id),{active:()=>alive,onProgress}),
   onProgress:state=>{progressAnimation.update(state.progress,state.ceiling);stage.value=state.phase==='uploading'?'正在上传文件':'正在处理文件';stageIndex.value=state.index+1;stageTotal.value=state.total;stageName.value=state.name}})
  if(alive)await progressAnimation.finish()
  if(alive){result.value=batch;const completed=new Set(batch.items.flatMap(item=>item.sources));selected.value=selected.value.filter(file=>!completed.has(file.path))}
  options.password=''
 }catch(e){error.value=e.message}finally{progressAnimation.stop();password.value='';confirmation.value='';busy.value=false}}
async function save(){
 if(saving.value||!result.value?.items.length)return
 saving.value=true;error.value=''
 try{
  // #ifdef H5
  for(const item of result.value.items)await savePdf({...item.job,name:item.name})
  uni.showToast({title:'已开始下载',icon:'none'});return
  // #endif
  // #ifdef MP-WEIXIN
  queueDownloads(result.value.items)
  const downloaded=await downloadBatch(result.value.items,{save:savePdf,remember:rememberExport,active:()=>alive,onProgress:(index,total)=>{downloadStage.value='正在下载 '+index+' / '+total}})
  completeDownloads(downloaded.saved.map(file=>file.jobId))
  if(!alive)return
  if(downloaded.failures.length){error.value=downloaded.failures.map(item=>item.name+'：'+item.message).join('\n');uni.showToast({title:'部分文件下载失败，可在文件列表重试',icon:'none'})}
  if(downloaded.saved.length){uni.setStorageSync('pdf-export-selected',downloaded.saved.map(file=>file.path));await uni.switchTab({url:'/pages/pdf/exports'})}
  // #endif
 }catch(e){error.value=e.message||'下载失败，请重试'}finally{saving.value=false}
}
function again(){result.value=null;error.value=''}
function openTools(){uni.switchTab({url:'/pages/tools/index'})}
</script>
<style scoped>
.mark-option{padding:20rpx 0;border-bottom:1rpx solid #e5edf7;font-size:26rpx}.mark-previews{display:flex;gap:12rpx}.mark-previews image{width:48%;background:#f4f6fa}

.pdf-work{background:#f6f9fe}.intro{display:grid;grid-template-columns:84rpx minmax(0,1fr);gap:6rpx 20rpx;align-items:center;padding:24rpx 0 28rpx}.intro-icon{grid-row:1/3}.intro-title{font-size:36rpx;font-weight:700;line-height:1.4;color:#172b4d}.intro>text{grid-column:2;font-size:26rpx;line-height:1.7;color:#73839b}.upload-box{background:#fff;border:2rpx dashed #c5d8f3;border-radius:20rpx;padding:34rpx 20rpx;text-align:center}.cloud-upload{font-size:48rpx;color:#246bfd;line-height:1.2}.upload-name{font-size:28rpx;font-weight:600;margin:16rpx 0 8rpx;color:#246bfd}.upload-box>text{font-size:24rpx;color:#73839b}.input-file{display:flex;align-items:center;gap:14rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:16rpx;padding:16rpx;margin-top:16rpx;font-size:26rpx}.input-file image{width:58rpx;height:66rpx;border-radius:8rpx}.file-info{flex:1;min-width:0;display:flex;flex-direction:column;gap:4rpx}.file-info>text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-control{display:flex;align-items:center;justify-content:center;width:64rpx;height:72rpx;flex-shrink:0;margin:0;padding:0;background:transparent;font-size:34rpx;color:#73839b}.options{padding:24rpx;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;margin-top:24rpx}.option-title{font-size:28rpx;font-weight:600;margin-bottom:14rpx}.options input{box-sizing:border-box;width:100%;height:88rpx;padding:20rpx;border:1rpx solid #dfe8f4;border-radius:14rpx;background:#fbfcff;margin-top:12rpx;font-size:28rpx;color:#172b4d}.options label{display:flex;align-items:center;min-height:76rpx;padding:8rpx 0;font-size:28rpx}.options>view>text{font-size:26rpx;color:#73839b}.choices{display:flex;gap:12rpx;margin-top:14rpx}.choices text{display:flex;align-items:center;justify-content:center;flex:1;padding:16rpx 8rpx;min-height:80rpx;text-align:center;background:#f3f6fb;border:1rpx solid transparent;border-radius:12rpx;font-size:26rpx;color:#73839b}.choices .active{background:#edf4ff;border-color:#246bfd;color:#246bfd}.free-tip{text-align:center;font-size:24rpx;color:#73839b;margin:24rpx 0 0}.pdf-tip{background:#eaf2ff;color:#627b9c;line-height:1.8;padding:20rpx 24rpx;border-radius:16rpx;font-size:24rpx;margin-top:20rpx}.decrypt-link{color:#246bfd;display:block;margin-top:8rpx;min-height:48rpx}.processing,.result{text-align:center;padding-top:32rpx}.processing .intro-title,.result .intro-title{margin:24rpx 0 12rpx}.success-mark{margin:32rpx auto;width:128rpx;height:128rpx;line-height:128rpx;border-radius:50%;background:#eaf2ff;color:#246bfd;font-size:64rpx}.result-file{display:flex;align-items:center;gap:20rpx;text-align:left;background:#fff;border:1rpx solid #e5edf7;border-radius:20rpx;padding:24rpx;margin:28rpx 0;font-size:28rpx}.result-file>view{min-width:0;word-break:break-word}.error-message{color:#b54747;background:#fff0f0;line-height:1.7;font-size:26rpx;padding:20rpx 24rpx;border-radius:16rpx;margin:20rpx 0}

.processing{padding-top:28rpx}
.processing-cat{display:block;width:120rpx;height:120rpx;margin:0 auto -10rpx;position:relative;z-index:1}
.progress-card{background:#fff;border:1rpx solid #e6eefb;border-radius:32rpx;padding:40rpx 28rpx 28rpx;box-shadow:0 16rpx 48rpx rgba(36,107,253,.06)}
.progress-circle{position:relative;width:252rpx;height:252rpx;margin:0 auto 28rpx;border-radius:50%;background:#e7effd}
.progress-half{position:absolute;top:0;width:50%;height:100%;overflow:hidden}
.progress-right{right:0}.progress-left{left:0}
.progress-fill{position:absolute;top:0;width:252rpx;height:252rpx;border-radius:50%;background:linear-gradient(to right,transparent 50%,#246bfd 50%)}
.progress-right .progress-fill{right:0}.progress-left .progress-fill{left:0}
.progress-center{position:absolute;top:12rpx;right:12rpx;bottom:12rpx;left:12rpx;background:#fff;border-radius:50%;display:flex;flex-direction:column;align-items:center;justify-content:center}
.progress-number{display:flex;align-items:baseline;justify-content:center;color:#246bfd;font-size:64rpx;font-weight:700;line-height:1;font-variant-numeric:tabular-nums}
.progress-unit{font-size:30rpx;margin-left:4rpx;font-weight:600}
.progress-caption{margin-top:16rpx;font-size:22rpx;line-height:1;color:#8999b1}
.processing-title{font-size:34rpx;font-weight:700;line-height:1.4;color:#172b4d}
.processing-count{display:inline-block;margin-top:14rpx;padding:6rpx 20rpx;border-radius:24rpx;background:#edf4ff;color:#4876c0;font-size:24rpx;line-height:1.5}
.processing-filename{margin-top:24rpx;padding-top:22rpx;border-top:1rpx solid #edf1f8;font-size:25rpx;line-height:1.7;color:#647893;word-break:break-all;text-align:left}
.processing-note{margin-top:26rpx;color:#8999b1;font-size:24rpx;line-height:1.8}
</style>
