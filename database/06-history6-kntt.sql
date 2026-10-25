/* Grade 6 curriculum replacement. Run with scripts/seed-history6.ps1.
   @seed_json nvarchar(max), @commit bit. Existing attempts and progress are retained. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF ISJSON(@seed_json)<>1 OR ISNULL(JSON_VALUE(@seed_json,'$.seed_key'),'')<>'seed.history6.kntt.v1'
 THROW 51000,'Unexpected grade 6 content.',1;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=4) THROW 51000,'Apply migration 04 first.',1;
IF (SELECT COUNT(*) FROM OPENJSON(@seed_json,'$.lessons'))<>20 THROW 51000,'Expected 20 lessons.',1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource='seed.history6.kntt.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 51000,'Cannot acquire curriculum lock.',1;
 IF EXISTS(SELECT 1 FROM app_settings WHERE setting_key='seed.history6.kntt.v1')
 BEGIN
  SELECT 'ALREADY_APPLIED' result,setting_value manifest FROM app_settings WHERE setting_key='seed.history6.kntt.v1';
  COMMIT; RETURN;
 END;
 DECLARE @admin int=(SELECT TOP 1 id FROM users WHERE role='ADMIN' AND deleted=0 AND locked=0 ORDER BY id);
 DECLARE @teacher int=(SELECT TOP 1 id FROM users WHERE role='TEACHER' AND deleted=0 AND locked=0 ORDER BY id);
 IF @admin IS NULL OR @teacher IS NULL THROW 51000,'Active admin and teacher required.',1;
 DECLARE @retiredSubjects TABLE(id int PRIMARY KEY);
 INSERT @retiredSubjects SELECT id FROM subjects WHERE name=N'Lịch sử' AND grade BETWEEN 7 AND 12;
 DECLARE @retiredTopics TABLE(id int PRIMARY KEY);
 INSERT @retiredTopics SELECT t.id FROM topics t JOIN lessons l ON l.id=t.lesson_id JOIN @retiredSubjects s ON s.id=l.subject_id;
 UPDATE c SET status='ARCHIVED' FROM courses c JOIN @retiredSubjects s ON s.id=c.subject_id;
 UPDATE q SET deleted=1,library_visible=0 FROM questions q JOIN @retiredTopics t ON t.id=q.topic_id;
 UPDATE x SET deleted=1,library_visible=0 FROM lectures x JOIN @retiredTopics t ON t.id=x.topic_id;
 UPDATE t SET deleted=1 FROM topics t JOIN @retiredTopics r ON r.id=t.id;
 UPDATE l SET deleted=1 FROM lessons l JOIN @retiredSubjects s ON s.id=l.subject_id;
 UPDATE s SET deleted=1 FROM subjects s JOIN @retiredSubjects r ON r.id=s.id;
 /* Retire only the previous three-lesson grade 6 demo, not teacher-created content. */
 DECLARE @oldManifest nvarchar(max)=(SELECT setting_value FROM app_settings WHERE setting_key='seed.history_6_12.v1');
 DECLARE @old6 TABLE(id int PRIMARY KEY);
 IF ISJSON(@oldManifest)=1 INSERT @old6 SELECT course_id FROM OPENJSON(@oldManifest) WITH(grade int,course_id int) WHERE grade=6;
 UPDATE c SET status='ARCHIVED' FROM courses c JOIN @old6 o ON o.id=c.id;
 UPDATE x SET library_visible=0 FROM lectures x JOIN course_items i ON i.lecture_id=x.id JOIN @old6 o ON o.id=i.course_id;
 UPDATE q SET library_visible=0 FROM questions q WHERE EXISTS(SELECT 1 FROM lectures x JOIN course_items i ON i.lecture_id=x.id JOIN @old6 o ON o.id=i.course_id WHERE x.topic_id=q.topic_id);
 DECLARE @subject int=(SELECT TOP 1 id FROM subjects WHERE name=N'Lịch sử' AND grade=6 AND deleted=0 ORDER BY id);
 IF @subject IS NULL BEGIN
  INSERT subjects(name,grade,description,created_by) VALUES(N'Lịch sử',6,N'Lịch sử lớp 6',@admin);
  SET @subject=CONVERT(int,SCOPE_IDENTITY());
 END;
 INSERT courses(subject_id,title,summary,objectives,audience,prerequisites,duration_minutes,enrollment_mode,status,created_by)
 VALUES(@subject,JSON_VALUE(@seed_json,'$.title'),JSON_VALUE(@seed_json,'$.summary'),JSON_VALUE(@seed_json,'$.objectives'),
 N'Học sinh lớp 6; người muốn ôn kiến thức lịch sử nền tảng.',N'Không yêu cầu kiến thức chuyên sâu.',900,'DIRECT','PUBLISHED',@admin);
 DECLARE @course int=CONVERT(int,SCOPE_IDENTITY());
 INSERT course_teachers(course_id,teacher_id) VALUES(@course,@teacher);
 DECLARE @items TABLE(number int PRIMARY KEY,item_id int,lesson_id int,topic_id int,lecture_id int);
 DECLARE @lessonJson nvarchar(max),@n int,@lesson int,@topic int,@lecture int,@item int;
 DECLARE lesson_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT value FROM OPENJSON(@seed_json,'$.lessons') ORDER BY TRY_CONVERT(int,JSON_VALUE(value,'$.number'));
 OPEN lesson_cursor;FETCH NEXT FROM lesson_cursor INTO @lessonJson;
 WHILE @@FETCH_STATUS=0 BEGIN
  SET @n=TRY_CONVERT(int,JSON_VALUE(@lessonJson,'$.number'));
  IF @n NOT BETWEEN 1 AND 20 OR @n IS NULL THROW 51000,'Invalid lesson number.',1;
  IF (SELECT COUNT(*) FROM OPENJSON(@lessonJson,'$.questions'))<>5 THROW 51000,'Each lesson needs five questions.',1;
  INSERT lessons(subject_id,title,description,created_by) VALUES(@subject,JSON_VALUE(@lessonJson,'$.title'),JSON_VALUE(@lessonJson,'$.section'),@admin);
  SET @lesson=CONVERT(int,SCOPE_IDENTITY());
  INSERT topics(lesson_id,title,description,created_by) VALUES(@lesson,JSON_VALUE(@lessonJson,'$.title'),N'Xem video, đọc kiến thức cốt lõi và ôn tập 5 câu hỏi theo bài.',@admin);
  SET @topic=CONVERT(int,SCOPE_IDENTITY());
  INSERT lectures(topic_id,title,description,body,video_url,created_by,library_visible)
  VALUES(@topic,JSON_VALUE(@lessonJson,'$.title'),N'Tóm tắt và câu hỏi tự biên soạn theo mục lục SGK Kết nối tri thức. Video: '+JSON_VALUE(@lessonJson,'$.source.video_author'),
   JSON_VALUE(@lessonJson,'$.body')+NCHAR(10)+NCHAR(10)+N'[Nguồn bài học] '+JSON_VALUE(@lessonJson,'$.source.source_url'),
   JSON_VALUE(@lessonJson,'$.source.video_url'),@admin,1);
  SET @lecture=CONVERT(int,SCOPE_IDENTITY());
  INSERT course_items(course_id,lecture_id,section_title,position,is_preview,required)
  VALUES(@course,@lecture,JSON_VALUE(@lessonJson,'$.section'),@n,CASE WHEN @n=1 THEN 1 ELSE 0 END,1);
  SET @item=CONVERT(int,SCOPE_IDENTITY());
  INSERT @items VALUES(@n,@item,@lesson,@topic,@lecture);
  INSERT questions(topic_id,kind,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty,created_by,library_visible)
  SELECT @topic,'MCQ',JSON_VALUE(value,'$.prompt'),JSON_VALUE(value,'$.options[0]'),JSON_VALUE(value,'$.options[1]'),JSON_VALUE(value,'$.options[2]'),JSON_VALUE(value,'$.options[3]'),
   JSON_VALUE(value,'$.answer'),JSON_VALUE(value,'$.explanation'),JSON_VALUE(value,'$.difficulty'),@admin,1 FROM OPENJSON(@lessonJson,'$.questions');
  FETCH NEXT FROM lesson_cursor INTO @lessonJson;
 END;
 CLOSE lesson_cursor;DEALLOCATE lesson_cursor;
 IF (SELECT COUNT(*) FROM @items)<>20 THROW 51000,'Incomplete curriculum.',1;
 IF EXISTS(SELECT i.topic_id FROM @items i LEFT JOIN questions q ON q.topic_id=i.topic_id AND q.deleted=0 AND q.library_visible=1 GROUP BY i.topic_id HAVING COUNT(q.id)<>5)
  THROW 51000,'Question count mismatch.',1;
 IF (SELECT COUNT(DISTINCT x.video_url) FROM @items i JOIN lectures x ON x.id=i.lecture_id WHERE x.video_url LIKE 'https://www.youtube.com/watch?v=%')<>20
  THROW 51000,'Expected twenty distinct lesson videos.',1;
 IF EXISTS(SELECT 1 FROM subjects WHERE name=N'Lịch sử' AND grade BETWEEN 7 AND 12 AND deleted=0) THROW 51000,'Higher-grade subjects still active.',1;
 DECLARE @manifest nvarchar(max)=(SELECT @course course_id,(SELECT * FROM @items ORDER BY number FOR JSON PATH) items FOR JSON PATH,WITHOUT_ARRAY_WRAPPER);
 INSERT app_settings(setting_key,setting_value) VALUES('seed.history6.kntt.v1',@manifest);
 INSERT audit_logs(actor_id,action,resource_id) VALUES(@admin,'SEED_HISTORY6_KNTT',@course);
 INSERT audit_logs(actor_id,action,resource_id) SELECT @admin,'RETIRE_HISTORY_SUBJECT',id FROM @retiredSubjects;
 SELECT CASE WHEN @commit=1 THEN 'APPLIED' ELSE 'DRY_RUN_ROLLED_BACK' END result,@manifest manifest;
 IF @commit=1 COMMIT ELSE ROLLBACK;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
