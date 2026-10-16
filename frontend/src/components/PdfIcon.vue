<template><view class="pdf-icon" :style="{'--icon-color':color||'#3478ee'}"><view class="icon-sheet"><view class="fold"></view><text>{{ label }}</text></view></view></template>
<script setup>defineProps({ label: String, color: String })</script>
<style scoped>
.pdf-icon{width:84rpx;height:84rpx;border-radius:22rpx;display:flex;align-items:center;justify-content:center;background:#f4f6fa;flex-shrink:0}.icon-sheet{width:48rpx;height:60rpx;border-radius:6rpx;position:relative;display:flex;align-items:center;justify-content:center;background:var(--icon-color);color:#fff;font-size:21rpx;font-weight:800;box-shadow:0 6rpx 12rpx #172b4d12}.fold{position:absolute;right:0;top:0;width:14rpx;height:14rpx;background:#ffffff70;border-radius:0 5rpx 0 4rpx}
</style>
