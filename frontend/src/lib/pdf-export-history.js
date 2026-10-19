const key='pdf-export-history-v1'
const lifetime=30*60*1000
const pendingKey='pdf-pending-downloads-v1'
export function pendingDownloads(now=Date.now()) {
 const stored=uni.getStorageSync(pendingKey),items=Array.isArray(stored)?stored:[]
 const valid=items.filter(item=>item.job?.id&&item.expiresAt>now)
 uni.setStorageSync(pendingKey,valid);return valid
}
export function queueDownloads(items,now=Date.now()) {
 const previous=pendingDownloads(now),known=new Set(previous.map(item=>item.job.id))
 for(const item of items)if(!known.has(item.job.id)){previous.push({...item,expiresAt:exportExpiry(item.job.expiresAt||item.job.expires_at,now)});known.add(item.job.id)}
 uni.setStorageSync(pendingKey,previous)
}
export function completeDownloads(ids) { uni.setStorageSync(pendingKey,pendingDownloads().filter(item=>!ids.includes(item.job.id))) }
export function resolveExportSelection(files,paths,now=Date.now()) {
 if(!paths.length)throw new Error('请先选择文件')
 if(paths.length>20)throw new Error('一次最多转发20个文件')
 const selected=[...new Set(paths)].map(path=>files.find(file=>file.path===path))
 if(selected.some(file=>!file||file.expiresAt<=now))throw new Error('选中的文件已过期，请重新选择')
 return selected
}
export function toggleExportSelection(paths,path) {
 if(paths.includes(path))return paths.filter(item=>item!==path)
 if(paths.length>=20)throw new Error('一次最多转发20个文件')
 return [...paths,path]
}
export function exportExpiry(value,now=Date.now()) {
 const parsed=typeof value==='number'?value:Date.parse(String(value||''))
 return Math.min(now+lifetime,Number.isFinite(parsed)?parsed:now+lifetime)
}
function removeLocal(file) {
 // Only delete generated application files, never an uploaded source document.
 if(typeof wx==='undefined'||!wx.env?.USER_DATA_PATH)return
 const root=wx.env.USER_DATA_PATH+'/'
 if(!file.path?.startsWith(root))return
 const name=file.path.slice(root.length)
 if(!/^(ai-report-|dengke-result-)[a-zA-Z0-9.-]+$/.test(name))return
 try{wx.getFileSystemManager().unlinkSync(file.path)}catch{}
}
export function listExportFiles(now=Date.now()) {
 // Bundles are temporary derivatives, not extra rows in the file list. Clear crash leftovers too.
 if(typeof wx!=='undefined'&&wx.env?.USER_DATA_PATH)try{
  const fs=wx.getFileSystemManager(),root=wx.env.USER_DATA_PATH
  for(const name of fs.readdirSync(root)){const match=/^dengke-bundle-(\d+)-[a-z0-9]+\.zip$/.exec(name);if(match&&Number(match[1])<=now)try{fs.unlinkSync(root+'/'+name)}catch{}}
 }catch{}
 const stored=uni.getStorageSync(key),rows=Array.isArray(stored)?stored:[]
 const valid=rows.filter(file=>file.path&&Number(file.expiresAt)>now)
 const activePaths=new Set(valid.map(file=>file.path))
 for(const file of rows)if(!activePaths.has(file.path))removeLocal(file)
 valid.sort((a,b)=>b.exportedAt-a.exportedAt)
 uni.setStorageSync(key,valid)
 return valid
}
export function rememberExport(file,details={},now=Date.now()) {
 if(!file?.path)return file
 const rows=listExportFiles(now),existing=rows.find(row=>row.path===file.path)
 const expiresAt=Math.min(existing?.expiresAt||Infinity,exportExpiry(details.expiresAt||file.expiresAt,now))
 if(expiresAt<=now){removeLocal(file);throw new Error('文件已超过30分钟，请重新处理')}
 const entry={...file,name:details.name||file.name,expiresAt,exportedAt:existing?.exportedAt||now,source:details.source||existing?.source||file.source||'ai',size:details.size||file.size||existing?.size||0,jobId:details.jobId||existing?.jobId||'',
  inputTokens:Math.max(0,Number(details.inputTokens)||0),outputTokens:Math.max(0,Number(details.outputTokens)||0)}
 uni.setStorageSync(key,[entry,...rows.filter(row=>row.path!==file.path)].sort((a,b)=>b.exportedAt-a.exportedAt))
 return entry
}
