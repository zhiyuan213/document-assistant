import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
const source=await readFile(new URL('../frontend/src/lib/pdf-progress.js',import.meta.url),'utf8')
const {createSmoothProgress}=await import('data:text/javascript,'+encodeURIComponent(source))
function animation(){
 let id=0;const callbacks=new Map(),values=[]
 const controller=createSmoothProgress({onChange:value=>values.push(value),schedule:fn=>{callbacks.set(++id,fn);return id},cancel:key=>callbacks.delete(key)})
 function tick(){const entry=callbacks.entries().next().value;if(!entry)return false;callbacks.delete(entry[0]);entry[1]();return true}
 return {controller,values,tick,callbacks}
}
test('stage updates animate every integer rather than jumping to the reported percentage',()=>{
 const a=animation();a.controller.start();a.controller.update(60,90)
 for(let i=0;i<59;i++)a.tick()
 assert.deepEqual(a.values,Array.from({length:60},(_,i)=>i+1))
 a.controller.update(30,50);a.tick();assert.equal(a.values.at(-1),60)
 a.controller.stop();assert.equal(a.callbacks.size,0)
})
test('estimated progress moves while waiting but respects the current stage ceiling and never finishes early',async()=>{
 const a=animation();a.controller.start();a.controller.update(0,10)
 for(let i=0;i<100;i++)a.tick()
 assert.equal(a.values.at(-1),10)
 a.controller.update(100,100)
 for(let i=0;i<150;i++)a.tick()
 assert.equal(a.values.at(-1),99)
 const finish=a.controller.finish();a.tick();await finish
 assert.equal(a.values.at(-1),100);assert.equal(a.callbacks.size,0)
})
test('pausing and resuming does not jump and disposal clears timers and resolves pending completion',async()=>{
 const a=animation();a.controller.start();a.controller.update(50,90);a.tick()
 a.controller.pause();assert.equal(a.callbacks.size,0)
 const value=a.values.at(-1);a.controller.resume();a.tick();assert.equal(a.values.at(-1),value+1)
 const finish=a.controller.finish();a.controller.stop();await finish;assert.equal(a.callbacks.size,0)
 a.controller.start();assert.equal(a.values.at(-1),1);a.controller.pause();await a.controller.finish();assert.equal(a.values.at(-1),100)
})
