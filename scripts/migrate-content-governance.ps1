param([Parameter(Mandatory=$true)][string]$ConfigPath)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$config=[IO.File]::ReadAllText($ConfigPath)
$match=[regex]::Match($config,'(?m)^db.url=jdbc:sqlserver://([^;\r\n]+);databaseName=([^;\r\n]+)')
if(-not $match.Success){throw 'Unsupported JDBC configuration.'}
$server=$match.Groups[1].Value.Replace(':',',');$database=$match.Groups[2].Value
if($database -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database name.'}
$connection=[Data.SqlClient.SqlConnection]::new("Data Source=$server;Initial Catalog=$database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=8")
function Scalar([string]$sql){$command=$connection.CreateCommand();$command.CommandTimeout=180;$command.CommandText=$sql;try{$command.ExecuteScalar()}finally{$command.Dispose()}}
function Execute([string]$sql){$command=$connection.CreateCommand();$command.CommandTimeout=180;$command.CommandText=$sql;try{$null=$command.ExecuteNonQuery()}finally{$command.Dispose()}}
try{
 $connection.Open()
 if((Scalar 'SELECT COUNT(*) FROM schema_migrations WHERE version=4') -ne 1){throw 'Migration 04 is missing.'}
 if((Scalar 'SELECT COUNT(*) FROM schema_migrations WHERE version=11') -eq 0){
  $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
  if(-not $backupDir){throw 'SQL Server backup directory unavailable.'}
  $backup=(Join-Path $backupDir "$database-before-content-governance-$(Get-Date -Format 'yyyyMMdd-HHmmss').bak").Replace("'","''")
  Execute "BACKUP DATABASE [$database] TO DISK=N'$backup' WITH COPY_ONLY,CHECKSUM"
  Execute "RESTORE VERIFYONLY FROM DISK=N'$backup' WITH CHECKSUM"
  Write-Output "Verified backup: $backup"
  $counts=@{}
  foreach($table in @('lectures','questions','course_items','attempts','answers')){
   $counts[$table]=[int](Scalar "SELECT COUNT(*) FROM [$table]")
  }
  Execute ([IO.File]::ReadAllText((Join-Path $root 'database/11-content-governance.sql')))
  foreach($table in @('lectures','questions','course_items','attempts','answers')){
   $after=[int](Scalar "SELECT COUNT(*) FROM [$table]")
   if($after -ne $counts[$table]){throw "Existing rows changed in $table"}
  }
  Write-Output 'Migration 11 applied; existing rows verified unchanged.'
 }else{Write-Output 'Migration 11 already applied.'}
}finally{$connection.Dispose()}
