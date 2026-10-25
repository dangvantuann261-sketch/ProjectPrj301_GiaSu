param(
    [Parameter(Mandatory=$true)][string]$ConfigPath,
    [switch]$TestCopy
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$config=Get-Content -LiteralPath $ConfigPath -Raw
$match=[regex]::Match($config,'(?m)^db.url=jdbc:sqlserver://([^;\r\n]+);databaseName=([^;\r\n]+)')
if(-not $match.Success){throw 'Unsupported JDBC URL. Run database/07-admin-essay-workflow.sql in SSMS after a backup.'}
$server=$match.Groups[1].Value -replace ':',','
$database=$match.Groups[2].Value
if($database -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database name.'}
$connection=New-Object System.Data.SqlClient.SqlConnection("Data Source=$server;Initial Catalog=master;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=8")
function Execute([string]$sql){$command=$connection.CreateCommand();$command.CommandTimeout=180;$command.CommandText=$sql;try{$null=$command.ExecuteNonQuery()}finally{$command.Dispose()}}
function Scalar([string]$sql){$command=$connection.CreateCommand();$command.CommandText=$sql;try{return $command.ExecuteScalar()}finally{$command.Dispose()}}
try{
    $connection.Open()
    if((Scalar "SELECT IS_SRVROLEMEMBER('sysadmin')") -ne 1){throw 'Windows account must be SQL Server administrator. Application DB permissions are not changed.'}
    $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
    if(-not $backupDir){throw 'SQL Server backup directory is unavailable.'}
    $stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
    $backup=Join-Path $backupDir "$database-before-admin-essay-$stamp.bak"
    $quotedBackup=$backup.Replace("'","''")
    Execute "BACKUP DATABASE [$database] TO DISK=N'$quotedBackup' WITH COPY_ONLY,CHECKSUM"
    Execute "RESTORE VERIFYONLY FROM DISK=N'$quotedBackup' WITH CHECKSUM"
    Write-Output "Verified database backup: $backup"
    $backupLocal=Join-Path $root ".local\backups\$stamp"
    New-Item -ItemType Directory -Force -Path $backupLocal | Out-Null
    $uploadMatch=[regex]::Match($config,'(?m)^uploads.dir=([^\r\n]+)')
    if($uploadMatch.Success -and (Test-Path -LiteralPath $uploadMatch.Groups[1].Value.Trim())){
        Copy-Item -LiteralPath $uploadMatch.Groups[1].Value.Trim() -Destination (Join-Path $backupLocal 'uploads') -Recurse
    }
    $target=$database
    if($TestCopy){
        $target=$database+'_UpgradeTest_'+$stamp.Replace('-','')
        $existing=Scalar "SELECT DB_ID(N'$target')"
        if($null -ne $existing -and $existing -isnot [DBNull]){throw "Test database $target already exists; it will not be overwritten."}
        $dataPath=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultDataPath'))")
        $logPath=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultLogPath'))")
        $cmd=$connection.CreateCommand();$cmd.CommandText="RESTORE FILELISTONLY FROM DISK=N'$quotedBackup'";$reader=$cmd.ExecuteReader();$moves=@();$n=0
        while($reader.Read()){
            $logical=([string]$reader['LogicalName']).Replace("'","''");$isLog=([string]$reader['Type']) -eq 'L';$n++
            $folder=if($isLog){$logPath}else{$dataPath};$extension=if($isLog){'ldf'}else{'mdf'}
            $physical=(Join-Path $folder "$target-$n.$extension").Replace("'","''");$moves+="MOVE N'$logical' TO N'$physical'"
        }
        $reader.Close();$cmd.Dispose()
        Execute ("RESTORE DATABASE [$target] FROM DISK=N'$quotedBackup' WITH CHECKSUM,RECOVERY,"+($moves -join ','))
        Write-Output "Restored isolated test database: $target"
    }
    $connection.ChangeDatabase($target)
    $tables=@('users','subjects','lessons','topics','lectures','questions','attempts','attempt_items','answers','app_settings')
    $before=@{};$fingerprints=@{};$columns=@{}
    foreach($table in $tables){
        $before[$table]=Scalar "SELECT COUNT_BIG(*) FROM [$table]"
        $columns[$table]=[string](Scalar "SELECT STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(name)),',') WITHIN GROUP(ORDER BY column_id) FROM sys.columns WHERE object_id=OBJECT_ID('$table')")
        $key=if($table -eq 'app_settings'){'setting_key'}else{'id'}
        $fingerprints[$table]=[string](Scalar "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT $($columns[$table]) FROM [$table] ORDER BY [$key] FOR JSON PATH,INCLUDE_NULL_VALUES)),2)")
    }
    $sql=[IO.File]::ReadAllText((Join-Path $root 'database\07-admin-essay-workflow.sql'))
    Execute $sql
    foreach($table in $tables){
        if((Scalar "SELECT COUNT_BIG(*) FROM [$table]") -ne $before[$table]){throw "Row count changed: $table"}
        $key=if($table -eq 'app_settings'){'setting_key'}else{'id'}
        $after=[string](Scalar "SELECT CONVERT(varchar(64),HASHBYTES('SHA2_256',(SELECT $($columns[$table]) FROM [$table] ORDER BY [$key] FOR JSON PATH,INCLUDE_NULL_VALUES)),2)")
        if($after -ne $fingerprints[$table]){throw "Original data changed: $table"}
    }
    if((Scalar 'SELECT COUNT(*) FROM sys.foreign_keys WHERE is_disabled=1 OR is_not_trusted=1') -ne 0){throw 'Untrusted foreign key found.'}
    Write-Output "Migration 7 completed on $target; original columns SHA-256 and row counts preserved for all ten existing tables."
    if($TestCopy){
        # The restored copy must never use live credentials saved through Admin.
        Execute "DELETE FROM app_settings WHERE setting_key IN('ai_key','essay_ai_key','ai_url','essay_ai_url','ai_model','essay_ai_model','ai_timeout','essay_ai_timeout')"
        $testConfig=$config.Replace("databaseName=$database;","databaseName=$target;")
        $testConfig=[regex]::Replace($testConfig,'(?m)^ai.apiKey=.*$','ai.apiKey=')
        $testUploads=(Join-Path $root '.local\admin-essay-test-uploads').Replace('\','/')
        $testConfig=[regex]::Replace($testConfig,'(?m)^uploads.dir=.*$',"uploads.dir=$testUploads")
        $testPath=Join-Path $root '.local\admin-essay-test-config.properties'
        [IO.File]::WriteAllText($testPath,$testConfig,[Text.UTF8Encoding]::new($false))
        Write-Output "Isolated test configuration: $testPath (AI disabled; separate uploads)."
    }
}finally{$connection.Dispose()}
