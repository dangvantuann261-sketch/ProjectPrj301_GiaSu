param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$TomcatHome = 'C:\Program Files\Apache Software Foundation\Tomcat 10.1',
    [string]$AntHome = 'C:\Program Files\NetBeans-17\netbeans\extide\ant',
    [string]$ConfigPath = '',
    [ValidateRange(1024, 65535)][int]$Port = 8082,
    [string]$TomcatBase = '',
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $workspaceRoot = Split-Path -Parent (Split-Path -Parent $projectRoot)
    $ConfigPath = Join-Path $workspaceRoot 'SuVietTutorData\config.properties'
    if (-not (Test-Path -LiteralPath $ConfigPath -PathType Leaf)) {
        $ConfigPath = 'C:\SuVietTutorData\config.properties'
    }
}

$java = Join-Path $JavaHome 'bin\java.exe'
$javac = Join-Path $JavaHome 'bin\javac.exe'
$antLauncher = Join-Path $AntHome 'lib\ant-launcher.jar'
$bootstrap = Join-Path $TomcatHome 'bin\bootstrap.jar'
$tomcatJuli = Join-Path $TomcatHome 'bin\tomcat-juli.jar'
foreach ($required in @($java, $javac, $antLauncher, $bootstrap, $tomcatJuli, $ConfigPath)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "File not found: $required. See docs/CHAY-VSCODE.md for path options."
    }
}
$ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path
if([string]::IsNullOrWhiteSpace($TomcatBase)){$TomcatBase=Join-Path $projectRoot '.local\tomcat'}
$TomcatBase=[IO.Path]::GetFullPath($TomcatBase)
$baseArgument='-Dcatalina.base='+$TomcatBase
$existing=Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine -match ([regex]::Escape($baseArgument)+'(?:"|\s|$)')
}
if($existing){throw "This TomcatBase is already running. Stop it or choose a separate -TomcatBase and -Port."}

# Check before building so a second run cannot overwrite a running instance.
$portProbe = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
try {
    $portProbe.Start()
} catch {
    throw "Port $Port is unavailable. Stop the previous run with Ctrl+C, or pass -Port 8083."
} finally {
    $portProbe.Stop()
}

Push-Location $projectRoot
try {
    if(-not $SkipBuild){
        Write-Host 'Building with Ant and JDK 17...' -ForegroundColor Cyan
        & $java '-jar' $antLauncher "-Dtutor.java.home=$JavaHome" 'clean' 'dist'
        if ($LASTEXITCODE -ne 0) { throw 'Build failed. Fix the error shown above before running Tomcat.' }
    }

    # A private CATALINA_BASE avoids using the deployment configured by NetBeans.
    if([string]::IsNullOrWhiteSpace($TomcatBase)){$TomcatBase=Join-Path $projectRoot '.local\tomcat'}
    $tomcatBase = [IO.Path]::GetFullPath($TomcatBase)
    foreach ($folder in @('conf', 'logs', 'temp', 'webapps', 'work')) {
        New-Item -ItemType Directory -Force -Path (Join-Path $tomcatBase $folder) | Out-Null
    }
    foreach ($configName in @('web.xml', 'catalina.properties', 'logging.properties')) {
        Copy-Item -LiteralPath (Join-Path $TomcatHome "conf\$configName") -Destination (Join-Path $tomcatBase "conf\$configName") -Force
    }
    $serverXml = @"
<?xml version="1.0" encoding="UTF-8"?>
<Server port="-1">
  <Listener className="org.apache.catalina.core.JreMemoryLeakPreventionListener"/>
  <Service name="Catalina">
    <Connector address="127.0.0.1" port="$Port" protocol="HTTP/1.1" URIEncoding="UTF-8"/>
    <Engine name="Catalina" defaultHost="localhost">
      <Host name="localhost" appBase="webapps" unpackWARs="false" autoDeploy="false" deployOnStartup="true"/>
    </Engine>
  </Service>
</Server>
"@
    [System.IO.File]::WriteAllText((Join-Path $tomcatBase 'conf\server.xml'), $serverXml, [System.Text.UTF8Encoding]::new($false))
    Copy-Item -LiteralPath (Join-Path $projectRoot 'dist\SuVietTutor.war') -Destination (Join-Path $tomcatBase 'webapps\SuVietTutor.war') -Force

    Write-Host "Config: $ConfigPath"
    Write-Host "After 'Server startup', open http://127.0.0.1:$Port/SuVietTutor/explore" -ForegroundColor Green
    Write-Host 'Keep this terminal open. Press Ctrl+C to stop; run again after saving changes.'
    $javaArgs = @(
        '--add-opens=java.base/java.lang=ALL-UNNAMED',
        '--add-opens=java.base/java.io=ALL-UNNAMED',
        '--add-opens=java.base/java.util=ALL-UNNAMED',
        '--add-opens=java.base/java.util.concurrent=ALL-UNNAMED',
        '--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED',
        '-Dfile.encoding=UTF-8',
        '-Duser.timezone=Asia/Ho_Chi_Minh',
        "-Dtutor.config=$ConfigPath",
        "-Dcatalina.home=$TomcatHome",
        "-Dcatalina.base=$tomcatBase",
        "-Djava.io.tmpdir=$(Join-Path $tomcatBase 'temp')",
        '-Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager',
        "-Djava.util.logging.config.file=$(Join-Path $tomcatBase 'conf\logging.properties')",
        '-classpath', "$bootstrap;$tomcatJuli",
        'org.apache.catalina.startup.Bootstrap', 'start'
    )
    & $java @javaArgs
    if ($LASTEXITCODE -ne 0) { throw 'Tomcat exited with an error. Read the messages above.' }
} finally {
    Pop-Location
}
