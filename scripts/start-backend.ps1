$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaExe = if ($javaCommand) { $javaCommand.Source } else {
    $portableJdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $portableJdk) { throw '请安装Java 21，或用docker compose up -d --build运行后端' }
    Join-Path $portableJdk.FullName 'bin/java.exe'
}
Push-Location (Join-Path $projectRoot 'backend')
try {
    if (-not (Test-Path 'target/doc-assistant-0.1.0.jar')) { throw '请先运行mvn package' }
    $env:CORS_ORIGIN = 'http://127.0.0.1:5173'
    & $javaExe -Xmx768m -jar target/doc-assistant-0.1.0.jar
} finally { Pop-Location }
