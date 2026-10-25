param(
 [ValidateSet('Validate','DryRun','Apply','Verify')][string]$Mode='Validate',
 [string]$ConfigPath=''
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$json=[IO.File]::ReadAllText((Join-Path $root 'database/history6-kntt-content.json'),[Text.Encoding]::UTF8)
$seed=$json|ConvertFrom-Json
if($seed.seed_key -ne 'seed.history6.kntt.v1' -or $seed.grade -ne 6){throw 'Unexpected curriculum.'}
if(@($seed.lessons).Count -ne 20 -or (@($seed.lessons.number|Sort-Object -Unique) -join ',') -ne ((1..20) -join ',')){throw 'Expected lesson numbers 1-20.'}
if(@($seed.lessons.source.video_url|Sort-Object -Unique).Count -ne 20){throw 'Each lesson needs its own video.'}
foreach($l in $seed.lessons){
 if(-not $l.title -or $l.title.Length -gt 255 -or -not $l.body -or $l.body.Length -gt 3500){throw 'Invalid lesson text.'}
 if($l.source.source_url -notmatch '^https://loigiaihay[.]com/[^\s]+[.]html$'){throw 'Invalid textbook source.'}
 if($l.source.video_url -notmatch '^https://www[.]youtube[.]com/watch[?]v=[A-Za-z0-9_-]{11}$'){throw 'Invalid video URL.'}
 if(@($l.questions).Count -ne 5 -or @($l.questions.prompt|Sort-Object -Unique).Count -ne 5){throw 'Expected five distinct questions.'}
 foreach($q in $l.questions){
  if(-not $q.prompt -or -not $q.explanation -or $q.prompt.Length -gt 4000){throw 'Missing question/explanation.'}
  if(@($q.options).Count -ne 4 -or @($q.options|Sort-Object -Unique).Count -ne 4){throw 'Four distinct options required.'}
  foreach($o in $q.options){if([string]::IsNullOrWhiteSpace($o) -or $o.Length -gt 1000){throw 'Invalid option.'}}
  if($q.answer -cnotmatch '^[ABCD]$' -or $q.difficulty -notin @('EASY','MEDIUM','HARD')){throw 'Invalid answer.'}
 }
}
Write-Output 'Validated: 20 lessons, 20 distinct videos, 100 questions.'
if($Mode -eq 'Validate'){return}
if(-not $ConfigPath){
    $ConfigPath=Join-Path (Split-Path -Parent (Split-Path -Parent $root)) 'SuVietTutorData\config.properties'
    if(-not (Test-Path -LiteralPath $ConfigPath)){$ConfigPath='C:\SuVietTutorData\config.properties'}
}
# Read only the JDBC destination; use Windows authentication, never print secrets.
$config=[IO.File]::ReadAllText($ConfigPath)
$match=[regex]::Match($config,'(?m)^db.url=jdbc:sqlserver://([^;\r\n]+);databaseName=([^;\r\n]+)')
if(-not $match.Success){throw 'Unsupported JDBC URL.'}
$server=$match.Groups[1].Value -replace ':',','
$database=$match.Groups[2].Value
if($database -notmatch '^[A-Za-z0-9_]+$'){throw 'Invalid database name.'}
$connection=New-Object System.Data.SqlClient.SqlConnection("Data Source=$server;Initial Catalog=$database;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;Connection Timeout=8")
function Scalar([string]$sql){$cmd=$connection.CreateCommand();$cmd.CommandText=$sql;try{return $cmd.ExecuteScalar()}finally{$cmd.Dispose()}}
function Query([string]$sql){
    $cmd=$connection.CreateCommand();$cmd.CommandText=$sql;$cmd.CommandTimeout=180
    $adapter=New-Object System.Data.SqlClient.SqlDataAdapter($cmd);$set=New-Object System.Data.DataSet
    try{$null=$adapter.Fill($set);foreach($table in $set.Tables){$table | Format-Table -AutoSize | Out-String | Write-Output}}
    finally{$adapter.Dispose();$cmd.Dispose();$set.Dispose()}
}
try{
    $connection.Open()
    Write-Output "Database: $database; mode: $Mode"
    $already=Scalar "SELECT COUNT(*) FROM app_settings WHERE setting_key='seed.history6.kntt.v1'"
    if($Mode -eq 'Verify' -or ($Mode -eq 'Apply' -and $already -gt 0)){
        if($already -ne 1){throw 'Grade 6 marker not found.'}
        Query ([IO.File]::ReadAllText((Join-Path $root 'database/verify-history6.sql'),[Text.Encoding]::UTF8))
        Write-Output 'Verified grade 6 curriculum. No data changed.'
        return
    }
    if($Mode -eq 'Apply'){
        # The SQL Server service writes this backup in its configured backup folder.
        $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
        if(-not $backupDir){throw 'SQL Server backup folder unavailable.'}
        $backup=Join-Path $backupDir ($database+'-before-history6-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)+'.bak')
        $cmd=$connection.CreateCommand();$cmd.CommandTimeout=180
        $cmd.CommandText="BACKUP DATABASE [$database] TO DISK=@path WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=@path WITH CHECKSUM;"
        $null=$cmd.Parameters.Add('@path',[Data.SqlDbType]::NVarChar,4000);$cmd.Parameters['@path'].Value=$backup
        try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
        Write-Output "Verified backup: $backup"
    }
    $cmd=$connection.CreateCommand();$cmd.CommandTimeout=180
    $cmd.CommandText=[IO.File]::ReadAllText((Join-Path $root 'database\06-history6-kntt.sql'),[Text.Encoding]::UTF8)
    $null=$cmd.Parameters.Add('@seed_json',[Data.SqlDbType]::NVarChar,-1);$cmd.Parameters['@seed_json'].Value=$json
    $null=$cmd.Parameters.Add('@commit',[Data.SqlDbType]::Bit);$cmd.Parameters['@commit'].Value=($Mode -eq 'Apply')
    $adapter=New-Object System.Data.SqlClient.SqlDataAdapter($cmd);$set=New-Object System.Data.DataSet
    try{$null=$adapter.Fill($set);foreach($table in $set.Tables){$table | Format-Table -AutoSize | Out-String -Width 220 | Write-Output}}
    finally{$adapter.Dispose();$cmd.Dispose();$set.Dispose()}
}finally{$connection.Dispose()}
