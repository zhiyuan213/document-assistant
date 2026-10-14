param([string]$Base='http://localhost:8080/api/pdf')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
if((Invoke-RestMethod ($Base+'/config')).billingEnabled){throw 'Use an unlimited test deployment; do not consume user credits'}
$visitor=Invoke-RestMethod -Method Post ($Base+'/visitor') -ContentType application/json -Body '{}'
$headers=@{Authorization=('Bearer '+$visitor.token)}
$out=Join-Path $PSScriptRoot '../.tools/expanded-audit';New-Item -ItemType Directory -Path $out -Force|Out-Null
$fixtures=Join-Path $PSScriptRoot '../backend/target/expanded-smoke'
$pdf=Get-Item (Join-Path $fixtures 'scan.pdf');$image=Get-Item (Join-Path $PSScriptRoot '../.tools/pdf-upload-test.png')
$cases=@(
 @{tool='excel-pdf';files=(Get-Item (Join-Path $fixtures 'sample.xlsx'))},
 @{tool='ppt-pdf';files=(Get-Item (Join-Path $fixtures 'sample.pptx'))},
 @{tool='ocr';files=$pdf},@{tool='crop';files=$pdf},
 @{tool='sign';files=@($pdf,$image);signX='10';signY='10';signWidth='20'},
 @{tool='extract-images';files=$pdf},@{tool='repair';files=$pdf},
 @{tool='markdown';files=(Get-Item (Join-Path $PSScriptRoot '../backend/target/smoke/sample.pdf'))},
 @{tool='compare';files=@($pdf,(Get-Item (Join-Path $fixtures 'crop.pdf')))},
 @{tool='redact';files=$pdf;redactX='0';redactY='0';redactWidth='100';redactHeight='100'}
)
foreach($form in $cases){
 $jobId=$null
 try{
  $created=Invoke-RestMethod -Method Post ($Base+'/jobs') -Headers $headers -Form $form -TimeoutSec 30;$jobId=$created.id
  for($i=0;$i -lt 300;$i++){$job=Invoke-RestMethod ($Base+'/jobs/'+$jobId) -Headers $headers -TimeoutSec 20;if($job.status -eq 'failed'){throw ($form.tool+': '+$job.error_message)};if($job.status -eq 'completed'){break};Start-Sleep -Milliseconds 500}
  if($job.status -ne 'completed'){throw 'processing timeout'}
  $file=Join-Path $out ($form.tool+'.'+$job.extension);Invoke-WebRequest ($Base+'/jobs/'+$jobId+'/file') -Headers $headers -OutFile $file -TimeoutSec 30
  if($job.extension -eq 'zip'){$zip=[IO.Compression.ZipFile]::OpenRead($file);try{if($zip.Entries.Count -eq 0){throw 'empty ZIP'}}finally{$zip.Dispose()}}
  else{if([Text.Encoding]::ASCII.GetString([IO.File]::ReadAllBytes($file),0,4) -ne '%PDF'){throw 'invalid PDF'}}
  Write-Output ($form.tool+': completed and downloaded, '+(Get-Item $file).Length+' bytes')
 }finally{if($jobId){$null=Invoke-RestMethod -Method Delete ($Base+'/jobs/'+$jobId) -Headers $headers -TimeoutSec 20}}
}
