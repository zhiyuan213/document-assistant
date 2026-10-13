<template>
  <view class="page"
    ><view class="title">工具中心</view><view class="subtitle">高效 · 简单 · 便捷</view
    ><view class="section-title">PDF工具</view
    ><view class="grid"
      ><ToolCard
        v-for="tool in tools.filter((t) => t.id !== 'ocr')"
        :key="tool.id"
        :tool="tool" /></view
    ><view class="section-title">文字识别</view
    ><view class="grid"><ToolCard :tool="tools[3]" /></view
    ><view class="notice"
      >图片转PDF、PDF合并、PDF压缩与文字识别，覆盖日常资料处理。更多工具会在后续版本开放。</view
    ></view
  >
</template>
<script setup>
import ToolCard from '../../components/ToolCard.vue'
import { tools } from '../../lib/tools'
</script>
