import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
const source=(await readFile(new URL('../frontend/src/lib/pdf-export-history.js',import.meta.url),'utf8')).replace(/export /g,'')
function history(){
 const store=new Map(),removed=[]
 const context={uni:{getStorageSync:key=>store.get(key),setStorageSync:(key,value)=>store.set(key,value)},wx:{env:{USER_DATA_PATH:'/data'},getFileSystemManager:()=>({unlinkSync:path=>removed.push(path)})}}
 return {...vm.runInNewContext(source+'\n({rememberExport,listExportFiles,exportExpiry})',context),store,removed}
}
test('exports persist in newest-first order with usage and retain the original expiry on repeated sharing',()=>{
 const h=history(),file={path:'/data/ai-report-1.pdf',name:'first.pdf',extension:'pdf'}
 h.rememberExport(file,{inputTokens:100,outputTokens:50,expiresAt:10000},1000)
 h.rememberExport({...file,path:'/data/ai-report-2.pdf',name:'second.pdf'},{},2000)
 const rows=h.listExportFiles(3000);assert.equal(rows[0].name,'second.pdf');assert.equal(rows[1].inputTokens+rows[1].outputTokens,150)
 const repeated=h.rememberExport(file,{expiresAt:900000},4000)
 assert.equal(repeated.expiresAt,10000);assert.equal(repeated.exportedAt,1000)
 assert.equal(h.listExportFiles(5000).length,2)
})
test('expiry removes the record and generated file but never deletes source documents',()=>{
 const h=history();h.rememberExport({path:'/data/ai-report-1.pdf',extension:'pdf'},{expiresAt:2000},1000)
 h.rememberExport({path:'/source/private.pdf',extension:'pdf'},{expiresAt:2000},1000)
 assert.equal(h.listExportFiles(2000).length,0);assert.equal(h.removed.length,1);assert.equal(h.removed[0],'/data/ai-report-1.pdf')
})
test('retention cannot exceed 30 minutes or the server expiry',()=>{
 const h=history();assert.equal(h.exportExpiry(999999999,1000),1801000);assert.equal(h.exportExpiry(5000,1000),5000)
 assert.throws(()=>h.rememberExport({path:'/data/ai-report-1.pdf'},{expiresAt:999},1000),/30/)
})
