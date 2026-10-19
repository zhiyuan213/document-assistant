import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
const helperSource=await readFile(new URL('../frontend/src/lib/pdf-ai-batch.js',import.meta.url),'utf8')
const {runAiBatch}=await import('data:text/javascript,'+encodeURIComponent(helperSource))
const selectionSource=await readFile(new URL('../frontend/src/lib/pdf-batch.js',import.meta.url),'utf8')
const {appendSelection}=await import('data:text/javascript,'+encodeURIComponent(selectionSource))
const source=(await readFile(new URL('../frontend/src/pages/pdf/ai.vue',import.meta.url),'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
function page({initialMode='translate',lostSubmit=false,restore=false,restoredStatus,taskError,statusLoader,exporter,submitter,delivery=async()=>({})}={}){
 const storage=new Map(restore?[['pdf-translation-request-v1','original-key']]:[]),calls=[],exports=[],toasts=[],switches=[];let polls=0,pick
 const context={runAiBatch,appendSelection,rememberExport:(file,details)=>file?{...file,...details}:file,exportExpiry:()=>Date.now()+1800000,ref:value=>({value}),computed:fn=>({get value(){return fn()}}),watch(){},onLoad(){},onShow(){},onUnload(){},onTabItemTap(){},headerPadding(){},setTimeout:fn=>queueMicrotask(fn),
  wx:{chooseMessageFile(options){pick=options}},uni:{getStorageSync:key=>storage.get(key),setStorageSync:(key,value)=>storage.set(key,value),removeStorageSync:key=>storage.delete(key),chooseFile(){},showToast(v){toasts.push(v.title)},navigateTo(){},switchTab(o){switches.push(o.url)},setClipboardData(){}},
  pdfRequest:async path=>{calls.push(path);if(path==='/ai/status'&&statusLoader)return statusLoader();if(path==='/ai/status')return {canUse:true,enabled:true,dailyRemaining:100};if(path.startsWith('/ai/translations/')&&taskError)throw taskError;if(path.startsWith('/ai/translations/by-request/'))return {translationJobId:'saved-job',status:'processing'};if(path.startsWith('/ai/translations/')&&restoredStatus)return {status:restoredStatus,answer:restoredStatus==='completed'?'English result':undefined};if(path.startsWith('/ai/translations/'))return ++polls===1?{status:'processing',progress:50}:{status:'completed',answer:'English result',usedTokenCredits:200,inputTokens:100,outputTokens:25,warnings:['Check OCR']};throw Error(path)},
  estimatePdfAi:async()=>({documentTranslation:true,canRun:true,remainingTokenCredits:2000000}),
  submitPdfAi:async(...args)=>{calls.push({submit:args});if(submitter)return submitter(...args);if(lostSubmit)throw Error('network');return {translationJobId:'saved-job'}},
  exportAiReport:async(...args)=>{exports.push(args);if(exporter)return exporter(...args);return {path:'/test/result-'+exports.length+'.'+args[1],extension:args[1]}},shareSavedFile:delivery,saveFileToPhone:delivery,openSavedPdf:async()=>{}
 }
 const p=vm.runInNewContext(source+'\n({resetWorkspace,activeStep,toggleStep,selectMode,removeFile,chooseFiles,selected,mode,run,resumeTranslation,exportReport,chooseExportFormat,deliverReport,exportFormats,exportFormat,exportFile,exporting,usageDetails,answer,busy,translationPending,translationJob,error,refreshStatus,status,statusError,batchResults,batchFailures,remainingFiles,stopReason,continueBatch})',context)
 p.mode.value=initialMode;p.selected.value=[{path:'/old.pdf',name:'old.pdf',size:20}]
 return {...p,storage,calls,exports,toasts,switches,pick:()=>pick}
}
test('AI selection appends multiple PDFs; cancelled and oversize selections preserve the existing files',()=>{
 const p=page();p.chooseFiles();assert.equal(p.pick().count,19)
 p.pick().fail({errMsg:'cancel'});assert.equal(p.selected.value[0].path,'/old.pdf')
 p.pick().success({tempFiles:[{path:'/large.pdf',size:21*1024*1024}]});assert.equal(p.selected.value[0].path,'/old.pdf')
 p.pick().success({tempFiles:[{path:'/new.pdf',name:'new.pdf',size:30}]});assert.equal(p.selected.value.length,2);assert.equal(p.selected.value[1].path,'/new.pdf')
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


test('PDF remains selectable for an older result whose original-layout PDF was unavailable',async()=>{
 const p=page();p.answer.value='Complete translation';p.usageDetails.value={pdfAvailable:false};p.exportFormat.value='docx'
 p.chooseExportFormat('pdf');await new Promise(resolve=>setImmediate(resolve))
 assert.equal(p.exportFormat.value,'pdf');assert.equal(p.exports[0][1],'pdf');assert.equal(p.exportFile.value.extension,'pdf')
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
test('AI download navigates to the file list on the first click without native sharing or another model call',async()=>{
 const p=page({delivery:async()=>{assert.fail('AI result page must not open the native chooser')}});p.answer.value='result'
 await p.deliverReport();assert.deepEqual(p.switches,['/pages/pdf/exports']);assert.equal(p.exports.length,1);assert.equal(p.exporting.value,false)
 assert.equal(p.storage.get('pdf-export-selected').length,1);assert.equal(p.calls.filter(value=>value.submit).length,0)
})

test('completion and failed submission both reload the current balance',async()=>{
 for(const lostSubmit of [false,true]){
  const p=page({lostSubmit,statusLoader:async()=>({enabled:true,canUse:true,dailyRemaining:36,monthlyRemainingTokenCredits:123})})
  await p.run();assert.equal(p.status.value.monthlyRemainingTokenCredits,123);assert.equal(p.status.value.dailyRemaining,36)
  assert.equal(p.calls.at(-1),'/ai/status')
 }
})
test('an older status response cannot replace a newer balance; refresh failures retain it',async()=>{
 const pending=[];const p=page({statusLoader:()=>new Promise((resolve,reject)=>pending.push({resolve,reject}))})
 const old=p.refreshStatus(),latest=p.refreshStatus()
 pending[1].resolve({monthlyRemainingTokenCredits:100});await latest
 pending[0].resolve({monthlyRemainingTokenCredits:200});await old
 assert.equal(p.status.value.monthlyRemainingTokenCredits,100)
 const failed=p.refreshStatus();pending[2].reject(Error('network'));await failed
 assert.equal(p.status.value.monthlyRemainingTokenCredits,100);assert.ok(p.statusError.value)
})

test('expired saved translation is cleared silently and is not retried on the next visit',async()=>{
 const p=page({taskError:{status:404,message:'翻译任务已过期或不存在，请重新处理'}})
 p.storage.set('pdf-translation-job-v1','old-job');p.storage.set('pdf-translation-request-v1','old-request')
 await p.resumeTranslation()
 assert.equal(p.error.value,'');assert.equal(p.translationPending.value,false);assert.equal(p.translationJob.value,'')
 assert.equal(p.storage.size,0);const count=p.calls.length;await p.resumeTranslation();assert.equal(p.calls.length,count)
})
test('a missing deployment route still reports a configuration error and preserves the request key',async()=>{
 const p=page({taskError:{status:404,message:'请求路径不存在'}})
 p.storage.set('pdf-translation-job-v1','old-job');p.storage.set('pdf-translation-request-v1','request')
 await p.resumeTranslation();assert.match(p.error.value,/部署/);assert.equal(p.storage.get('pdf-translation-request-v1'),'request')
})

test('accordion advances through translation steps and lets users reopen earlier selections',async()=>{
 const p=page();assert.equal(p.activeStep.value,'mode')
 p.selectMode('translate');assert.equal(p.activeStep.value,'files')
 p.chooseFiles();p.pick().fail({errMsg:'cancel'});assert.equal(p.activeStep.value,'files')
 p.pick().success({tempFiles:[{path:'/new.pdf',name:'new.pdf',size:30}]});assert.equal(p.activeStep.value,'language')
 p.toggleStep('files');assert.equal(p.selected.value[1].name,'new.pdf');assert.equal(p.activeStep.value,'files')
 p.toggleStep('files');assert.equal(p.activeStep.value,'')
 await p.run();assert.equal(p.activeStep.value,'result');assert.equal(p.answer.value,'English result')
 p.toggleStep('result');assert.equal(p.activeStep.value,'');assert.equal(p.answer.value,'English result')
 p.removeFile(0);assert.equal(p.activeStep.value,'files');assert.equal(p.answer.value,'')
})
test('summary file selection closes the upload step without opening a language step',()=>{
 const p=page();p.selectMode('summary');p.chooseFiles();p.pick().success({tempFiles:[{path:'/new.pdf',name:'new.pdf',size:30}]})
 assert.equal(p.activeStep.value,'');assert.equal(p.selected.value.length,2)
})

test('AI function is initially unselected and later steps cannot bypass the choice',()=>{
 const p=page({initialMode:''});assert.equal(p.mode.value,'');assert.equal(p.activeStep.value,'mode')
 p.toggleStep('files');assert.equal(p.activeStep.value,'mode')
 p.chooseFiles();assert.equal(p.pick(),undefined)
 p.selectMode('translate');assert.equal(p.mode.value,'translate');assert.equal(p.activeStep.value,'files')
})

test('entering AI again starts at open step 01 without a function or previous result selected',async()=>{
 const p=page();await p.run();assert.equal(p.storage.size,0)
 p.resetWorkspace();assert.equal(p.activeStep.value,'mode');assert.equal(p.mode.value,'');assert.equal(p.answer.value,'');assert.equal(p.selected.value.length,0)
})
test('automatic restoration preserves a paid completed translation for download',async()=>{
 const p=page({initialMode:'',restore:true,restoredStatus:'completed'});p.selected.value=[]
 await p.resumeTranslation(true)
 assert.equal(p.mode.value,'translate');assert.equal(p.activeStep.value,'result');assert.equal(p.answer.value,'English result');assert.equal(p.translationPending.value,false);assert.equal(p.storage.size,0)
})
test('switching tabs does not reset a running or uncertain translation',async()=>{
 const p=page({lostSubmit:true});await p.run();assert.equal(p.translationPending.value,true)
 const key=p.storage.get('pdf-translation-request-v1');p.resetWorkspace()
 assert.equal(p.mode.value,'translate');assert.equal(p.storage.get('pdf-translation-request-v1'),key)
})


test('mindmap uses the existing workflow and exports PNG PDF XMind without submitting again',async()=>{
 const p=page({initialMode:'mindmap'})
 await p.run()
 assert.equal(p.calls.filter(v=>v.submit).length,1)
 assert.equal(p.calls.find(v=>v.submit).submit[0],'mindmap')
 assert.deepEqual(Array.from(p.exportFormats.value,format=>format.id),['png','pdf','xmind'])
 assert.equal(p.exportFormat.value,'pdf')
 for(const format of ['png','pdf','xmind']){
  const file=await p.exportReport(format)
  assert.equal(file.extension,format)
  assert.ok(file.name.endsWith('_脑图.'+format))
 }
 assert.equal(p.exports.length,3)
 for(const args of p.exports){assert.equal(args[2],'');assert.equal(args[3],'mindmap')}
 assert.equal(p.calls.filter(v=>v.submit).length,1)
 assert.ok(p.calls.includes('/ai/status'))
})


test('PPT uses editable PPTX as its default and reuses generation for repeat exports',async()=>{
 const p=page({initialMode:'ppt'})
 await p.run()
 assert.equal(p.calls.find(v=>v.submit).submit[0],'ppt')
 assert.equal(p.exportFormat.value,'pptx')
 assert.deepEqual(Array.from(p.exportFormats.value,f=>f.id),['pptx'])
 const first=await p.exportReport('pptx'),second=await p.exportReport('pptx')
 assert.equal(first,second)
 assert.ok(first.name.endsWith('_演示.pptx'))
 assert.equal(p.exports.length,1)
 assert.equal(p.exports[0][2],'')
 assert.equal(p.exports[0][3],'ppt')
 assert.equal(p.calls.filter(v=>v.submit).length,1)
 assert.ok(p.calls.includes('/ai/status'))
})
test('AI batch stops at exhausted credits, shows completed results and downloads them without opening recharge',async()=>{
 let consumed=false
 const p=page({initialMode:'summary',statusLoader:async()=>({enabled:true,canUse:!consumed,remainingTokenCredits:consumed?0:100,dailyRemaining:100}),submitter:async()=>{consumed=true;return{answer:'first summary',usedTokenCredits:100,remainingTokenCredits:0}}})
 p.selected.value=[{path:'/a',name:'first.pdf',size:20},{path:'/b',name:'second.pdf',size:20},{path:'/c',name:'third.pdf',size:20}]
 await p.run();assert.equal(p.batchResults.value.length,1);assert.equal(p.remainingFiles.value.length,2);assert.match(p.stopReason.value,/额度/);assert.equal(p.activeStep.value,'result')
 assert.equal(p.calls.filter(value=>value.submit).length,1);assert.equal(p.switches.length,0)
 await p.deliverReport();assert.deepEqual(p.switches,['/pages/pdf/exports']);assert.equal(p.exports.length,1);assert.equal(p.calls.filter(value=>value.submit).length,1)
 consumed=false;await p.refreshStatus();await p.continueBatch()
 assert.deepEqual(p.calls.filter(value=>value.submit).map(value=>value.submit[1][0].path),['/a','/b']);assert.equal(p.batchResults.value.length,2);assert.equal(p.remainingFiles.value.length,1)
})
test('multi-file AI exports use each document result and token usage with no new model requests',async()=>{
 const p=page({initialMode:'mindmap',submitter:async(mode,files)=>({answer:'result '+files[0].name,inputTokens:10,outputTokens:5,remainingTokenCredits:100})})
 p.selected.value=[{path:'/a',name:'first.pdf',size:20},{path:'/b',name:'second.pdf',size:20}]
 await p.run();assert.equal(p.batchResults.value.length,2)
 assert.ok(p.calls.filter(value=>value.submit).every(value=>value.submit[1].length===1))
 await p.deliverReport();assert.deepEqual(p.exports.map(args=>args[0]),['result first.pdf','result second.pdf'])
 assert.equal(p.storage.get('pdf-export-selected').length,2);assert.equal(p.calls.filter(value=>value.submit).length,2)
})
test('an uncertain second translation stops later files and recovers the original task and source name',async()=>{
 let submitted=0
 const p=page({submitter:async()=>{if(++submitted===2)throw Error('lost response');return{translationJobId:'job-a'}}})
 p.selected.value=[{path:'/a',name:'first.pdf',size:20},{path:'/b',name:'second.pdf',size:20},{path:'/c',name:'third.pdf',size:20}]
 await p.run();assert.equal(submitted,2);assert.equal(p.batchResults.value.length,1);assert.equal(p.translationPending.value,true);assert.equal(p.remainingFiles.value.length,2)
 const context=p.storage.get('pdf-translation-context-v1');assert.equal(context.file.name,'second.pdf');assert.equal(context.completed[0].translationJobId,'job-a');assert.equal(context.completed[0].answer,'')
 await p.resumeTranslation();assert.equal(submitted,2);assert.equal(p.batchResults.value.length,2);assert.equal(p.batchResults.value[1].file.name,'second.pdf');assert.equal(p.remainingFiles.value.length,1)
 await p.deliverReport();assert.deepEqual(p.exports.map(args=>args[2]),['job-a','saved-job'])
})
test('every translated file gets a distinct request key and HTTP 402 leaves no uncertain task marker',async()=>{
 let id=0
 const p=page({submitter:async()=>({translationJobId:'job-'+(++id)})});p.selected.value=[{path:'/a',name:'a.pdf'},{path:'/b',name:'b.pdf'}]
 await p.run();const requests=p.calls.filter(value=>value.submit).map(value=>value.submit[5]);assert.equal(new Set(requests).size,2)
 const denied=page({submitter:async()=>{throw{status:402,message:'insufficient'}}});await denied.run();assert.equal(denied.translationPending.value,false);assert.equal(denied.storage.size,0);assert.equal(denied.remainingFiles.value.length,1)
})
test('translation recovery restores earlier completed task IDs without storing document text in recovery context',async()=>{
 const p=page({restore:true});p.selected.value=[]
 p.storage.set('pdf-translation-context-v1',{file:{path:'/b',name:'second.pdf'},completed:[{id:'first-job',mode:'translate',translationJobId:'first-job',answer:'',file:{path:'/a',name:'first.pdf'},expiresAt:Date.now()+60000,usage:{inputTokens:20,outputTokens:10}}]})
 await p.resumeTranslation();assert.equal(p.batchResults.value.length,2)
 await p.deliverReport();assert.deepEqual(p.exports.map(args=>args[2]),['first-job','saved-job']);assert.equal(p.calls.filter(value=>value.submit).length,0)
})
