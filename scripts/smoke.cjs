// 仅针对本地开发用户。真实MySQL与HTTP接口冒烟验证。
const fs = require('node:fs');
const assert = require('node:assert/strict');
const base = 'http://localhost:8080/api';
async function main() {
  const health = await (await fetch(base+'/health')).json();
  assert.equal(health.developmentLogin,true,'只能对开发环境运行');
  const session = await (await fetch(base+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({development:true})})).json();
  assert.ok(session.token,JSON.stringify(session));
  const headers = {Authorization:'Bearer '+session.token};
  const created = [];
  async function process(operation,inputs) {
    const data = new FormData();
    inputs.forEach((input,i)=>data.append('files',new Blob([input]),'input'+i));
    const response = await fetch(base+'/process/'+operation,{method:'POST',headers,body:data});
    const result = await response.json();
    assert.equal(response.status,200,JSON.stringify(result));created.push(result.file.id);return result;
  }
  try {
    const images = await process('images',[fs.readFileSync('frontend/src/static/mascot.png')]);
    const download = await fetch(base+'/files/'+images.file.id+'/download',{headers});
    assert.equal(download.status,200);const pdf = Buffer.from(await download.arrayBuffer());assert.equal(pdf.subarray(0,4).toString(),'%PDF');
    const compressed = await process('compress',[pdf]);assert.ok(compressed.file.size_bytes<=pdf.length);
    await process('merge',[pdf,pdf]);
    const files = await (await fetch(base+'/files?kind=pdf&page=1',{headers})).json();
    assert.ok(created.every(id=>files.items.some(f=>f.id===id)));
    const unauthenticated = await fetch(base+'/files/'+images.file.id+'/download');assert.equal(unauthenticated.status,401);
    const cors = await fetch(base+'/health',{headers:{Origin:'http://127.0.0.1:5173'}});assert.equal(cors.headers.get('access-control-allow-origin'),'http://127.0.0.1:5173');
    console.log('PASS: real login/session, images-to-PDF, compress, merge, history, download, authentication, CORS');
  } finally {
    for(const id of created)await fetch(base+'/files/'+id,{method:'DELETE',headers});
    console.log('Test files removed. Successful operations consume the development user daily quota.');
  }
}
main().catch(error=>{console.error(error.message);process.exitCode=1});
