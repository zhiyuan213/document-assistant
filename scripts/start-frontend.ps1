$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $projectRoot 'frontend')
try {
    if (-not (Test-Path 'node_modules/@dcloudio/vite-plugin-uni/bin/uni.js')) { throw '请先在frontend运行pnpm install' }
    & node node_modules/@dcloudio/vite-plugin-uni/bin/uni.js --host 127.0.0.1 --port 5173
} finally { Pop-Location }
