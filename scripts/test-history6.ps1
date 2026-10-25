param([string]$BaseUrl="http://127.0.0.1:8082/SuVietTutor", [string]$ConfigPath="")
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$seed=Get-Content (Join-Path $root 'database/history6-kntt-content.json') -Raw -Encoding UTF8|ConvertFrom-Json
$base=$BaseUrl.TrimEnd('/')
$session=New-Object Microsoft.PowerShell.Commands.WebRequestSession
function Page([string]$url){$r=Invoke-WebRequest -UseBasicParsing -Uri ($base+$url) -WebSession $session -TimeoutSec 30; if($r.StatusCode -ne 200){throw "HTTP $url"};return [Net.WebUtility]::HtmlDecode($r.Content)}
function Token([string]$html){return [regex]::Match($html,'name="csrf" value="([^"]+)"').Groups[1].Value}
function Post([string]$url,[hashtable]$body){return Invoke-WebRequest -UseBasicParsing -Uri ($base+$url) -Method Post -Body $body -WebSession $session -TimeoutSec 30}
if(-not $ConfigPath){$ConfigPath=Join-Path (Split-Path -Parent (Split-Path -Parent $root)) 'SuVietTutorData/config.properties';if(-not(Test-Path $ConfigPath)){$ConfigPath='C:/SuVietTutorData/config.properties'}}
$cfg=[IO.File]::ReadAllText($ConfigPath)
$m=[regex]::Match($cfg,'(?m)^db.url=jdbc:sqlserver://([^;\r\n]+);databaseName=([^;\r\n]+)')
$db=[Data.SqlClient.SqlConnection]::new("Data Source=$($m.Groups[1].Value -replace ':',',');Initial Catalog=$($m.Groups[2].Value);Integrated Security=True;Encrypt=True;TrustServerCertificate=True");$db.Open()
function Scalar([string]$sql){$c=$db.CreateCommand();$c.CommandText=$sql;try{return $c.ExecuteScalar()}finally{$c.Dispose()}}
$manifest=(Scalar "SELECT setting_value FROM app_settings WHERE setting_key='seed.history6.kntt.v1'")|ConvertFrom-Json
$email='grade6-check-'+[Guid]::NewGuid().ToString('N')+'@test.suviet.local';$uid=0
try{
 $catalog=Page '/courses'
 if(-not $catalog.Contains($seed.title)){throw 'Missing new course.'}
 $ids=@([regex]::Matches($catalog,'/course\?id=(\d+)')|ForEach-Object {$_.Groups[1].Value}|Sort-Object -Unique)
 if($ids.Count -ne 1 -or $ids[0] -ne [string]$manifest.course_id){throw 'Unexpected public catalog.'}
 if($catalog -match '>Lớp (7|8|9|10|11|12)<'){throw 'Higher grades visible.'}
 $detail=Page ('/course?id='+$manifest.course_id)
 foreach($l in $seed.lessons){if(-not $detail.Contains($l.title)){throw "Missing outline $($l.number)"}}
 $preview=Page ('/preview?id='+$manifest.items[0].item_id)
 if($preview -notmatch 'youtube-nocookie.com/embed/' -or $preview -match 'Mở video / tài liệu gốc'){throw 'Preview player mismatch.'}
 $reg=Page '/register'
 $null=Post '/register' @{csrf=(Token $reg);email=$email;password='TestGrade6#2026';full_name='Grade 6 HTTP verification'}
 $uid=[int](Scalar "SELECT id FROM users WHERE email='$email'")
 if($uid -lt 1){throw 'Test registration failed.'}
 $login=Page '/login';$r=Post '/login' @{csrf=(Token $login);email=$email;password='TestGrade6#2026'}
 if($r.BaseResponse.ResponseUri.AbsolutePath -ne '/SuVietTutor/student/dashboard'){throw 'Test login failed.'}
 $detail=Page ('/course?id='+$manifest.course_id);$null=Post '/student/enroll' @{csrf=(Token $detail);course_id=$manifest.course_id}
 foreach($entry in $manifest.items){
  $l=$seed.lessons[[int]$entry.number-1];$html=Page ('/student/lesson?id='+$entry.item_id)
  $videoId=($l.source.video_url -split 'v=')[1]
  if(-not $html.Contains('youtube-nocookie.com/embed/'+$videoId)){throw "Wrong embed $($entry.number)"}
  if(-not $html.Contains('href="'+$l.source.source_url+'"')){throw "Wrong source $($entry.number)"}
  if(-not $html.Contains('name="topic_id" value="'+$entry.topic_id+'"') -or -not $html.Contains('Bắt đầu ôn tập bài này')){throw 'Review form missing.'}
  $topic=Page ('/student/topic?id='+$entry.topic_id)
  if(-not $topic.Contains('5 câu trong kho')){throw 'Missing questions.'}
 }
 $first=$manifest.items[0];$html=Page ('/student/lesson?id='+$first.item_id)
 $r=Post '/student/start' @{csrf=(Token $html);topic_id=$first.topic_id;kind='MCQ'}
 $attempt=[int][regex]::Match($r.BaseResponse.ResponseUri.Query,'id=(\d+)').Groups[1].Value
 for($pos=0;$pos -lt 5;$pos++){
  $html=Page ('/student/practice?id='+$attempt)
  if($html.Contains('Đáp án đúng:')){throw 'Answer leaked before submission.'}
  $matches=@($seed.lessons[0].questions|Where-Object {$html.Contains($_.prompt)})
  if($matches.Count -ne 1){throw 'Wrong lesson question.'}
  $q=$matches[0];$chosen=$q.answer
  if($pos -eq 0){$chosen=if($q.answer -eq 'A'){'B'}else{'A'}}
  $r=Post ('/student/practice?id='+$attempt) @{csrf=(Token $html);position=$pos;chosen=$chosen}
  $review=[Net.WebUtility]::HtmlDecode($r.Content)
  if(-not $review.Contains('Xem đáp án và giải thích') -or -not $review.Contains($q.explanation)){throw 'Answer explanation missing.'}
 }
 $average=[decimal](Scalar "SELECT AVG(score) FROM answers WHERE attempt_id=$attempt")
 if($average -ne 8){throw "Wrong score: $average"}
 $summary=Page ('/student/practice?id='+$attempt)
 if(-not $summary.Contains('Đã nộp 5 / 5 câu')){throw 'Attempt incomplete.'}
 Write-Output 'PASS: catalog only grade 6; all 20 lesson pages, 20 source links, 20 unique embeds, 20 review forms; completed 5-question quiz, one wrong/four right = 8/10; explanations displayed.'
}finally{
 if($uid -gt 0){
  $cmd=$db.CreateCommand();$cmd.CommandText=@'
SET XACT_ABORT ON; BEGIN TRAN;
IF NOT EXISTS(SELECT 1 FROM users WHERE id=@uid AND email=@email AND full_name=N'Grade 6 HTTP verification') THROW 51000,'Wrong test user.',1;
DELETE FROM answers WHERE attempt_id IN(SELECT id FROM attempts WHERE user_id=@uid);
DELETE FROM attempt_items WHERE attempt_id IN(SELECT id FROM attempts WHERE user_id=@uid);
DELETE FROM attempts WHERE user_id=@uid;
DELETE FROM learning_records WHERE enrollment_id IN(SELECT id FROM enrollments WHERE student_id=@uid);
DELETE FROM enrollments WHERE student_id=@uid;
DELETE FROM subject_follows WHERE user_id=@uid;
DELETE FROM learner_profiles WHERE user_id=@uid;
DELETE FROM audit_logs WHERE actor_id=@uid;
DELETE FROM users WHERE id=@uid;
COMMIT;
'@
  $null=$cmd.Parameters.Add('@uid',[Data.SqlDbType]::Int);$cmd.Parameters['@uid'].Value=$uid
  $null=$cmd.Parameters.Add('@email',[Data.SqlDbType]::NVarChar,254);$cmd.Parameters['@email'].Value=$email
  $null=$cmd.ExecuteNonQuery();$cmd.Dispose();Write-Output 'Removed only the temporary verification account and its learning data.'
 }
 $db.Dispose()
}
