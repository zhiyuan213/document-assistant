// 自定义导航页给微信状态栏/胶囊留出空间；H5沿用页面设计间距。
export function headerPadding(rpx, belowCapsule = false) {
  let statusHeight = 0
  // #ifdef MP-WEIXIN
  statusHeight = uni.getSystemInfoSync().statusBarHeight || 0
  if (belowCapsule) {
    const capsule = wx.getMenuButtonBoundingClientRect()
    return { paddingTop: (capsule.bottom || statusHeight + 44) + 10 + 'px' }
  }
  // #endif
  return { paddingTop: `calc(${statusHeight}px + ${rpx}rpx)` }
}
