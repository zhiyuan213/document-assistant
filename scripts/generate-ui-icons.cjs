// 原生SVG绘制UI图标并渲染PNG，供H5和微信小程序共用。
const fs=require('node:fs'),path=require('node:path')
const sharp=require(process.env.SHARP_MODULE||'sharp')
const root=path.resolve(__dirname,'../frontend/src/static/icons');fs.mkdirSync(root,{recursive:true})
const shapes={
 search:'<circle cx="13" cy="13" r="8" fill="none"/><path d="m19 19 8 8" fill="none"/>',
 cloud:'<path d="M8 25C0 25 1 13 9 13C10 2 25 3 26 14C34 15 33 25 25 25" fill="none"/><path d="M16 14v16m-5-5 5 5 5-5" fill="none"/>',
 document:'<path d="M7 3h13l6 6v20H7z"/><path d="M20 3v7h6M11 15h11m-11 5h11m-11 5h7" fill="none" stroke="#b99afc"/>',
 notes:'<path d="M7 4h18v24H7z" fill="none"/><path d="M11 9h10m-10 5h10m-10 5h10m-10 5h7" fill="none"/>',
 exam:'<path d="M8 8h16v21H8z" fill="none"/><path d="M13 8V4h6v4m-7 7h8m-8 6h8m-8 5h5" fill="none"/>',
 briefcase:'<rect x="3" y="10" width="26" height="17" rx="4" fill="none"/><path d="M11 10V5h10v5M3 18h26m-15-2h4v6h-4z" fill="none"/>',
 badge:'<path d="m16 3 4 6 7 2-4 6v8l-7-3-7 3v-8l-4-6 7-2z"/><circle cx="16" cy="14" r="3" fill="none" stroke="#fff8"/>',
 language:'<path d="M4 5h23v21H4z" fill="none"/><path d="M8 11h9m-5-3v3m-3 2 7 8m0-8-7 8m11-8 4 8m-7-2h9" fill="none"/>',
 cap:'<path d="m2 11 14-7 14 7-14 7z"/><path d="M7 15v9q9 7 18 0v-9m4-3v12" fill="none"/>',
 book:'<path d="M3 6q7-3 13 2 6-5 13-2v21q-7-4-13 0-6-4-13 0z" fill="none"/><path d="M16 8v19" fill="none"/>',
 school:'<path d="m3 11 13-8 13 8M6 12v16h20V12M3 29h26m-17-1V17h8v11" fill="none"/>',
 grid:'<circle cx="9" cy="9" r="4"/><circle cx="23" cy="9" r="4"/><circle cx="9" cy="23" r="4"/><circle cx="23" cy="23" r="4"/>',
}
async function draw(name,shape,color){const svg=`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" fill="${color}" stroke="${color}" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">${shape}</svg>`;await sharp(Buffer.from(svg)).resize(64,64).png().toFile(path.join(root,name+'.png'))}
;(async()=>{for(const [name,shape]of Object.entries(shapes))await draw(name,shape,'#ffffff');await draw('search-muted',shapes.search,'#aaa0bd');await draw('search-white',shapes.search,'#ffffff');await draw('cloud-purple',shapes.cloud,'#7b3cff')})()
