<template>
 <view class="pdf-screen exports-page" :style="headerPadding(24,true)">
  <PdfPageHeader :vip="bannerVip" title="处理好的文件，都在这里。" eyebrow="文件列表" subtitle="选择文件，转发给好友或文件传输助手保存。" badge="支持多选转发"/>
  <view class="ui-heading">文件列表</view>
  <view class="ui-notice">文件只保留30分钟。单选转发原文件，多选打包为ZIP，包内保留每个文件。</view>
  <view v-if="error" class="export-error" role="alert">{{error}}</view>
  <view v-if="pending.length" class="ui-notice"><view>待下载 {{pending.length}} 个文件，下载失败的结果可在过期前重试。</view><view v-for="item in pending" :key="item.job.id">{{item.name}}</view><button class="ui-button ui-button-secondary" :disabled="retrying || sharing || preparing" :loading="retrying" @click="retryDownloads">重试下载并加入列表</button></view>
  <view v-if="!files.length && !pending.length" class="empty">暂无可用文件，处理完成后点击“下载文件”即可添加。</view>
  <view v-if="files.length" class="selection-bar"><button class="selection-button" :disabled="sharing || preparing" @click="selectAll">{{allSelected?'取消全选':'全选'}}</button><text>已选 {{selected.length}} 个 · 单次最多20个</text></view>
  <view v-for="file in files" :key="file.path" class="export-file ui-card" :class="{'file-selected':selected.includes(file.path)}" @click="toggle(file.path)">
   <view class="file-heading"><text class="file-type">{{file.extension.toUpperCase()}}</text><text class="file-name">{{file.name}}</text><view class="selection-dot" :class="{checked:selected.includes(file.path)}" /></view>
   <view class="file-details">保存时间：{{formatTime(file.exportedAt)}}<text v-if="file.size"> · {{formatSize(file.size)}}</text></view>
   <view v-if="file.source!=='tool'" class="file-details">消耗 Token：{{formatTokens(file.inputTokens+file.outputTokens)}}</view>
  </view>
  <button v-if="files.length" class="ui-button" :disabled="!selected.length || sharing || preparing" :loading="sharing || preparing" @click="share">{{preparing?'正在打包…':bundle?'转发保存 ZIP（'+selected.length+'个文件）':'转发保存'+(selected.length?'（'+selected.length+'）':'')}}</button>
 </view>
