const key='pdf-export-history-v1'
const lifetime=30*60*1000
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
 const entry={...file,name:details.name||file.name,expiresAt,exportedAt:existing?.exportedAt||now,
  inputTokens:Math.max(0,Number(details.inputTokens)||0),outputTokens:Math.max(0,Number(details.outputTokens)||0)}
 uni.setStorageSync(key,[entry,...rows.filter(row=>row.path!==file.path)].sort((a,b)=>b.exportedAt-a.exportedAt))
 return entry
}
