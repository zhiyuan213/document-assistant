<template>
 <button :class="['pdf-tool-tile',{selected}]" :disabled="disabled" :aria-label="title" @click="$emit('click')"><view class="tile-top"><PdfIcon :label="label" :color="color"/><text class="tile-arrow">{{selected?'✓':'↗'}}</text></view><text class="tile-title">{{title}}</text><text class="tile-caption">{{caption}}</text></button>
</template>
<script setup>
import PdfIcon from './PdfIcon.vue'
defineProps({label:String,title:String,caption:String,color:String,selected:Boolean,disabled:Boolean})
defineEmits(['click'])
</script>
<style scoped>
.pdf-tool-tile{display:flex;flex-direction:column;align-items:flex-start;gap:8rpx;width:100%;min-width:0;min-height:236rpx;margin:0;padding:24rpx;background:#fff;border:1rpx solid #e4e8ee;border-radius:20rpx;line-height:1.5;text-align:left;box-sizing:border-box;box-shadow:0 4rpx 14rpx #172b4d03}.pdf-tool-tile::after{border:0}.tile-top{width:100%;display:flex;align-items:center;justify-content:space-between;margin-bottom:10rpx}.tile-arrow{font-size:28rpx;color:#aab4c3}.tile-title{font-size:28rpx;font-weight:700;color:#202b40;line-height:1.4}.tile-caption{font-size:23rpx;font-weight:400;color:#7c8595;line-height:1.5}.pdf-tool-tile.selected{background:#eff5ff;border:2rpx solid #246bfd;padding:23rpx}.selected .tile-arrow{color:#246bfd}.pdf-tool-tile:active{background:#f0f5ff}.pdf-tool-tile[disabled]{opacity:.65}
</style>
