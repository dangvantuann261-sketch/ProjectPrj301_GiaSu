param(
    [ValidateSet('Validate','DryRun','Apply','Verify')][string]$Mode='Validate',
    [string]$ConfigPath=''
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$contentPath=Join-Path $root 'database\history-courses-content.json'
$json=[IO.File]::ReadAllText($contentPath,[Text.Encoding]::UTF8)
$seed=$json | ConvertFrom-Json
if($seed.seed_key -ne 'seed.history_6_12.v1'){throw 'Unexpected seed key.'}
if(@($seed.courses).Count -ne 7 -or (@($seed.courses.grade | Sort-Object -Unique) -join ',') -ne '6,7,8,9,10,11,12'){throw 'Expected grades 6-12 exactly once.'}
$total=0; $videos=0
foreach($course in $seed.courses){
    if([string]::IsNullOrWhiteSpace($course.title) -or $course.title.Length -gt 255){throw 'Invalid course title.'}
    if(@($course.lessons).Count -ne 3){throw 'Expected three lessons per course.'}
    foreach($lesson in $course.lessons){
        if([string]::IsNullOrWhiteSpace($lesson.body) -or $lesson.body.Length -gt 3500){throw 'Invalid lesson body.'}
        if($lesson.source.grade -ne $course.grade -or $lesson.source.lesson -ne $lesson.source_lesson){throw 'Wrong source mapping.'}
        if($lesson.source.source_url -notmatch '^https://vietjack\.com/[^\s]+\.jsp$'){throw 'Invalid source URL.'}
        if($lesson.source.video_url){
            if($lesson.source.video_url -notmatch '^https://www\.youtube\.com/watch\?v=[A-Za-z0-9_-]{11}$'){throw 'Invalid YouTube URL.'}
            $videos++
        }
        if(@($lesson.questions).Count -ne 5){throw 'Expected five questions per lesson.'}
        if(@($lesson.questions.prompt | Sort-Object -Unique).Count -ne 5){throw 'Duplicate question prompt.'}
        foreach($q in $lesson.questions){
            if([string]::IsNullOrWhiteSpace($q.prompt) -or [string]::IsNullOrWhiteSpace($q.explanation)){throw 'Missing question text/explanation.'}
            if(@($q.options).Count -ne 4 -or @($q.options | Sort-Object -Unique).Count -ne 4){throw 'Expected four distinct options.'}
            foreach($option in $q.options){if([string]::IsNullOrWhiteSpace($option) -or $option.Length -gt 1000){throw 'Invalid option.'}}
            if($q.answer -cnotmatch '^[ABCD]$' -or $q.difficulty -notin @('EASY','MEDIUM','HARD')){throw 'Invalid answer/difficulty.'}
            $total++
        }
    }
}
Write-Output "Validated: 7 courses, 21 lessons, $total MCQs, $videos YouTube videos, $(21-$videos) source documents."
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
    if((Scalar "SELECT COUNT(*) FROM app_settings WHERE setting_key='seed.history6.kntt.v1'") -gt 0){throw 'This curriculum was replaced by grade 6. Use scripts/seed-history6.ps1 -Mode Verify.'}
    Write-Output "Database: $database; mode: $Mode"
    $already=Scalar "SELECT COUNT(*) FROM app_settings WHERE setting_key='seed.history_6_12.v1'"
    if($Mode -eq 'Verify' -or ($Mode -eq 'Apply' -and $already -gt 0)){
        if($already -ne 1){throw 'Seed marker not found.'}
        Query @'
DECLARE @manifest nvarchar(max)=(SELECT setting_value FROM app_settings WHERE setting_key='seed.history_6_12.v1');
SELECT s.grade,c.id,c.title,c.status,COUNT(DISTINCT i.id) AS lectures,COUNT(q.id) AS questions,
 COUNT(DISTINCT CASE WHEN l.video_url LIKE 'https://www.youtube.com/watch?v=%' THEN l.id END) AS youtube_videos
FROM OPENJSON(@manifest) WITH(course_id int) m JOIN courses c ON c.id=m.course_id JOIN subjects s ON s.id=c.subject_id
JOIN course_items i ON i.course_id=c.id JOIN lectures l ON l.id=i.lecture_id
JOIN questions q ON q.topic_id=l.topic_id AND q.deleted=0 AND q.library_visible=1
GROUP BY s.grade,c.id,c.title,c.status ORDER BY s.grade;
IF (SELECT COUNT(*) FROM OPENJSON(@manifest))<>7 THROW 51000,'Seed manifest must contain seven courses.',1;
IF (SELECT COUNT(*) FROM OPENJSON(@manifest) WITH(course_id int) m JOIN courses c ON c.id=m.course_id WHERE c.status='PUBLISHED')<>7 THROW 51000,'Expected seven published courses.',1;
IF (SELECT COUNT(*) FROM OPENJSON(@manifest) WITH(course_id int) m JOIN course_items i ON i.course_id=m.course_id)<>21 THROW 51000,'Expected 21 lessons.',1;
IF (SELECT COUNT(*) FROM OPENJSON(@manifest) WITH(course_id int) m JOIN course_items i ON i.course_id=m.course_id JOIN lectures l ON l.id=i.lecture_id JOIN questions q ON q.topic_id=l.topic_id WHERE q.kind='MCQ' AND q.deleted=0 AND q.library_visible=1)<>105 THROW 51000,'Expected 105 accessible MCQs.',1;
'@
        Write-Output 'Verified existing seed. No data was changed.'
        return
    }
    if($Mode -eq 'Apply'){
        # The SQL Server service writes this backup in its configured backup folder.
        $backupDir=[string](Scalar "SELECT CONVERT(nvarchar(4000),SERVERPROPERTY('InstanceDefaultBackupPath'))")
        if(-not $backupDir){throw 'SQL Server backup folder unavailable.'}
        $backup=Join-Path $backupDir ($database+'-before-history-seed-'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)+'.bak')
        $cmd=$connection.CreateCommand();$cmd.CommandTimeout=180
        $cmd.CommandText="BACKUP DATABASE [$database] TO DISK=@path WITH COPY_ONLY,CHECKSUM; RESTORE VERIFYONLY FROM DISK=@path WITH CHECKSUM;"
        $null=$cmd.Parameters.Add('@path',[Data.SqlDbType]::NVarChar,4000);$cmd.Parameters['@path'].Value=$backup
        try{$null=$cmd.ExecuteNonQuery()}finally{$cmd.Dispose()}
        Write-Output "Verified backup: $backup"
    }
    $cmd=$connection.CreateCommand();$cmd.CommandTimeout=180
    $cmd.CommandText=[IO.File]::ReadAllText((Join-Path $root 'database\05-seed-history-courses.sql'),[Text.Encoding]::UTF8)
    $null=$cmd.Parameters.Add('@seed_json',[Data.SqlDbType]::NVarChar,-1);$cmd.Parameters['@seed_json'].Value=$json
    $null=$cmd.Parameters.Add('@commit',[Data.SqlDbType]::Bit);$cmd.Parameters['@commit'].Value=($Mode -eq 'Apply')
    $adapter=New-Object System.Data.SqlClient.SqlDataAdapter($cmd);$set=New-Object System.Data.DataSet
    try{$null=$adapter.Fill($set);foreach($table in $set.Tables){$table | Format-Table -AutoSize | Out-String -Width 220 | Write-Output}}
    finally{$adapter.Dispose();$cmd.Dispose();$set.Dispose()}
}finally{$connection.Dispose()}
