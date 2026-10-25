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
 if((Scalar 'SELECT COUNT(*) FROM schema_migrations WHERE version IN(4,7,8,9)') -ne 4){throw 'Required migrations 04, 07, 08 and 09 are missing.'}
 if((Scalar 'SELECT COUNT(*) FROM schema_migrations WHERE version=10') -eq 0){
  $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
  if(-not $backupDir){throw 'SQL Server backup directory unavailable.'}
  $backup=(Join-Path $backupDir "$database-before-learning-experience-$(Get-Date -Format 'yyyyMMdd-HHmmss').bak").Replace("'","''")
  Execute "BACKUP DATABASE [$database] TO DISK=N'$backup' WITH COPY_ONLY,CHECKSUM"
  Execute "RESTORE VERIFYONLY FROM DISK=N'$backup' WITH CHECKSUM"
  Write-Output "Verified backup: $backup"
  $tables=@('attempts','attempt_items','answers','enrollments','learning_records','learning_activity')
  $columns=@{};$fingerprints=@{}
  foreach($table in $tables){
   $columns[$table]=[string](Scalar "SELECT STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id) FROM sys.columns WHERE object_id=OBJECT_ID('$table')")
   $key=if($table -eq 'learning_records'){'enrollment_id],[course_item_id'}else{'id'}
   $fingerprints[$table]=[string](Scalar "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT $($columns[$table]) FROM [$table] ORDER BY [$key] FOR JSON PATH,INCLUDE_NULL_VALUES)),2)")
  }
  Execute ([IO.File]::ReadAllText((Join-Path $root 'database/10-learning-experience.sql')))
  foreach($table in $tables){
   $key=if($table -eq 'learning_records'){'enrollment_id],[course_item_id'}else{'id'}
   $after=[string](Scalar "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT $($columns[$table]) FROM [$table] ORDER BY [$key] FOR JSON PATH,INCLUDE_NULL_VALUES)),2)")
   if($after -ne $fingerprints[$table]){throw "Existing data changed in $table"}
  }
  Write-Output 'Migration 10 applied; existing learning data verified unchanged.'
 }else{Write-Output 'Migration 10 already applied.'}
}finally{$connection.Dispose()}
