<template>
  <view class="pdf-work">
    <view class="tool-actions"><button open-type="share">分享 ↗</button><button @click="favorite">{{ liked ? '★ 已收藏' : '☆ 收藏工具' }}</button></view>
    <view v-if="error" class="error-message" role="alert">{{error}}</view>
    <template v-if="!busy && !result">
      <view class="intro"><PdfIcon :label="tool.label" :color="tool.color"/><view class="intro-title">{{ tool.title }}</view><text>{{ tool.hint }}</text></view>
      <view class="upload-box" @click="choose"><view class="cloud-upload">☁ ↑</view><view class="upload-name">{{ selected.length ? (['merge','images'].includes(tool.id) ? '继续添加文件' : '更换文件') : pickerLabel }}</view><text>支持 {{ tool.accept.join('、').toUpperCase() }} · 单个最大 20MB</text></view>
      <view v-if="tool.id==='merge'" class="pdf-tip">带密码的 PDF 无法直接合并，请先使用“PDF 解密”，再选择解密后的文件合并。<text class="decrypt-link" @click="uni.navigateTo({url:'/pages/pdf/work?tool=decrypt'})">去解密 ›</text></view>
      <!-- #ifdef MP-WEIXIN -->
      <view v-if="tool.id !== 'images'" class="pdf-tip">文件从微信聊天中选择。手机里的 PDF / Word 请先发送给“文件传输助手”，再从该聊天选择文件。选择文件不会向聊天发送消息。</view>
      <!-- #endif -->
      <view v-for="(file,i) in selected" :key="file.path" class="input-file"><image v-if="tool.id==='images'" :src="file.path" mode="aspectFill"/><PdfIcon v-else :label="tool.id==='word-pdf'?'W':'PDF'" :color="tool.color"/><view class="file-info"><text>{{ file.name }}</text><text class="muted">{{ size(file.size) }}</text></view><text v-if="i" @click="move(i)">↑</text><text @click="selected.splice(i,1)">×</text></view>
      <view v-if="tool.id==='split'" class="options"><view class="option-title">拆分方式</view><radio-group @change="splitMode=$event.detail.value"><label><radio value="pages" :checked="splitMode==='pages'" color="#087bff"/>逐页拆分</label><label><radio value="ranges" :checked="splitMode==='ranges'" color="#087bff"/>自定义范围</label></radio-group><input v-if="splitMode==='ranges'" v-model="ranges" placeholder="例如 1-3,5,8-10"/></view>
      <view v-if="['rotate','extract','remove-pages','reorder'].includes(tool.id)" class="options"><view class="option-title">{{tool.id==='reorder'?'全部页码的新顺序':'处理页码'}}</view><input v-model="pages" maxlength="1000" :placeholder="tool.id==='rotate'?'留空处理全部页面，例如 1-3,5':tool.id==='reorder'?'例如 3,1,2；填写全部页码':'例如 1-3,5'"/></view>
      <view v-if="tool.id==='rotate'" class="options"><view class="option-title">顺时针旋转</view><view class="choices"><text v-for="degree in [90,180,270]" :key="degree" :class="{active:angle===degree}" @click="angle=degree">{{degree}}°</text></view></view>
      <view v-if="tool.id==='page-numbers'" class="options"><view class="option-title">起始页码</view><input v-model="startNumber" type="number" maxlength="4" placeholder="1 至 9999"/><view class="choices"><text :class="{active:numberPosition==='bottom'}" @click="numberPosition='bottom'">底部居中</text><text :class="{active:numberPosition==='top'}" @click="numberPosition='top'">顶部居中</text></view></view>
      <view v-if="tool.id==='images'" class="options"><view class="option-title">页面方向 · 按当前图片顺序生成</view><view class="choices"><text :class="{active:orientation==='portrait'}" @click="orientation='portrait'">A4 竖版</text><text :class="{active:orientation==='landscape'}" @click="orientation='landscape'">A4 横版</text></view></view>
      <view v-if="tool.id==='pdf-images'" class="options"><view class="option-title">输出格式</view><view class="choices"><text :class="{active:format==='jpg'}" @click="format='jpg'">JPG</text><text :class="{active:format==='png'}" @click="format='png'">PNG</text></view></view>
      <view v-if="tool.id==='compress'" class="options"><view class="option-title">压缩强度</view><view class="choices"><text v-for="p in compressionLevels" :key="p.id" :class="{active:compression===p.id}" @click="compression=p.id">{{p.title}}</text></view></view>
      <view v-if="tool.id==='images'" class="options"><view class="option-title">输出方式</view><view class="choices"><text :class="{active:imageOutput==='merged'}" @click="imageOutput='merged'">合成一个 PDF</text><text :class="{active:imageOutput==='separate'}" @click="imageOutput='separate'">每张一个 PDF</text></view></view>
      <view v-if="tool.id==='watermark'" class="options"><view class="choices"><text :class="{active:watermarkMode==='text'}" @click="watermarkMode='text'">文字水印</text><text :class="{active:watermarkMode==='image'}" @click="watermarkMode='image'">图片水印</text></view><input v-if="watermarkMode==='text'" v-model="watermark" maxlength="40" placeholder="输入水印文字，最多40字"/><view v-else class="pdf-tip" @click="chooseWatermark">{{watermarkFile?'已选择水印图片，点击更换':'点击选择 JPG / PNG 水印图片'}}</view></view>
      <view v-if="['encrypt','decrypt'].includes(tool.id)" class="options"><view class="option-title">{{tool.id==='encrypt'?'设置密码':'输入有效密码'}}</view><input v-model="password" password maxlength="64" placeholder="请输入密码"/><input v-if="tool.id==='encrypt'" v-model="confirmation" password placeholder="再次输入密码"/></view>
      <view class="free-tip">{{ billingEnabled === false ? '免费使用 · 不限处理次数' : quota === null ? '正在获取使用状态…' : memberActive ? `月卡今日剩余 ${quota} 个文件 · 失败退回额度` : `今日免费剩余 ${quota} / 3 次 · 失败不扣次数` }}</view>
      <button class="pdf-primary" :disabled="!selected.length" @click="run">开始{{tool.id==='merge'?'合并':tool.id==='split'?'拆分':'处理'}}</button>
      <view class="assurance"><view><text>▤</text>格式处理</view><view><text>ϟ</text>无需注册</view><view><text>✓</text>临时存储</view></view>
      <view class="pdf-tip">文件仅用于本次处理，结果 30 分钟后自动删除。请及时保存到手机。密码不写入数据库。</view>
    </template>
    <view v-else-if="busy" class="processing"><image src="/static/brand/pdf-cat.png" mode="aspectFit"/><view class="progress-circle">{{ stagePercent === 0 ? '上传中' : stagePercent+'%' }}</view><view class="intro-title">{{ stage }}</view><text class="muted">进度反映处理阶段，复杂文件请耐心等待</text><view class="pdf-tip">{{ selected[0]?.name }}<br/>请保持页面打开，处理后及时下载</view></view>
    <view v-else class="result"><view class="success-mark">✓</view><view class="intro-title">处理完成！</view><view class="muted">结果可下载，30 分钟后自动删除</view><view class="result-file"><PdfIcon :label="result.extension.toUpperCase()" :color="tool.color"/><view><text>登科喵结果.{{result.extension}}</text><view class="muted">{{size(result.size_bytes)}}</view></view></view><button class="pdf-primary" :loading="saving" @click="save">下载文件</button><button v-if="preparedFile" class="pdf-primary" @click="openPrepared">{{preparedFile.extension==='zip'?'发送 / 保存到微信':'打开文件'}}</button><view v-if="preparedFile" class="muted">{{preparedFile.extension==='zip'?'可发送到文件传输助手，再保存或解压':'打开后使用右上角菜单保存或分享'}}</view><button class="pdf-secondary" @click="again">再处理一个</button><button class="pdf-secondary" @click="openTools">转换其他格式</button></view>
  </view>
