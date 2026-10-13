<template>
  <view class="me-page">
    <view class="profile-header" :style="headerPadding(46)"
      ><view class="profile-row"
        ><image :src="mascotSrc" class="avatar" mode="aspectFit" /><view class="profile-copy"
          ><view class="nickname">{{ nickname }}</view
          ><view class="profile-subtitle">让好资料陪你上岸！</view></view
        ><text class="profile-setting" @click="settings">⚙</text></view
      ><button v-if="!me" class="login-button" :loading="loading" @click="signIn">
        登录登科喵，收藏喜欢的资料
      </button></view
    >
    <view class="me-content"
      ><view class="membership" @click="vip"
        ><view class="crown">♛</view
        ><view class="membership-copy"
          ><view>{{ me?.quota.premium ? '我的会员权益' : '开通会员' }}</view
          ><text>让你的资料整理更轻松！</text></view
        ><button>{{ me?.quota.premium ? '查看权益' : '了解权益' }}</button></view
      >
      <view v-if="error" class="notice">{{ error }}<text @click="signIn"> 重新登录</text></view>
      <view class="menu-card"
        ><view class="menu-row" @click="downloads('completed')"
          ><view class="menu-icon">⇩</view><text>我的下载</text
          ><text class="menu-count">{{ summary.downloads }}</text
          ><text class="chevron">›</text></view
        ><view class="menu-row" @click="uni.switchTab({ url: '/pages/favorites/index' })"
          ><view class="menu-icon orange">☆</view><text>我的收藏</text
          ><text class="menu-count">{{ summary.favorites }}</text
          ><text class="chevron">›</text></view
        ><view class="menu-row" @click="downloads('all')"
          ><view class="menu-icon gray">◷</view><text>下载记录</text
          ><text class="chevron">›</text></view
        ><view class="menu-row" @click="feedback"
          ><view class="menu-icon gray">✎</view><text>意见反馈</text
          ><text class="chevron">›</text></view
        ></view
      >
      <view class="menu-card tools-card"
        ><view class="menu-row" @click="uni.navigateTo({ url: '/pages/tools/index' })"
          ><view class="menu-icon">▧</view><text>文件处理工具</text
          ><text class="chevron">›</text></view
        ><view class="menu-row" @click="uni.navigateTo({ url: '/pages/files/index' })"
          ><view class="menu-icon">▤</view><text>我的文件</text
          ><text class="menu-count">{{ summary.files }}</text
          ><text class="chevron">›</text></view
        ><view class="menu-row" @click="settings"
          ><view class="menu-icon">⚙</view><text>设置</text><text class="chevron">›</text></view
        ></view
      >
      <view v-if="me" class="quota-card"
        ><view>文件工具免费次数</view
        ><text
          >今日剩余
          {{ me.quota.premium ? '不限' : me.quota.remaining + ' / ' + me.quota.dailyLimit }}</text
        ></view
      ><view class="version">登科喵资料助手 · 好资料 · 好未来</view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request, login, ensureLogin } from '../../lib/api'
import { mascotSrc } from '../../lib/brand'
import { toast } from '../../lib/tools'
import { resetDownloads } from '../../lib/downloads'
import { headerPadding } from '../../lib/layout'
const me = ref(null),
  error = ref(''),
  loading = ref(false),
  summary = ref({ favorites: 0, downloads: 0, files: 0 })
