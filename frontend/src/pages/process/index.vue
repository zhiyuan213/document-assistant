<template>
  <view class="page"
    ><view class="title">{{ tool.title }}</view
    ><view class="subtitle">{{ hints[tool.id] }}</view
    ><view v-if="!result" class="workspace"
      ><view class="upload" @click="choose"
        ><view class="plus">＋</view
        ><view class="upload-title">{{
          selected.length ? '继续添加文件' : isImage ? '点击添加图片' : '点击选择PDF'
        }}</view
        ><view class="muted">{{
          isImage ? '支持 JPG、PNG，单张最大20MB' : '单个PDF最大20MB，最多200页'
        }}</view></view
      ><view v-if="selected.length" class="selection"
        ><view v-for="(file, i) in selected" :key="file.path" class="selected-file"
          ><image
            v-if="isImage"
            :src="file.path"
            mode="aspectFill"
            class="thumbnail"
            @click="preview(file.path)"
          /><view v-else class="pdf-icon">PDF</view
          ><view class="file-label"
            ><view>{{ file.name }}</view
            ><view class="muted">{{ size(file.size) }}</view></view
          ><view class="file-actions"
            ><text v-if="i > 0" @click="move(i)">↑</text
            ><text @click="selected.splice(i, 1)">×</text></view
          ></view
        ><view v-if="selected.length > 1" class="muted sort-tip"
          >按当前顺序生成，点击 ↑ 调整顺序</view
        ></view
      ><view v-if="tool.id === 'images'" class="card settings"
        ><view class="section-title settings-title">PDF设置</view
        ><view class="setting-row"><text>纸张尺寸</text><text class="muted">A4</text></view
        ><view class="setting-row"
          ><text>页面方向</text
          ><view class="choices"
            ><text :class="{ active: !landscape }" @click="landscape = false">竖版</text
            ><text :class="{ active: landscape }" @click="landscape = true">横版</text></view
          ></view
        ><view class="setting-row"
          ><text>图片质量</text
          ><view class="choices"
            ><text :class="{ active: !highQuality }" @click="highQuality = false">标准</text
            ><text :class="{ active: highQuality }" @click="highQuality = true">高清 ♛</text></view
          ></view
        ></view
      ><view v-if="tool.id === 'compress'" class="card settings"
        ><view class="section-title settings-title">智能压缩</view
        ><view class="muted"
          >优化PDF中的图片，保留页面与文字。已压缩的文件可能无法进一步缩小；建议下载后检查图片清晰度。</view
        ></view
      ><view v-if="tool.id === 'ocr'" class="notice"
        >请选择文字清晰、光线均匀的图片。识别结果可能有误，请核对重要内容。</view
      ><button class="primary" :disabled="busy || !selected.length" :loading="busy" @click="run">
        {{ busy ? '正在处理，请稍候…' : actions[tool.id] }}</button
      ><view class="muted footer-note">处理成功后自动保存到「我的文件」</view></view
    ><view v-else
      ><view class="success"
        ><view class="success-mark">✓</view
        ><view class="success-title">{{ tool.id === 'ocr' ? '文字识别完成' : '资料处理完成' }}</view
        ><view class="subtitle">文件已保存，可随时在「我的文件」查看</view></view
      ><view class="card"
        ><view class="row"
          ><view
            ><view>{{ result.file.name }}</view
            ><view class="muted">{{ size(result.file.size_bytes) }}</view></view
          ><view class="result-type">{{ result.file.kind.toUpperCase() }}</view></view
        ><view v-if="tool.id === 'compress'" class="notice">{{
          result.savedBytes
            ? '已节省 ' + size(result.savedBytes)
            : '文件已优化，当前大小无需进一步压缩'
        }}</view
        ><textarea
          v-if="tool.id === 'ocr'"
          v-model="result.text"
          class="ocr-text"
          :maxlength="-1"
          auto-height
        /></view
      ><view v-if="tool.id === 'ocr'" class="grid"
        ><button class="secondary" @click="save">导出TXT</button
        ><button class="primary" @click="copy">复制文字</button></view
      ><button v-else class="primary" @click="save">下载 / 打开PDF</button
      ><button class="secondary" @click="reset">继续处理</button></view
    ></view
  >
</template>
<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { tools, size, toast } from '../../lib/tools'
import { processFiles, download } from '../../lib/api'
const tool = ref(tools[0]),
  selected = ref([]),
  landscape = ref(false),
  highQuality = ref(false),
  busy = ref(false),
  result = ref(null)
