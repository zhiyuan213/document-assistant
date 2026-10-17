import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
const source=(await readFile(new URL('../frontend/src/pages/pdf/ai.vue',import.meta.url),'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
function page({lostSubmit=false,restore=false,exporter,delivery=async()=>({})}={}){
 const storage=new Map(restore?[['pdf-translation-request-v1','original-key']]:[]),calls=[],exports=[],toasts=[];let polls=0,pick
 const context={ref:value=>({value}),computed:fn=>({get value(){return fn()}}),watch(){},onLoad(){},onShow(){},headerPadding(){},setTimeout:fn=>queueMicrotask(fn),
  wx:{chooseMessageFile(options){pick=options}},uni:{getStorageSync:key=>storage.get(key),setStorageSync:(key,value)=>storage.set(key,value),removeStorageSync:key=>storage.delete(key),chooseFile(){},showToast(v){toasts.push(v.title)},navigateTo(){},switchTab(){},setClipboardData(){}},
  pdfRequest:async path=>{calls.push(path);if(path==='/ai/status')return {canUse:true,enabled:true,dailyRemaining:50};if(path.startsWith('/ai/translations/by-request/'))return {translationJobId:'saved-job',status:'processing'};if(path.startsWith('/ai/translations/'))return ++polls===1?{status:'processing',progress:50}:{status:'completed',answer:'English result',usedTokenCredits:200,inputTokens:100,outputTokens:25,warnings:['Check OCR']};throw Error(path)},
  estimatePdfAi:async()=>({documentTranslation:true,canRun:true,remainingTokenCredits:2000000}),
  submitPdfAi:async(...args)=>{calls.push({submit:args});if(lostSubmit)throw Error('network');return {translationJobId:'saved-job'}},
  exportAiReport:async(...args)=>{exports.push(args);if(exporter)return exporter(...args);return {path:'/test/result.'+args[1],extension:args[1]}},shareSavedFile:delivery,saveFileToPhone:delivery,openSavedPdf:async()=>{}
 }
 const p=vm.runInNewContext(source+'\n({chooseFiles,selected,mode,run,resumeTranslation,exportReport,chooseExportFormat,deliverReport,exportFormat,exportFile,exporting,usageDetails,answer,busy,translationPending,translationJob,error})',context)
 p.mode.value='translate';p.selected.value=[{path:'/old.pdf',name:'old.pdf',size:20}]
 return {...p,storage,calls,exports,toasts,pick:()=>pick}
}
test('an existing PDF can be replaced without removing it first; cancelled and oversize selections preserve it',()=>{
 const p=page();p.chooseFiles();assert.equal(p.pick().count,1)
 p.pick().fail({errMsg:'cancel'});assert.equal(p.selected.value[0].path,'/old.pdf')
 p.pick().success({tempFiles:[{path:'/large.pdf',size:21*1024*1024}]});assert.equal(p.selected.value[0].path,'/old.pdf')
 p.pick().success({tempFiles:[{path:'/new.pdf',name:'new.pdf',size:30}]});assert.equal(p.selected.value.length,1);assert.equal(p.selected.value[0].path,'/new.pdf')
})
test('document translation polls the existing task and all exports reuse its id',async()=>{
 const p=page();await p.run();assert.equal(p.answer.value,'English result');assert.equal(p.busy.value,false)
 assert.equal(p.calls.filter(v=>v.submit).length,1);assert.ok(p.calls.find(v=>v.submit).submit[5])
 for(const format of ['pdf','docx','txt'])await p.exportReport(format)
 assert.equal(p.exports.length,3);for(const args of p.exports)assert.equal(args[2],'saved-job')
 assert.equal(p.calls.filter(v=>v.submit).length,1)
})
test('a lost submission response remains recoverable by request id without another model submission',async()=>{
 const p=page({lostSubmit:true});await p.run();assert.equal(p.translationPending.value,true)
 assert.ok(p.storage.get('pdf-translation-request-v1'));assert.equal(p.calls.filter(v=>v.submit).length,1)
 await p.resumeTranslation();assert.equal(p.answer.value,'English result');assert.equal(p.calls.filter(v=>v.submit).length,1)
})
test('a reopened page can recover the task before receiving its job id',async()=>{
 const p=page({restore:true});await p.resumeTranslation();assert.equal(p.answer.value,'English result');assert.equal(p.translationJob.value,'saved-job');assert.equal(p.calls.filter(v=>v.submit).length,0)
})


test('format choices prepare one existing result and unavailable layout PDF is blocked',async()=>{
 const p=page();p.answer.value='Complete translation';p.usageDetails.value={pdfAvailable:false}
 p.chooseExportFormat('pdf');assert.equal(p.exportFormat.value,'docx');assert.equal(p.exports.length,0)
 p.chooseExportFormat('docx');await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.exports[0][1],'docx');assert.equal(p.exportFile.value.extension,'docx')
})

test('format preparation is silent, deduplicated and cannot overwrite a newer selection',async()=>{
 const pending={};const p=page({exporter:(_,format)=>new Promise(resolve=>{pending[format]=resolve})});p.answer.value='result'
 p.chooseExportFormat('pdf');p.chooseExportFormat('pdf');p.chooseExportFormat('docx')
 assert.equal(p.exporting.value,false);assert.equal(p.exports.length,2)
 pending.docx({path:'/word',extension:'docx'});await new Promise(resolve=>setImmediate(resolve))
 pending.pdf({path:'/pdf',extension:'pdf'});await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.exportFile.value.extension,'docx');assert.equal(p.toasts.length,0)
 p.chooseExportFormat('pdf');await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.exportFile.value.extension,'pdf');assert.equal(p.exports.length,2);assert.equal(p.toasts.length,0)
 p.answer.value='new result';const next=p.exportReport('pdf');assert.equal(p.exports.length,3);pending.pdf({path:'/new',extension:'pdf'});await next;assert.equal(p.exportFile.value.path,'/new')
})
test('only completed native delivery reports success; unsupported or cancelled actions do not',async()=>{
 for(const kind of ['success','unsupported','cancelled']){
  const p=page({delivery:async()=>{if(kind==='cancelled')throw {cancelled:true};return {unsupported:kind==='unsupported'}}});p.answer.value='result'
  await p.exportReport('docx');assert.equal(p.toasts.length,0)
  await p.deliverReport('download');assert.equal(p.toasts.length,kind==='success'?1:0);assert.equal(p.exporting.value,false)
 }
})
