<template>
 <view v-if="rendered" class="step-panel" :class="{'panel-closed':!open}" :aria-hidden="!open" :style="{height,opacity:shown?1:0,transform:shown?'translateY(0)':'translateY(-6px)'}">
  <view class="step-panel-inner"><slot /></view>
 </view>
</template>
<script setup>
import {ref,watch,nextTick,getCurrentInstance,onBeforeUnmount} from 'vue'
const props=defineProps({open:Boolean})
const instance=getCurrentInstance(),rendered=ref(props.open),shown=ref(props.open),height=ref(props.open?'auto':'0px')
let generation=0,timer,frame
function measure(callback){uni.createSelectorQuery().in(instance.proxy).select('.step-panel-inner').boundingClientRect(rect=>callback(rect?.height||0)).exec()}
function cancel(){clearTimeout(timer);clearTimeout(frame)}
watch(()=>props.open,async open=>{
 const token=++generation;cancel()
 if(open){
  rendered.value=true;shown.value=false;height.value='0px'
  await nextTick()
  measure(size=>{if(token!==generation)return;frame=setTimeout(()=>{if(token!==generation)return;shown.value=true;height.value=size+'px';timer=setTimeout(()=>{if(token===generation)height.value='auto'},240)},20)})
 }else if(rendered.value){
  measure(size=>{if(token!==generation)return;height.value=size+'px';frame=setTimeout(()=>{if(token!==generation)return;shown.value=false;height.value='0px';timer=setTimeout(()=>{if(token===generation)rendered.value=false},240)},20)})
 }
})
onBeforeUnmount(()=>{generation++;cancel()})
</script>
<style scoped>
.step-panel{overflow:hidden;transition:height .24s ease,opacity .18s ease,transform .24s ease;transform-origin:top}.panel-closed{pointer-events:none}.step-panel-inner{display:flex;flex-direction:column}
</style>