const isImage = computed(() => ['images', 'ocr'].includes(tool.value.id))
const hints = {
  images: '把零散图片，整理成一份清晰资料',
  compress: '更小的文件，更轻松地提交',
  merge: '多份PDF，按顺序合成一份',
  ocr: '拍照或选择图片，快速提取文字',
}
const actions = { images: '生成PDF', compress: '开始压缩', merge: '合并PDF', ocr: '开始识别' }
onLoad((query) => {
  tool.value = tools.find((t) => t.id === query.tool) || tools[0]
  uni.setNavigationBarTitle({ title: tool.value.title })
})
function choose() {
  if (busy.value) return
  const max = tool.value.id === 'images' ? 20 : tool.value.id === 'merge' ? 10 : 1
  if (selected.value.length >= max) {
    toast('已达到文件数量上限，请先移除文件')
    return
  }
  const success = (r) => {
    const files = r.tempFiles.map((f, i) => ({
      path: f.path || f.tempFilePath,
      name: f.name || '图片 ' + (selected.value.length + i + 1),
      size: f.size,
    }))
    if (files.some((f) => f.size > 20 * 1024 * 1024)) {
      toast('单个文件不能超过20MB')
      return
    }
    if (
      [...selected.value, ...files].reduce((sum, f) => sum + (f.size || 0), 0) >
      90 * 1024 * 1024
    ) {
      toast('总文件大小不能超过90MB')
      return
    }
    selected.value.push(...files.slice(0, max - selected.value.length))
  }
  const fail = (e) => {
    if (!String(e.errMsg).includes('cancel')) toast('选择文件失败，请重试')
  }
  if (isImage.value)
    uni.chooseImage({
      count: Math.min(9, max - selected.value.length),
      sourceType: ['album', 'camera'],
      success,
      fail,
    })
  else {
    // #ifdef H5
    uni.chooseFile({
      count: max - selected.value.length,
      type: 'file',
      extension: ['.pdf'],
      success,
      fail,
    })
    // #endif
    // #ifdef MP-WEIXIN
    wx.chooseMessageFile({
      count: max - selected.value.length,
      type: 'file',
      extension: ['pdf'],
      success,
      fail,
    })
    // #endif
  }
}
function move(i) {
  const f = selected.value.splice(i, 1)[0]
  selected.value.splice(i - 1, 0, f)
}
function preview(path) {
  uni.previewImage({ current: path, urls: selected.value.map((f) => f.path) })
}
async function run() {
  if (busy.value) return
  if (tool.value.id === 'merge' && selected.value.length < 2) {
    toast('请至少选择两个PDF')
    return
  }
  busy.value = true
  try {
    result.value = await processFiles(tool.value.id, selected.value, {
      landscape: landscape.value,
      highQuality: highQuality.value,
    })
    selected.value = []
  } catch (e) {
    toast(e.message || '处理失败，请重试')
  } finally {
    busy.value = false
  }
}
async function save() {
  try {
    await download(result.value.file)
  } catch (e) {
    toast(e.message || '下载失败')
  }
}
function copy() {
  uni.setClipboardData({ data: result.value.text, fail: () => toast('复制失败') })
}
function reset() {
  result.value = null
  selected.value = []
}
</script>
<style scoped>
.workspace {
  margin-top: 30rpx;
}
.upload {
  border: 2rpx dashed #c2aeff;
  border-radius: 24rpx;
  text-align: center;
  padding: 54rpx 20rpx;
  background: #f5f0ff;
}
.plus {
  width: 76rpx;
  height: 76rpx;
  background: #8050ff;
  border-radius: 50%;
  color: #fff;
  font-size: 54rpx;
  line-height: 68rpx;
  margin: 0 auto 24rpx;
}
.upload-title {
  font-weight: 600;
  margin-bottom: 12rpx;
}
.selection {
  margin-top: 24rpx;
}
.selected-file {
  background: #fff;
  border-radius: 18rpx;
  padding: 18rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-bottom: 14rpx;
}
.thumbnail {
  width: 94rpx;
  height: 94rpx;
  border-radius: 10rpx;
  flex-shrink: 0;
}
.pdf-icon {
  background: #ff606f;
  color: #fff;
  padding: 22rpx 10rpx;
  border-radius: 10rpx;
  font-size: 21rpx;
}
.file-label {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
  font-size: 25rpx;
}
.file-actions {
  display: flex;
  gap: 28rpx;
  color: #6f8eae;
  font-size: 36rpx;
}
.sort-tip {
  text-align: center;
  padding: 12rpx;
}
.settings {
  margin-top: 28rpx;
}
.settings-title {
  margin: 0 0 20rpx;
}
.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 18rpx 0;
  font-size: 25rpx;
}
.choices {
  display: flex;
  gap: 12rpx;
}
.choices text {
  background: #f2edff;
  color: #7291b6;
  padding: 12rpx 24rpx;
  border-radius: 12rpx;
}
.choices text.active {
  background: #713cff;
  color: #fff;
}
.footer-note {
  text-align: center;
  margin-top: 24rpx;
}
.success {
  text-align: center;
  padding: 48rpx 0 38rpx;
}
.success-mark {
  background: #e3f5ef;
  color: #26b68c;
  border-radius: 50%;
  width: 100rpx;
  height: 100rpx;
  line-height: 100rpx;
  font-size: 54rpx;
  margin: 0 auto 20rpx;
}
.success-title {
  font-size: 34rpx;
  font-weight: bold;
}
.result-type {
  background: #f0ebff;
  color: #713cff;
  border-radius: 10rpx;
  padding: 16rpx;
  font-size: 22rpx;
}
.ocr-text {
  width: 100%;
  min-height: 280rpx;
  margin-top: 28rpx;
  padding: 20rpx;
  background: #f7faff;
  border-radius: 12rpx;
  font-size: 28rpx;
  line-height: 1.8;
}
.grid button {
  width: 100%;
}
</style>
