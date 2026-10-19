$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    $mavenPath = if ($maven) { $maven.Source } else { Join-Path $projectRoot '.tools/maven/apache-maven-3.9.9/bin/mvn.cmd' }
    if (-not (Test-Path -LiteralPath $mavenPath)) { throw 'Maven unavailable; install Maven before submitting' }
    if (-not $env:JAVA_HOME) {
        $jdk = Get-ChildItem -LiteralPath (Join-Path $projectRoot '.tools/java') -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
    }
    & $mavenPath -f backend/pom.xml test -q
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
    node --test scripts/pdf-job.test.mjs scripts/pdf-files.test.mjs scripts/pdf-payment.test.mjs scripts/pdf-translation.test.mjs scripts/pdf-export-history.test.mjs scripts/pdf-step-panel.test.mjs scripts/pdf-cloud.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed' }
    npm run build:mp-weixin --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'Mini program build failed' }
    node scripts/check-mp-bundle.cjs
    if ($LASTEXITCODE -ne 0) { throw 'Mini program module check failed' }
    npm run build:h5 --prefix frontend
    if ($LASTEXITCODE -ne 0) { throw 'H5 build failed' }
    git diff --check
    if ($LASTEXITCODE -ne 0) { throw 'Diff check failed' }
    git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
    Write-Output 'All pre-commit checks passed'
} finally { Pop-Location }
