<template><view class="pdf-icon" :style="{background: color}"><view class="fold"></view><text>{{ label }}</text></view></template>
<script setup>defineProps({ label: String, color: String })</script>
<style scoped>.pdf-icon{width:66rpx;height:78rpx;border-radius:8rpx;position:relative;display:flex;align-items:center;justify-content:center;color:white;box-shadow:0 6rpx 14rpx #14345115;font-size:23rpx;font-weight:800;flex-shrink:0}.fold{position:absolute;right:0;top:0;width:20rpx;height:20rpx;background:#ffffff65;border-radius:0 6rpx 0 5rpx}</style>