</template>
<script setup>
import {bannerVip,refreshBannerVip} from '../../lib/vip-banner'
import {formatCount as formatTokens} from '../../lib/format-number'
import {computed,nextTick,ref,watch} from 'vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import {headerPadding} from '../../lib/layout'
import {onTabItemTap,onShow,onHide,onUnload} from '@dcloudio/uni-app'
import {listExportFiles,resolveExportSelection,toggleExportSelection,pendingDownloads,completeDownloads,rememberExport} from '../../lib/pdf-export-history'
import {downloadBatch} from '../../lib/pdf-batch'
import {createFileBundle,discardBundle,retainBundle,bundleKey} from '../../lib/pdf-file-bundle'
import {shareSavedFile,savePdf} from '../../lib/pdf-api'
onShow(refreshBannerVip)
onTabItemTap(()=>nextTick(()=>uni.pageScrollTo({scrollTop:0,duration:0})))
const files=ref([]),selected=ref([]),sharing=ref(false),preparing=ref(false),error=ref(''),bundle=ref(null)
const pending=ref([]),retrying=ref(false)
const allSelected=computed(()=>files.value.length>0&&selected.value.length===Math.min(20,files.value.length)&&files.value.slice(0,20).every(file=>selected.value.includes(file.path)))
let timer,identity='',visible=false,alive=true
function clearBundle(){if(bundle.value)discardBundle(bundle.value);bundle.value=null;identity=''}
watch(()=>JSON.stringify(selected.value),clearBundle)
function refresh(){
 try{files.value=listExportFiles();pending.value=pendingDownloads();selected.value=selected.value.filter(path=>files.value.some(file=>file.path===path))}catch(e){error.value='无法读取文件列表，请稍后重试'}
}
function stop(){if(timer)clearInterval(timer);timer=null}
onShow(()=>{visible=true;refresh();const incoming=uni.getStorageSync('pdf-export-selected');if(Array.isArray(incoming)){selected.value=incoming.filter(path=>files.value.some(file=>file.path===path)).slice(0,20);uni.removeStorageSync('pdf-export-selected')}stop();timer=setInterval(refresh,10000)})
onHide(()=>{visible=false;stop();if(!sharing.value)clearBundle()})
onUnload(()=>{alive=false;visible=false;stop();if(!sharing.value)clearBundle()})
function toggle(path){if(sharing.value||preparing.value)return;try{selected.value=toggleExportSelection(selected.value,path);error.value=''}catch(e){error.value=e.message}}
function selectAll(){if(sharing.value||preparing.value)return;selected.value=allSelected.value?[]:files.value.slice(0,20).map(file=>file.path);error.value=''}
function formatSize(size){return size>1024*1024?(size/1024/1024).toFixed(1)+' MB':Math.ceil(size/1024)+' KB'}
function formatTime(value){const d=new Date(value),pad=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds())}
async function retryDownloads(){
 if(retrying.value||sharing.value||preparing.value)return
 retrying.value=true;error.value=''
 try{
  const result=await downloadBatch(pendingDownloads(),{save:savePdf,remember:rememberExport,active:()=>alive&&visible})
  completeDownloads(result.saved.map(file=>file.jobId));refresh()
  if(result.saved.length)selected.value=result.saved.map(file=>file.path).slice(0,20)
  if(result.failures.length)error.value=result.failures.map(item=>item.name+'：'+item.message).join('\n')
 }catch(e){error.value=e.message}finally{retrying.value=false}
}
async function share(){
 if(sharing.value||preparing.value||retrying.value)return
 error.value=''
 // Resolve the original selection before pruning expired entries; never silently send only a subset.
 let chosen
 try{chosen=resolveExportSelection(listExportFiles(),selected.value)}catch(e){error.value=e.message;refresh();return}
 // #ifdef H5
 error.value='请在微信小程序中转发保存文件';return
 // #endif
 // #ifdef MP-WEIXIN
 const key=bundleKey(chosen)
 if(chosen.length>1&&(!bundle.value||identity!==key||bundle.value.expiresAt<=Date.now())){
  clearBundle();preparing.value=true
  let packed
  try{
   packed=await createFileBundle(chosen)
   if(!alive||!visible||bundleKey(resolveExportSelection(files.value,selected.value))!==key){discardBundle(packed);return}
   bundle.value=packed;identity=key;uni.showToast({title:'已打包，请再次点击转发保存',icon:'none'})
  }catch(e){if(packed)discardBundle(packed);error.value=e.message}finally{preparing.value=false}
  return
 }
 sharing.value=true
 try{
  // Invoke the native chooser during this tap; file preparation requires a separate tap.
  const file=chosen.length===1?chosen[0]:retainBundle(bundle.value)
  await shareSavedFile(file)
  uni.showToast({title:'已交给微信发送',icon:'none'})
 }catch(e){if(!e.cancelled)error.value=e.message}finally{sharing.value=false;if(!visible)clearBundle()}
 // #endif
}
</script>
<style scoped>
.exports-page{padding-top:28rpx}.selection-bar{display:flex;align-items:center;justify-content:space-between;margin-top:24rpx;color:#73839b;font-size:24rpx}.selection-button{margin:0;padding:0 20rpx;color:#246bfd;background:#edf4ff;font-size:26rpx}.export-file{margin:20rpx 0;border:2rpx solid #e5edf7}.file-selected{border-color:#246bfd;background:#f1f6ff}.file-heading{display:flex;align-items:center;gap:14rpx;margin-bottom:16rpx}.file-type{font-size:22rpx;color:#246bfd;background:#eaf2ff;padding:10rpx;border-radius:8rpx}.file-name{flex:1;min-width:0;word-break:break-all;font-size:28rpx;color:#253858}.selection-dot{width:32rpx;height:32rpx;border:2rpx solid #cad7e9;border-radius:50%;flex-shrink:0}.selection-dot.checked{border:9rpx solid #246bfd;background:#fff}.file-details{color:#73839b;font-size:24rpx;line-height:1.9}.export-error{color:#b54747;font-size:26rpx;padding:20rpx 0;white-space:pre-wrap}
</style>
