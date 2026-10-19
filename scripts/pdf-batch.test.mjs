import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile,writeFile} from 'node:fs/promises'
import {tmpdir} from 'node:os'
import {join} from 'node:path'
const load=async file=>import('data:text/javascript,'+encodeURIComponent(await readFile(new URL('../frontend/src/lib/'+file,import.meta.url),'utf8')))
const {runPdfBatch,downloadBatch,appendSelection,selectionLimit}=await load('pdf-batch.js')
const {createFileBundle,crc32,archiveNames,bundleKey,discardBundle,retainBundle}=await load('pdf-file-bundle.js')
const {resolveExportSelection}=await load('pdf-export-history.js')
const {pdfTools}=await load('pdf-tools.js')
const inputs=[{path:'/a',name:'合同.pdf',size:3},{path:'/b',name:'合同.pdf',size:4}]
test('all independent free tools submit every file separately with shared settings',async()=>{
 for(const tool of pdfTools.filter(tool=>!['images','merge','compare','remove-watermark'].includes(tool.id))){
  const calls=[]
  const result=await runPdfBatch({tool:tool.id,title:tool.title,files:inputs,options:{angle:90,watermarkMode:'image'},imageFile:{path:'/mark'},
   submit:async(id,files,options)=>{calls.push({id,files,options});return{id:files[0].path}},poll:async id=>({id,extension:'pdf'})})
  assert.equal(result.items.length,2,tool.id);assert.equal(calls.length,2,tool.id)
  assert.equal(calls[0].files[0].path,'/a');assert.equal(calls[1].files[0].path,'/b');assert.equal(calls[1].options.angle,90)
  assert.equal(calls[0].files.length,['sign','watermark'].includes(tool.id)?2:1,tool.id)
 }
})
test('merge and compare keep their semantics, while separate image mode produces individual files',async()=>{
 for(const tool of ['merge','compare','images']){
  let calls=0
  const result=await runPdfBatch({tool,title:tool,files:inputs,options:{},submit:async(id,files)=>{calls++;assert.equal(files.length,2);return{id:'group'}},poll:async id=>({id,extension:'pdf'})})
  assert.equal(calls,1);assert.equal(result.items.length,1)
 }
 let calls=0
 const result=await runPdfBatch({tool:'images',title:'图片转PDF',files:inputs,options:{imageOutput:'separate'},submit:async(id,files,options)=>{calls++;assert.equal(files.length,1);assert.equal(options.imageOutput,'merged');return{id:calls}},poll:async id=>({id,extension:'pdf'})})
 assert.equal(result.items.length,2)
})
test('one failed file does not discard successful results and leaving the page stops new submissions',async()=>{
 let active=true,calls=0
 const result=await runPdfBatch({tool:'pdf-word',title:'PDF转Word',files:[...inputs,{path:'/c',name:'third.pdf'}],options:{},
  submit:async(id,files)=>{calls++;if(files[0].path==='/a')throw Error('no text');return{id:files[0].path}},poll:async id=>{active=false;return{id,extension:'docx'}},active:()=>active})
 assert.equal(calls,2);assert.equal(result.failures[0].name,'合同.pdf');assert.equal(result.items.length,0)
 const normal=await runPdfBatch({tool:'compress',title:'压缩',files:inputs,options:{},submit:async(id,files)=>{if(files[0].path==='/a')throw Error('bad');return{id:'ok'}},poll:async id=>({id,extension:'pdf'})})
 assert.equal(normal.items.length,1);assert.equal(normal.failures.length,1)
})
test('watermark removal uses each file own reviewed candidate IDs and proof',async()=>{
 const plans={'/a':{ids:['p0b1'],proof:'proof-a'},'/b':{ids:['p0b2'],proof:'proof-b'}},seen=[]
 const result=await runPdfBatch({tool:'remove-watermark',title:'去水印',files:inputs,options:{},watermarkPlans:plans,
  submit:async(id,files,options)=>{seen.push(options);return{id:files[0].path}},poll:async id=>({id,extension:'pdf'})})
 assert.equal(result.items.length,2);assert.deepEqual(seen.map(value=>value.previewProof),['proof-a','proof-b']);assert.deepEqual(seen.map(value=>value.candidateIds),['p0b1','p0b2'])
 delete plans['/b'].proof
 const guarded=await runPdfBatch({tool:'remove-watermark',title:'去水印',files:inputs,options:{},watermarkPlans:plans,submit:async()=>({id:'a'}),poll:async()=>({id:'a',extension:'pdf'})})
 assert.equal(guarded.items.length,1);assert.match(guarded.failures[0].message,/预览/)
})
test('batch selection enforces per-file, total size, count and duplicate constraints',()=>{
 assert.equal(appendSelection(inputs,[inputs[0]],'pdf-word').length,2)
 assert.throws(()=>appendSelection([],Array.from({length:21},(_,i)=>({path:String(i)})),'pdf-word'),/20/)
 assert.throws(()=>appendSelection([],[{path:'/a',size:21*1024*1024}],'compress'),/20MB/)
 assert.throws(()=>appendSelection([],Array.from({length:4},(_,i)=>({path:String(i),size:20*1024*1024})),'compress'),/60MB/)
 assert.equal(selectionLimit('merge'),10);assert.equal(selectionLimit('compare'),2)
})
test('download batch records names and original expiry and keeps download failures separate',async()=>{
 const expiry=new Date(Date.now()+30000).toISOString(),items=[{job:{id:'a',extension:'docx',expiresAt:expiry},name:'合同.docx'},{job:{id:'b',extension:'pdf'},name:'second.pdf'}]
 const result=await downloadBatch(items,{save:async job=>{if(job.id==='b')throw Error('network');assert.equal(job.name,'合同.docx');return{path:'/a',extension:job.extension}},remember:(file,details)=>({...file,...details})})
 assert.equal(result.saved.length,1);assert.equal(result.saved[0].expiresAt,expiry);assert.equal(result.saved[0].source,'tool');assert.equal(result.saved[0].jobId,'a');assert.equal(result.failures[0].name,'second.pdf')
})
test('download button saves every result then navigates to file list without opening documents',async()=>{
 const original=globalThis.uni
 let destination,paths,queued=false
 globalThis.uni={setStorageSync(key,value){assert.equal(key,'pdf-export-selected');paths=value},async switchTab(o){destination=o.url},showToast(){}}
 let source=await readFile(new URL('../frontend/src/pages/pdf/work.vue',import.meta.url),'utf8')
 source=source.slice(source.indexOf('async function save(){'),source.indexOf('function again(){')).replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'')
 const state={saving:{value:false},error:{value:''},result:{value:{items:[{job:{id:'a',extension:'pdf'},name:'a.pdf'},{job:{id:'b',extension:'pdf'},name:'b.pdf'}]}},downloadStage:{value:''}}
 const bindings={...state,alive:true,downloadBatch,savePdf:async job=>({path:'/'+job.id}),rememberExport:(file,details)=>({...file,...details}),queueDownloads(){queued=true},completeDownloads(ids){assert.deepEqual(ids,['a','b'])}}
 const save=new Function(...Object.keys(bindings),source+';return save')(...Object.values(bindings))
 try{await save();assert.equal(queued,true);assert.equal(destination,'/pages/pdf/exports');assert.deepEqual(paths,['/a','/b']);assert.equal(state.saving.value,false)}finally{globalThis.uni=original}
})
function memoryPlatform(records){
 const files=new Map(Object.entries(records).map(([path,data])=>[path,Buffer.from(data)]))
 return {files,env:{USER_DATA_PATH:'/data'},getFileSystemManager(){return{
  writeFile(o){files.set(o.filePath,Buffer.from(o.data));o.success({})},
  appendFile(o){files.set(o.filePath,Buffer.concat([files.get(o.filePath),Buffer.from(o.data)]));o.success({})},
  readFile(o){if(!files.has(o.filePath)){o.fail({});return}o.success({data:Uint8Array.from(files.get(o.filePath)).buffer})},
  stat(o){if(!files.has(o.path)){o.fail({});return}o.success({stats:{size:files.get(o.path).length}})},
  unlinkSync(path){files.delete(path)}
 }}}
}
test('ZIP contains unchanged originals, safe unique UTF-8 names and a valid central directory',async()=>{
 assert.equal(crc32(Buffer.from('123456789')),0xcbf43926)
 const platform=memoryPlatform({'/a':'synthetic PDF','/b':'synthetic Word'})
 const files=[{path:'/a',name:'合同.pdf',expiresAt:Date.now()+60000},{path:'/b',name:'合同.pdf',expiresAt:Date.now()+120000}]
 const bundle=await createFileBundle(files,platform),zip=platform.files.get(bundle.path)
 assert.equal(bundle.expiresAt,files[0].expiresAt)
 let offset=0;const names=[],content=[],offsets=[]
 while(zip.readUInt32LE(offset)===0x04034b50){
  offsets.push(offset);assert.equal(zip.readUInt16LE(offset+6),0x800);assert.equal(zip.readUInt16LE(offset+8),0)
  const size=zip.readUInt32LE(offset+18),nameLength=zip.readUInt16LE(offset+26),start=offset+30+nameLength
  names.push(zip.subarray(offset+30,start).toString('utf8'));content.push(zip.subarray(start,start+size).toString());offset=start+size
 }
 assert.deepEqual(names,['合同.pdf','合同(1).pdf']);assert.deepEqual(content,['synthetic PDF','synthetic Word'])
 const directory=offset
 for(let i=0;i<2;i++){assert.equal(zip.readUInt32LE(offset),0x02014b50);assert.equal(zip.readUInt32LE(offset+42),offsets[i]);offset+=46+zip.readUInt16LE(offset+28)}
 assert.equal(zip.readUInt32LE(offset),0x06054b50);assert.equal(zip.readUInt16LE(offset+10),2);assert.equal(zip.readUInt32LE(offset+16),directory)
 assert.ok(archiveNames([{name:'../secret.txt'},{name:'../secret.txt'}]).every(name=>!name.includes('/')))
 // Synthetic fixture for an independent standard ZIP-reader smoke check.
 await writeFile(join(tmpdir(),'dengke-batch-synthetic.zip'),zip)
})
test('file-list sharing preserves the click gesture for single files and prepared ZIPs',async()=>{
 const oldUni=globalThis.uni;globalThis.uni={showToast(){}}
 let source=await readFile(new URL('../frontend/src/pages/pdf/exports.vue',import.meta.url),'utf8')
 source=source.slice(source.indexOf('async function share(){'),source.indexOf('</script>')).replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'')
 const files=[{path:'/a',name:'a.pdf',expiresAt:Date.now()+60000},{path:'/b',name:'b.pdf',expiresAt:Date.now()+60000}]
 const state={sharing:{value:false},preparing:{value:false},retrying:{value:false},error:{value:''},bundle:{value:null},selected:{value:['/a']},files:{value:files}}
 let shared=0,packed=0
 const bindings={...state,identity:'',visible:true,alive:true,bundleKey,retainBundle,resolveExportSelection,listExportFiles:()=>files,refresh(){},discardBundle(){},clearBundle(){state.bundle.value=null},
  async createFileBundle(){packed++;return{path:'/bundle'+packed,extension:'zip',expiresAt:Date.now()+60000}},shareSavedFile(file){shared++;assert.ok(['/a','/bundle1'].includes(file.path));return Promise.resolve()}}
 const share=new Function(...Object.keys(bindings),source+';return share')(...Object.values(bindings))
 try{
  let pending=share();assert.equal(shared,1);await pending
  state.selected.value=['/a','/b'];await share();assert.equal(shared,1);assert.equal(packed,1)
  pending=share();assert.equal(shared,2);await pending;assert.equal(packed,1)
  files[0].expiresAt=Date.now()-1;await share();assert.equal(shared,2);assert.match(state.error.value,/过期/)
 }finally{globalThis.uni=oldUni}
})
test('bundle refuses expired or missing files and cleans partially written archives',async()=>{
 const platform=memoryPlatform({'/a':'synthetic'})
 await assert.rejects(createFileBundle([{path:'/a',expiresAt:Date.now()-1},{path:'/a',expiresAt:Date.now()+10000}],platform),/过期/)
 await assert.rejects(createFileBundle([{path:'/a',expiresAt:Date.now()+10000},{path:'/missing',expiresAt:Date.now()+10000}],platform),/打包失败/)
 assert.deepEqual([...platform.files.keys()],['/a'])
})
test('incomplete ZIP writes are rejected and never handed to the share flow',async()=>{
 const platform=memoryPlatform({'/a':'A','/b':'B'}),fs=platform.getFileSystemManager(),stat=fs.stat
 fs.stat=o=>stat({...o,success:r=>o.success({stats:{size:r.stats.size-(o.path.includes('/dengke-bundle-')?1:0)}})})
 platform.getFileSystemManager=()=>fs
 await assert.rejects(createFileBundle([{path:'/a',expiresAt:Date.now()+60000},{path:'/b',expiresAt:Date.now()+60000}],platform),/写入不完整/)
 assert.deepEqual([...platform.files.keys()],['/a','/b'])
})
test('ZIP remains readable after callback while hidden, after selection cleanup and page unload',async()=>{
 const oldUni=globalThis.uni;let toast
 globalThis.uni={showToast(o){toast=o.title}}
 const platform=memoryPlatform({'/a':'synthetic A','/b':'synthetic B'})
 const files=[{path:'/a',name:'a.pdf',expiresAt:Date.now()+60000},{path:'/b',name:'b.pdf',expiresAt:Date.now()+60000}]
 const packed=await createFileBundle(files,platform)
 const expected=Buffer.from(platform.files.get(packed.path)),state={sharing:{value:false},preparing:{value:false},retrying:{value:false},error:{value:''},bundle:{value:packed},selected:{value:['/a','/b']},files:{value:files}}
 let resolveNative,handedPath
 let source=await readFile(new URL('../frontend/src/pages/pdf/exports.vue',import.meta.url),'utf8')
 source=source.slice(source.indexOf('async function share(){'),source.indexOf('</script>')).replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'')
 const clearBundle=()=>{discardBundle(state.bundle.value,platform);state.bundle.value=null}
 const bindings={...state,identity:bundleKey(files),visible:false,alive:true,bundleKey,retainBundle,resolveExportSelection,listExportFiles:()=>files,refresh(){},discardBundle,clearBundle,
  createFileBundle(){assert.fail('prepared ZIP must be reused')},shareSavedFile(file){handedPath=file.path;return new Promise(resolve=>{resolveNative=resolve})}}
 const share=new Function(...Object.keys(bindings),source+';return share')(...Object.values(bindings))
 try{
  const pending=share();assert.equal(handedPath,packed.path)
  resolveNative();await pending
  assert.equal(state.bundle.value,null);assert.deepEqual(platform.files.get(handedPath),expected)
  // WeChat can read again after its callback; later cleanup must still not unlink this archive.
  discardBundle(packed,platform);assert.deepEqual(platform.files.get(handedPath),expected)
  assert.equal(toast,'已交给微信发送')
  const unused=await createFileBundle(files,platform);discardBundle(unused,platform);assert.equal(platform.files.has(unused.path),false)
 }finally{globalThis.uni=oldUni}
})