</template>
<script setup>
import {ref,computed} from 'vue'
import {onLoad,onUnload,onShareAppMessage,onAddToFavorites} from '@dcloudio/uni-app'
import PdfIcon from '../../components/PdfIcon.vue'
import {pdfTools,favorites,toggleFavorite,pdfShare} from '../../lib/pdf-tools'
import {pdfRequest,submitPdf,savePdf,openSavedPdf} from '../../lib/pdf-api'
import {pollPdfJob} from '../../lib/pdf-job'
const tool=ref(pdfTools[0]), selected=ref([]), liked=ref(false), busy=ref(false), result=ref(null), error=ref(''),saving=ref(false),quota=ref(null),stagePercent=ref(0),stage=ref('正在上传文件…')
const billingEnabled=ref(null)
const memberActive=ref(false)
const pages=ref(''),angle=ref(90),startNumber=ref('1'),numberPosition=ref('bottom')
const preparedFile=ref(null)
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
onLoad(async options=>{tool.value=pdfTools.find(t=>t.id===options.tool)||pdfTools[0];uni.setNavigationBarTitle({title:tool.value.title});liked.value=favorites().includes(tool.value.id);await refreshQuota()})
onUnload(()=>{alive=false;password.value='';confirmation.value=''})
onShareAppMessage(()=>pdfShare(tool.value));onAddToFavorites(()=>({title:tool.value.title,query:'tool='+tool.value.id}))
function size(n){return n>1024*1024?(n/1024/1024).toFixed(1)+' MB':Math.ceil((n||0)/1024)+' KB'}
function favorite(){liked.value=toggleFavorite(tool.value.id).includes(tool.value.id);uni.showToast({title:liked.value?'已收藏常用工具':'已取消收藏',icon:'none'})}
async function refreshQuota(){try{const r=await pdfRequest('/quota');quota.value=r.remaining;billingEnabled.value=r.billingEnabled!==false;memberActive.value=r.membership?.active===true}catch(e){if(!error.value)error.value=e.message}}
function choose(){
 const max=tool.value.id==='merge'?10:tool.value.id==='images'?20:1
 const success=r=>{const files=r.tempFiles.map(f=>({path:f.path||f.tempFilePath,name:f.name||('image.'+(f.path?.split('.').pop()||'jpg')),size:f.size}));if(files.some(f=>f.size>20*1024*1024)){uni.showToast({title:'单个文件不能超过20MB',icon:'none'});return}selected.value=max===1?files.slice(0,1):[...selected.value,...files].slice(0,max);error.value=''}
 if(selected.value.length>=max&&max>1){uni.showToast({title:'已达到文件数量上限',icon:'none'});return}
 if(tool.value.id==='images'){uni.chooseImage({count:Math.min(9,max-selected.value.length),success});return}
 // #ifdef H5
 uni.chooseFile({count:Math.max(1,max-selected.value.length),type:'file',extension:tool.value.accept.map(e=>'.'+e),success})
 // #endif
 // #ifdef MP-WEIXIN
 wx.chooseMessageFile({count:Math.max(1,max-selected.value.length),type:'file',extension:tool.value.accept,success,fail:r=>{if(!String(r.errMsg||'').includes('cancel'))error.value='文件选择失败，请确认文件已下载到微信聊天中，再重试'}})
 // #endif
}
function move(i){selected.value.splice(i-1,0,selected.value.splice(i,1)[0])}
function chooseWatermark(){uni.chooseImage({count:1,success:r=>{const f=r.tempFiles[0];if(f.size>20*1024*1024){error.value='水印图片不能超过20MB';return}watermarkFile.value={path:f.path,size:f.size,name:'watermark.'+(f.path.split('.').pop()||'png')}}})}
async function run(){if(busy.value)return;error.value='';if(tool.value.id==='encrypt'&&(password.value.length<6||password.value.length>64)){error.value='密码需为 6 至 64 位';uni.showToast({title:error.value,icon:'none'});return}if(tool.value.id==='encrypt'&&password.value!==confirmation.value){error.value='两次密码不一致';return}if(tool.value.id==='merge'&&selected.value.length<2){error.value='请选择至少两个PDF';return}if(tool.value.id==='split'&&splitMode.value==='ranges'&&!ranges.value.trim()){error.value='请输入页码范围';return}
 busy.value=true;stagePercent.value=0;stage.value='正在上传文件…'
 try{const imageMark=tool.value.id==='watermark'&&watermarkMode.value==='image';if(imageMark&&!watermarkFile.value)throw new Error('请选择水印图片');const created=await submitPdf(tool.value.id,imageMark?[...selected.value,watermarkFile.value]:selected.value,{pages:pages.value,angle:angle.value,start:startNumber.value,position:numberPosition.value,ranges:splitMode.value==='ranges'?ranges.value:'',orientation:orientation.value,format:format.value,text:watermark.value,password:password.value,compression:compression.value,imageOutput:imageOutput.value,watermarkMode:watermarkMode.value});password.value='';confirmation.value='';
  const job=await pollPdfJob(()=>pdfRequest('/jobs/'+created.id),{active:()=>alive,onProgress:job=>{stagePercent.value=job.progress;stage.value=job.status==='syncing'?'文件已上传，正在同步任务状态…':job.status==='queued'?'文件已上传，等待处理…':'正在处理文件…'}})
  if(job){result.value=job;selected.value=[]}
 }catch(e){error.value=e.message;if(e.status===402)uni.navigateTo({url:'/pages/pdf/credits'})}finally{busy.value=false;if(alive)await refreshQuota()}}
