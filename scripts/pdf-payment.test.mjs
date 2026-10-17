import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
const paymentSource=await readFile(new URL('../frontend/src/lib/pdf-payment.js',import.meta.url),'utf8')
const {requestPdfPayment}=await import('data:text/javascript,'+encodeURIComponent(paymentSource))
const pageSource=(await readFile(new URL('../frontend/src/pages/pdf/credits.vue',import.meta.url),'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
const parameters={paymentProvider:'virtual',orderId:'owned-order',mode:'short_series_goods',signData:'{"goodsPrice":990,"productId":"ai_monthly"}',paySig:'signed',signature:'session-signature'}
test('virtual payment receives only signed platform fields and never falls back',async()=>{
 let passed,legacy=false
 await requestPdfPayment(parameters,{requestVirtualPayment(p){passed=p;p.success({})},requestPayment(){legacy=true}})
 assert.equal(passed.signData,parameters.signData);assert.equal(passed.orderId,undefined);assert.equal(passed.sessionKey,undefined);assert.equal(legacy,false)
 await assert.rejects(requestPdfPayment(parameters,{requestPayment(){legacy=true}}),/更新微信/);assert.equal(legacy,false)
 await assert.rejects(requestPdfPayment({...parameters,paySig:''},{requestVirtualPayment(){throw Error('must not call')}}),/参数不完整/)
})
function page({member=false,cancel=false,platform=true,confirmAfter=1,queryFails=false,cashierError=false}={}){
 const requests=[],modals=[],switches=[],storage=new Map();let polls=0,onShow
 const context={ref:v=>({value:v}),computed:f=>({get value(){return f()}}),onShow:f=>{onShow=f},requestPdfPayment,setTimeout:f=>{queueMicrotask(f)},wx:{login(p){p.success({code:'fresh-login-code'})},...(platform?{requestVirtualPayment(p){cancel?p.fail({errMsg:'requestVirtualPayment:fail cancel'}):cashierError?p.fail({errMsg:'requestVirtualPayment:fail unknown'}):p.success({})}}:{})},uni:{getStorageSync:k=>storage.get(k),setStorageSync:(k,v)=>storage.set(k,v),removeStorageSync:k=>storage.delete(k),showModal:p=>modals.push(p),switchTab:p=>switches.push(p.url)},pdfRequest:async(path,method,body)=>{
  requests.push({path,method,body})
  if(path==='/quota')return {billingEnabled:true,paymentEnabled:true,paymentProvider:'virtual',wechatLoginEnabled:true,monthlyPriceFen:990,membership:{active:member}}
  if(path==='/login')return {ok:true}
  if(path==='/ai/status')return {canUse:false,testMode:false,monthlyRemainingTokenCredits:0,topupRemainingTokenCredits:0}
  if(path==='/purchase')return parameters
  if(path==='/purchase/owned-order'){if(queryFails)throw Error('network');return {paid:++polls>=confirmAfter,status:polls>=confirmAfter?'paid':'pending'}}
  throw Error(path)
 }}
 const result=vm.runInNewContext(pageSource+'\n({buy,refresh,checkPending,pendingOrder,purchasing,selectedPackage,selectPackage})',context)
 return {...result,requests,modals,switches,storage,show:()=>onShow()}
}
test('fresh login code goes to server purchase and success waits for server confirmation',async()=>{
 const p=page({confirmAfter:3});await p.refresh();p.requests.length=0;await p.buy('monthly')
 assert.equal(p.requests.find(r=>r.path==='/purchase').body.code,'fresh-login-code')
 assert.equal(p.requests.filter(r=>r.path==='/purchase/owned-order').length,3)
 assert.equal(p.requests.some(r=>r.path==='/login'&&p.requests.indexOf(r)<p.requests.findIndex(r=>r.path==='/purchase')),false)
 assert.deepEqual(p.switches,['/pages/pdf/ai']);assert.equal(p.pendingOrder.value,null)
})
test('cancellation never displays purchase success',async()=>{const p=page({cancel:true});await p.refresh();await p.buy('monthly');assert.equal(p.modals.some(m=>m.title==='购买成功'),false);assert.equal(p.switches.length,0);assert.equal(p.pendingOrder.value,null)})
test('missing platform capability prevents order creation',async()=>{const p=page({platform:false});await p.refresh();await p.buy('monthly');assert.equal(p.requests.some(r=>r.path==='/purchase'),false);assert.match(p.modals.at(-1).content,/更新微信/)})
test('uncertain payment keeps only the order reference for retry',async()=>{const p=page({queryFails:true});await p.refresh();await p.buy('ai-topup');assert.equal(p.pendingOrder.value.orderId,'owned-order');assert.equal(p.pendingOrder.value.signData,undefined);assert.equal(p.modals.some(m=>m.title==='购买成功'),false);assert.equal(p.switches.length,0)})

test('missing cashier callbacks time out without declaring payment failed',async()=>{
 await assert.rejects(requestPdfPayment(parameters,{requestVirtualPayment(){}},5),/查询订单/)
})
test('cashier errors still reconcile the existing order before reporting a result',async()=>{
 const p=page({cashierError:true});await p.refresh()
 // The page must query after any non-cancellation cashier error, including timeout.
 assert.equal(p.pendingOrder.value,null)
 await p.buy('monthly');assert.equal(p.pendingOrder.value,null)
 assert.deepEqual(p.switches,['/pages/pdf/ai'])
})

test('active membership blocks another monthly purchase but permits topup purchases',async()=>{
 const p=page({member:true});await p.refresh();assert.equal(p.selectedPackage.value,'ai-topup')
 p.selectPackage('monthly');assert.equal(p.selectedPackage.value,'ai-topup')
 p.requests.length=0;await p.buy('monthly');assert.equal(p.requests.some(r=>r.path==='/purchase'),false)
 await p.buy('ai-topup');assert.equal(p.requests.find(r=>r.path==='/purchase').body.packageId,'ai-topup')
})


test('pending payment stays on credits until server confirms; later confirmation returns to AI',async()=>{
 const p=page({confirmAfter:16});await p.refresh();await p.buy('ai-topup')
 assert.equal(p.switches.length,0);assert.ok(p.pendingOrder.value)
 await p.checkPending();assert.deepEqual(p.switches,['/pages/pdf/ai']);assert.equal(p.pendingOrder.value,null)
})


const ordersSource=(await readFile(new URL('../frontend/src/pages/pdf/orders.vue',import.meta.url),'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
function orderPage({failLogin=false,paid=false}={}){
 const requests=[],switches=[],storage=new Map([['pdf-pending-purchase-v1',{orderId:'other-order'}]])
 const context={ref:value=>({value}),onShow(){},onPullDownRefresh(){},wx:{login:p=>failLogin?p.fail({}):p.success({code:'fresh-code'})},uni:{getStorageSync:k=>storage.get(k),removeStorageSync:k=>storage.delete(k),switchTab:p=>switches.push(p.url),showToast(){}},pdfRequest:async(path,method,body)=>{
  requests.push({path,method,body})
  if(path==='/quota')return {wechatLoginEnabled:true}
  if(path==='/login')return {ok:true}
  if(path==='/purchases?page=1')return {items:[{id:'latest',status:'paid'},{id:'pending',status:'pending'}],hasMore:true}
  if(path==='/purchases?page=2')return {items:[{id:'older',status:'refunded'}],hasMore:false}
  if(path==='/purchase/pending')return {paid,status:paid?'paid':'pending'}
  throw Error(path)
 }}
 const page=vm.runInNewContext(ordersSource+'\n;({load,check,orders,error,hasMore,loading,statusLabel})',context)
 return {...page,requests,switches,storage}
}
test('order center binds identity before reading, pages newest first and does not redirect past paid orders',async()=>{
 const p=orderPage();await p.load(true)
 assert.ok(p.requests.findIndex(r=>r.path==='/login')<p.requests.findIndex(r=>r.path.startsWith('/purchases')))
 assert.deepEqual(Array.from(p.orders.value,o=>o.id),['latest','pending'])
 assert.equal(p.switches.length,0)
 await p.load(false);assert.deepEqual(Array.from(p.orders.value,o=>o.id),['latest','pending','older']);assert.equal(p.hasMore.value,false)
 await p.load(true);assert.equal(p.orders.value.length,2)
})
test('failed identity check does not read or display another identity order list',async()=>{
 const p=orderPage({failLogin:true});await p.load(true)
 assert.equal(p.requests.some(r=>r.path.startsWith('/purchases')),false);assert.match(p.error.value,/微信登录失败/);assert.equal(p.orders.value.length,0)
})
test('manual order confirmation returns to AI only when paid, preserving another pending order',async()=>{
 const pending=orderPage();await pending.load();await pending.check({id:'pending'});assert.equal(pending.switches.length,0)
 const paid=orderPage({paid:true});await paid.load();await paid.check({id:'pending'});assert.deepEqual(paid.switches,['/pages/pdf/ai']);assert.ok(paid.storage.has('pdf-pending-purchase-v1'))
 assert.equal(paid.statusLabel({status:'paid',refundedFen:100}),'部分退款')
})
