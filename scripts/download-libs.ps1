$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$projectRoot = Split-Path -Parent $PSScriptRoot
$target = Join-Path $projectRoot 'lib/gson-2.11.0.jar'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar' -OutFile $target
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne '57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b') { throw 'Sai checksum: lib/gson-2.11.0.jar' }
$target = Join-Path $projectRoot 'lib/mssql-jdbc-12.8.1.jre11.jar'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/com/microsoft/sqlserver/mssql-jdbc/12.8.1.jre11/mssql-jdbc-12.8.1.jre11.jar' -OutFile $target
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne 'e6933c0711e598a224060e52ed31392f720a4a7664e85d8ae37c52a85b67ebb0') { throw 'Sai checksum: lib/mssql-jdbc-12.8.1.jre11.jar' }
$target = Join-Path $projectRoot 'lib/jakarta.servlet.jsp.jstl-api-3.0.2.jar'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/jakarta/servlet/jsp/jstl/jakarta.servlet.jsp.jstl-api/3.0.2/jakarta.servlet.jsp.jstl-api-3.0.2.jar' -OutFile $target
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne 'd55951aefedb543cb16193a873b92d22c40c3b8ba26cfa7ed9229eb118bb9c9b') { throw 'Sai checksum: lib/jakarta.servlet.jsp.jstl-api-3.0.2.jar' }
$target = Join-Path $projectRoot 'lib/jakarta.servlet.jsp.jstl-3.0.1.jar'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/glassfish/web/jakarta.servlet.jsp.jstl/3.0.1/jakarta.servlet.jsp.jstl-3.0.1.jar' -OutFile $target
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne '5cc6e60b9e74d38c25fe4f2d22dfd40577f5b8396bc885f7061cd2c525a43b86') { throw 'Sai checksum: lib/jakarta.servlet.jsp.jstl-3.0.1.jar' }
$target = Join-Path $projectRoot 'lib/compile-only/jakarta.servlet-api-6.0.0.jar'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/jakarta/servlet/jakarta.servlet-api/6.0.0/jakarta.servlet-api-6.0.0.jar' -OutFile $target
if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ne 'c034eb1afb158987dbb53a5fea0cadf611c8dae8daadd59c44d9d5ab70129cef') { throw 'Sai checksum: lib/compile-only/jakarta.servlet-api-6.0.0.jar' }
