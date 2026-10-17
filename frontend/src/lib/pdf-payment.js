// The server selects the channel. Never silently fall back to a different payment contract.
export function requestPdfPayment(order, native, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    // Some clients return from the cashier without delivering either callback.
    // Timeout means unknown, never unpaid: the page must query the saved order.
    const timer = setTimeout(() => reject(new Error('支付结果暂未确认，请查询订单，不要重复付款')), timeoutMs)
    const success = value => { clearTimeout(timer); resolve(value) }
    const fail = error => { clearTimeout(timer); reject(error) }
    const failNow = message => fail(new Error(message))
    if (order.paymentProvider === 'virtual') {
      if (typeof native.requestVirtualPayment !== 'function') { failNow('当前微信版本不支持虚拟支付，请更新微信后重试'); return }
      const { mode, signData, paySig, signature } = order
      if (mode !== 'short_series_goods' || !signData || !paySig || !signature) { failNow('支付参数不完整，请重新发起购买'); return }
      try { native.requestVirtualPayment({ mode, signData, paySig, signature, success, fail }) } catch (e) { fail(e) }
    } else if (order.paymentProvider === 'wechat') {
      const { timeStamp, nonceStr, package: pkg, signType, paySign } = order
      try { native.requestPayment({ timeStamp, nonceStr, package: pkg, signType, paySign, success, fail }) } catch (e) { fail(e) }
    } else failNow('支付服务版本不匹配，请稍后重试')
  })
}
