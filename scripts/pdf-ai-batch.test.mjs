import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
const source=await readFile(new URL('../frontend/src/lib/pdf-ai-batch.js',import.meta.url),'utf8')
const {runAiBatch}=await import('data:text/javascript,'+encodeURIComponent(source))
const files=['a','b','c'].map(name=>({name:name+'.pdf',path:'/'+name}))
const balance={enabled:true,canUse:true,remainingTokenCredits:100,dailyRemaining:100}
test('an exhausted completed result stops before another request and retains all completed outputs',async()=>{
 let reads=0,submitted=0;const stored=[]
 const batch=await runAiBatch({files,readStatus:async()=>{reads++;return balance},process:async()=>{submitted++;return{answer:'paid output',remainingTokenCredits:0}},onResult:(result,file)=>stored.push({result,file})})
 assert.equal(submitted,1);assert.equal(reads,1);assert.equal(batch.completed.length,1);assert.equal(stored[0].file.path,'/a')
 assert.deepEqual(batch.remaining.map(file=>file.path),['/b','/c']);assert.match(batch.stopReason,/额度/)
})
test('each file gets a fresh balance and concurrent use of credits stops the next submission',async()=>{
 let reads=0,submitted=0
 const batch=await runAiBatch({files,readStatus:async()=>++reads===1?balance:{...balance,canUse:false,remainingTokenCredits:0},process:async()=>{submitted++;return{answer:'done'}}})
 assert.equal(reads,2);assert.equal(submitted,1);assert.equal(batch.remaining.length,2)
})
test('authoritative HTTP 402 stops even if the preceding status showed credits',async()=>{
 let submitted=0
 const batch=await runAiBatch({files,readStatus:async()=>balance,process:async()=>{if(++submitted===2)throw{status:402,message:'insufficient'};return{answer:'done'}}})
 assert.equal(submitted,2);assert.equal(batch.completed.length,1);assert.equal(batch.failed.length,0);assert.equal(batch.remaining[0].path,'/b')
})
test('uncertain processing and balance lookup failures pause the batch without resubmitting',async()=>{
 let submitted=0
 const uncertain=await runAiBatch({files,readStatus:async()=>balance,process:async()=>{submitted++;throw{uncertain:true,message:'pending task'}}})
 assert.equal(submitted,1);assert.equal(uncertain.remaining.length,3)
 const unavailable=await runAiBatch({files,readStatus:async()=>{throw Error('network')},process:async()=>{assert.fail('must not submit without balance')}})
 assert.equal(unavailable.completed.length,0);assert.equal(unavailable.remaining.length,3)
})
test('invalid documents are isolated, while daily limits and test mode still respect request limits',async()=>{
 const batch=await runAiBatch({files,readStatus:async()=>balance,process:async file=>{if(file.path==='/a')throw{status:400,message:'invalid PDF'};return{answer:file.name}}})
 assert.equal(batch.failed.length,1);assert.equal(batch.completed.length,2)
 let submitted=0
 const testMode=await runAiBatch({files,readStatus:async()=>({...balance,testMode:true,canUse:false,remainingTokenCredits:0}),process:async()=>{submitted++;return{answer:'test',remainingTokenCredits:0,dailyRemaining:0}}})
 assert.equal(submitted,1);assert.equal(testMode.remaining.length,2);assert.match(testMode.stopReason,/次数/)
})
