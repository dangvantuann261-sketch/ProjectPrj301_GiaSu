param(
    [Parameter(Mandatory=$true)][string]$TestConfigPath,
    [string]$BaseUrl='http://127.0.0.1:18086/SuVietTutor',
    [string]$JavaHome='C:\Program Files\Java\jdk-17',
    [string]$AntHome='C:\Program Files\NetBeans-17\netbeans\extide\ant'
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$java=Join-Path $JavaHome 'bin\java.exe'
$config=Get-Content -LiteralPath $TestConfigPath -Raw
if($config -notmatch 'databaseName=[^;\r\n]*_UpgradeTest_'){throw 'Only isolated UpgradeTest databases are accepted.'}
if($config -match '(?m)^ai.apiKey=\S+'){throw 'Disable AI in the test configuration; no real provider calls are allowed.'}
& $java -jar (Join-Path $AntHome 'lib\ant-launcher.jar') "-Dtutor.java.home=$JavaHome" -f (Join-Path $root 'build.xml') test
if($LASTEXITCODE -ne 0){throw 'Core tests failed.'}
$classpath="$root\build\test\classes;$root\build\web\WEB-INF\classes;$root\lib\*;$root\lib\compile-only\*"
& $java "-Dtutor.config=$TestConfigPath" -cp $classpath vn.suviet.CourseIntegrationTests
if($LASTEXITCODE -ne 0){throw 'SQL integration tests failed.'}
& $java "-Dtutor.config=$TestConfigPath" -cp $classpath vn.suviet.CourseHttpTests $BaseUrl
if($LASTEXITCODE -ne 0){throw 'HTTP tests failed.'}
Write-Output 'All course tests passed. Synthetic data exists only in the isolated test database.'