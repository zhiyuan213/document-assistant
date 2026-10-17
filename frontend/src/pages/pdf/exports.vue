<template>
 <view class="pdf-screen exports-page" :style="headerPadding(24,true)">
  <PdfPageHeader title="读得更少，懂得更多。" eyebrow="AI 文档工作台" subtitle="整理重点报告，翻译常用语言。" badge="按实际用量扣额度"/>
  <view class="ui-heading">导出文件</view>
  <view class="ui-notice">文件只保留 30 分钟，请及时转发保存。</view>
  <view v-if="error" class="export-error">{{error}}</view>
  <view v-if="!files.length" class="empty">暂无可用的导出文件</view>
  <view v-for="file in files" :key="file.path" class="export-file ui-card" :class="{'file-selected':selected===file.path}" @click="selected=file.path">
   <view class="file-heading"><text class="file-type">{{file.extension.toUpperCase()}}</text><text class="file-name">{{file.name}}</text><view class="selection-dot" :class="{checked:selected===file.path}" /></view>
   <view class="file-details">导出时间：{{formatTime(file.exportedAt)}}</view>
   <view class="file-details">消耗 Token：{{formatTokens(file.inputTokens+file.outputTokens)}}</view>
  </view>
  <button v-if="files.length" class="ui-button" :disabled="!selected || sharing" :loading="sharing" @click="share">转发保存</button>
 </view>
</template>
<script setup>
import {ref} from 'vue'
import PdfPageHeader from '../../components/PdfPageHeader.vue'
import {headerPadding} from '../../lib/layout'
import {onShow,onHide,onUnload} from '@dcloudio/uni-app'
import {listExportFiles} from '../../lib/pdf-export-history'
import {shareSavedFile} from '../../lib/pdf-api'
const files=ref([]),selected=ref(''),sharing=ref(false),error=ref('')
let timer
function refresh(){
 try{files.value=listExportFiles();if(!files.value.some(file=>file.path===selected.value))selected.value=''}catch(e){error.value='无法读取导出记录，请稍后重试'}
}
function stop(){if(timer)clearInterval(timer);timer=null}
onShow(()=>{refresh();stop();timer=setInterval(refresh,10000)})
onHide(stop);onUnload(stop)
function formatTokens(value){return Number(value||0).toLocaleString('zh-CN')}
function formatTime(value){const d=new Date(value),pad=n=>String(n).padStart(2,'0');return d.getFullYear()+'-'+pad(d.getMonth()+1)+'-'+pad(d.getDate())+' '+pad(d.getHours())+':'+pad(d.getMinutes())+':'+pad(d.getSeconds())}
async function share(){
 if(sharing.value)return
 const path=selected.value;refresh()
 const file=files.value.find(item=>item.path===path)
 if(!file){error.value='文件已过期，请重新处理';return}
 sharing.value=true;error.value=''
 try{await shareSavedFile(file);uni.showToast({title:'转发成功',icon:'none'})}catch(e){if(!e.cancelled)error.value=e.message}finally{sharing.value=false}
}
</script>
<style scoped>
.exports-page{padding-top:28rpx}.export-file{margin:20rpx 0;border:2rpx solid #e5edf7}.file-selected{border-color:#246bfd;background:#f1f6ff}.file-heading{display:flex;align-items:center;gap:14rpx;margin-bottom:16rpx}.file-type{font-size:22rpx;color:#246bfd;background:#eaf2ff;padding:10rpx;border-radius:8rpx}.file-name{flex:1;min-width:0;word-break:break-all;font-size:28rpx;color:#253858}.selection-dot{width:32rpx;height:32rpx;border:2rpx solid #cad7e9;border-radius:50%;flex-shrink:0}.selection-dot.checked{border:9rpx solid #246bfd;background:#fff}.file-details{color:#73839b;font-size:24rpx;line-height:1.9}.export-error{color:#b54747;font-size:26rpx;padding:20rpx 0}
</style>
