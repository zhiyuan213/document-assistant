import {test} from 'node:test'
import assert from 'node:assert/strict'
import {readFile} from 'node:fs/promises'
import vm from 'node:vm'
const source=(await readFile(new URL('../frontend/src/components/PdfStepPanel.vue',import.meta.url),'utf8')).split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm,'')
function panel(open){
 const props={open},measurements=[],timers=new Map();let watcher,dispose,id=0
 const query={in(){return this},select(){return this},boundingClientRect(fn){measurements.push(fn);return this},exec(){}}
 const context={defineProps:()=>props,ref:value=>({value}),getCurrentInstance:()=>({proxy:{}}),watch:(_,fn)=>watcher=fn,nextTick:async()=>{},onBeforeUnmount:fn=>dispose=fn,uni:{createSelectorQuery:()=>query},setTimeout:fn=>{timers.set(++id,fn);return id},clearTimeout:id=>timers.delete(id)}
 const state=vm.runInNewContext(source+';\n({rendered,shown,height})',context)
 return {...state,change:async open=>{props.open=open;await watcher(open)},measure:()=>measurements.shift()({height:160}),tick:()=>{const pending=[...timers.values()];timers.clear();pending.forEach(fn=>fn())},dispose:()=>dispose()}
}
test('opening and closing animate measured height and remove the closed panel afterwards',async()=>{
 const p=panel(false);await p.change(true);assert.equal(p.rendered.value,true);assert.equal(p.height.value,'0px')
 p.measure();p.tick();assert.equal(p.height.value,'160px');assert.equal(p.shown.value,true)
 p.tick();assert.equal(p.height.value,'auto')
 await p.change(false);p.measure();assert.equal(p.height.value,'160px');p.tick();assert.equal(p.height.value,'0px');assert.equal(p.rendered.value,true)
 p.tick();assert.equal(p.rendered.value,false)
})
test('rapid toggles and unmounts ignore stale measurement callbacks',async()=>{
 const p=panel(false);await p.change(true);await p.change(false)
 p.measure();p.measure();p.tick();p.tick();assert.equal(p.rendered.value,false)
 await p.change(true);p.dispose();p.measure();p.tick();assert.equal(p.shown.value,false)
})