const nickname = computed(() =>
  !me.value || me.value.user.nickname === '开发体验用户' ? '登科喵同学' : me.value.user.nickname,
)
onShow(async () => {
  error.value = ''
  if (!uni.getStorageSync('token')) {
    me.value = null
    summary.value = { favorites: 0, downloads: 0, files: 0 }
    return
  }
  try {
    me.value = await request('/me')
    summary.value = await request('/library/summary')
  } catch (e) {
    me.value = null
    error.value = e.message
  }
})
async function signIn() {
  loading.value = true
  error.value = ''
  try {
    await login()
    me.value = await request('/me')
    summary.value = await request('/library/summary')
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}
function vip() {
  uni.navigateTo({ url: '/pages/vip/index' })
}
function downloads(filter) {
  uni.navigateTo({ url: '/pages/downloads/index?filter=' + filter })
}
function settings() {
  uni.showActionSheet({
    itemList: ['关于登科喵', '资料与隐私', '退出登录'],
    success: (r) => {
      if (r.tapIndex === 2) {
        resetDownloads()
        uni.removeStorageSync('token')
        me.value = null
        summary.value = { favorites: 0, downloads: 0, files: 0 }
        toast('已退出登录')
      } else
        uni.showModal({
          title: r.tapIndex === 0 ? '关于登科喵' : '资料与隐私',
          content:
            r.tapIndex === 0
              ? '登科喵陪你发现好资料，也帮你处理PDF和文字。好资料，好未来。'
              : '收藏与下载记录仅本人可查看。使用OCR时图片会发送至腾讯云识别；自己处理的文件可在「我的文件」中删除。',
          showCancel: false,
        })
    },
  })
}
function feedback() {
  uni.showModal({
    title: '意见反馈',
    editable: true,
    placeholderText: '写下你的建议（5–1000字）',
    success: async (r) => {
      if (!r.confirm) return
      try {
        await ensureLogin()
        await request('/feedback', 'POST', { content: r.content })
        toast('感谢反馈，已收到你的建议')
      } catch (e) {
        toast(e.message)
      }
    },
  })
}
</script>
<style scoped>
.me-page {
  background: #f9f7fe;
  min-height: 100vh;
  max-width: 960rpx;
  margin: auto;
}
.profile-header {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, #bb9bff, #8051f7);
  padding: 46rpx 32rpx 110rpx;
  padding-top: calc(var(--status-bar-height, 0px) + 46rpx);
}
.profile-header::after {
  content: '';
  position: absolute;
  width: 560rpx;
  height: 300rpx;
  border-radius: 50%;
  background: #ffffff16;
  right: -140rpx;
  bottom: -220rpx;
  transform: rotate(-20deg);
}
.profile-row {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 22rpx;
  color: white;
}
.avatar {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
  background: #f7efff;
  border: 4rpx solid #fff8;
  flex-shrink: 0;
}
.profile-copy {
  flex: 1;
}
.nickname {
  font-size: 32rpx;
  font-weight: 650;
}
.profile-subtitle {
  font-size: 23rpx;
  color: #f2eaff;
  margin-top: 12rpx;
}
.profile-setting {
  font-size: 40rpx;
  color: #eee0ff;
  padding: 10rpx;
}
.login-button {
  background: #ffffff22;
  color: #fff;
  font-size: 24rpx;
  border-radius: 30rpx;
  padding: 14rpx;
  margin: 24rpx 0 -36rpx;
  position: relative;
  z-index: 1;
}
.me-content {
  position: relative;
  padding: 0 28rpx 40rpx;
  margin-top: -64rpx;
  z-index: 2;
}
.membership {
  display: flex;
  align-items: center;
  gap: 16rpx;
  background: linear-gradient(120deg, #ffdf9f, #fff0d1);
  border-radius: 22rpx;
  padding: 26rpx 20rpx;
  color: #704714;
  box-shadow: 0 8rpx 16rpx #a9873910;
}
.crown {
  font-size: 55rpx;
  color: #e7a427;
}
.membership-copy {
  flex: 1;
}
.membership-copy > view {
  font-size: 28rpx;
  font-weight: 700;
}
.membership-copy > text {
  font-size: 20rpx;
  display: block;
  margin-top: 5rpx;
  color: #9c783b;
}
.membership button {
  margin: 0;
  font-size: 21rpx;
  background: linear-gradient(120deg, #f7c467, #eaa82e);
  color: white;
  border-radius: 30rpx;
  padding: 11rpx 20rpx;
  flex-shrink: 0;
}
.menu-card {
  background: #fff;
  border-radius: 22rpx;
  margin-top: 24rpx;
  padding: 0 20rpx;
  box-shadow: 0 6rpx 18rpx #6b47a306;
}
.menu-row {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 24rpx 6rpx;
  border-bottom: 1rpx solid #f5f1fa;
  font-size: 27rpx;
  color: #4b425f;
}
.menu-row:last-child {
  border-bottom: 0;
}
.menu-icon {
  width: 38rpx;
  flex-shrink: 0;
  text-align: center;
  color: #804bf6;
  font-size: 34rpx;
}
.orange {
  color: #eda351;
}
.gray {
  color: #665e76;
}
.chevron {
  color: #b9afc6;
  margin-left: auto;
  font-size: 36rpx;
}
.menu-count {
  color: #ada1bb;
  font-size: 22rpx;
  margin-left: auto;
}
.menu-count + .chevron {
  margin-left: 0;
}
.tools-card .menu-row {
  padding: 22rpx 6rpx;
}
.quota-card {
  display: flex;
  justify-content: space-between;
  padding: 26rpx 10rpx;
  color: #a39ab3;
  font-size: 22rpx;
}
.quota-card text {
  color: #8d6bc4;
}
.version {
  text-align: center;
  font-size: 21rpx;
  color: #b8abc6;
  margin-top: 20rpx;
}
</style>