async function save(){if(saving.value)return;saving.value=true;error.value='';try{preparedFile.value=await savePdf(result.value)||null;if(preparedFile.value)uni.showToast({title:'已下载，请点击下方打开或发送',icon:'none'})}catch(e){error.value=e.message||'下载失败，请重试'}finally{saving.value=false}}
function openPrepared(){error.value='';openSavedPdf(preparedFile.value).catch(e=>{if(!e.cancelled)error.value=e.message})}
function again(){result.value=null;preparedFile.value=null;error.value=''}
function openTools(){uni.switchTab({url:'/pages/tools/index'})}
</script>
<style scoped>
.pdf-work{padding:16rpx 28rpx 40rpx;background:linear-gradient(#fff,#f3f9ff);min-height:100vh}.tool-actions{display:flex;justify-content:flex-end;gap:10rpx}.tool-actions button{font-size:23rpx;background:white;color:#233045;padding:8rpx 15rpx;margin:0}.intro{display:flex;align-items:center;flex-direction:column;text-align:center;gap:16rpx;padding:30rpx 8rpx}.intro-title{font-size:34rpx;font-weight:800;color:#101c35}.intro>text{font-size:24rpx;line-height:1.6;color:#596c86;max-width:570rpx}.upload-box{background:#eef7ff;border:2rpx solid #d6eaff;border-radius:16rpx;padding:32rpx 20rpx;text-align:center}.cloud-upload{font-size:60rpx;color:#087bff;line-height:1.1}.upload-name{font-weight:700;margin:16rpx 0 8rpx}.upload-box>text{font-size:22rpx;color:#8391a7}.input-file{display:flex;align-items:center;gap:16rpx;padding:14rpx 8rpx;border-bottom:1rpx solid #eaf1f8;font-size:23rpx}.input-file image{width:66rpx;height:78rpx;border-radius:8rpx}.file-info{flex:1;min-width:0;display:flex;flex-direction:column}.file-info>text:first-child{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.options{margin-top:24rpx}.option-title{font-size:25rpx;font-weight:700;margin-bottom:12rpx}.options input{background:#fff;border:1rpx solid #dfebf7;border-radius:12rpx;padding:20rpx;height:78rpx;margin-top:10rpx;font-size:25rpx}.options label{display:flex;align-items:center;padding:8rpx;font-size:25rpx}.choices{display:flex;gap:12rpx}.choices text{flex:1;padding:18rpx;text-align:center;background:#f0f3f8;border-radius:10rpx;font-size:26rpx}.choices .active{background:#e7f3ff;border:1rpx solid #087bff;color:#087bff}.free-tip{text-align:center;font-size:22rpx;color:#7390ac;margin:22rpx 0 8rpx}.pdf-primary{background:#087bff;color:white;font-weight:700;border-radius:12rpx;padding:19rpx;margin-top:14rpx}.pdf-primary[disabled]{background:#b9d7fb;color:white}.pdf-secondary{border:1rpx solid #087bff;color:#087bff;background:white;border-radius:12rpx;padding:18rpx;margin-top:14rpx}.assurance{display:flex;justify-content:space-around;margin:28rpx 0;font-size:23rpx;color:#64728b}.assurance view{display:flex;flex-direction:column;align-items:center;gap:8rpx}.assurance text{color:#087bff;font-size:40rpx}.pdf-tip{background:#eaf5ff;color:#5e728c;line-height:1.7;padding:20rpx;border-radius:14rpx;font-size:23rpx;margin-top:18rpx}.processing,.result{text-align:center;padding-top:40rpx}.processing image{width:160rpx;height:160rpx}.progress-circle{width:220rpx;height:220rpx;border:16rpx solid #cce8ff;border-top-color:#087bff;border-right-color:#087bff;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:44rpx;font-weight:800;margin:24rpx auto}.processing .intro-title,.result .intro-title{margin:22rpx 0 12rpx}.success-mark{margin:30rpx auto;width:150rpx;height:150rpx;line-height:150rpx;border-radius:50%;background:#03c98c;color:white;font-size:80rpx;box-shadow:0 0 0 18rpx #e5faf1}.result-file{display:flex;align-items:center;gap:22rpx;text-align:left;background:#ecf6ff;border-radius:14rpx;padding:24rpx;margin:36rpx 0}.error-message{color:#bb4545;line-height:1.6;font-size:25rpx;text-align:center;margin-top:20rpx}
</style>
