// The server selects the channel. Never silently fall back to a different payment contract.
export function requestPdfPayment(order, native) {
  return new Promise((resolve, reject) => {
    if (order.paymentProvider === 'virtual') {
      if (typeof native.requestVirtualPayment !== 'function') { reject(new Error('当前微信版本不支持虚拟支付，请更新微信后重试')); return }
      const { mode, signData, paySig, signature } = order
      if (mode !== 'short_series_goods' || !signData || !paySig || !signature) { reject(new Error('支付参数不完整，请重新发起购买')); return }
      native.requestVirtualPayment({ mode, signData, paySig, signature, success: resolve, fail: reject })
    } else if (order.paymentProvider === 'wechat') {
      const { timeStamp, nonceStr, package: pkg, signType, paySign } = order
      native.requestPayment({ timeStamp, nonceStr, package: pkg, signType, paySign, success: resolve, fail: reject })
    } else reject(new Error('支付服务版本不匹配，请稍后重试'))
  })
}
