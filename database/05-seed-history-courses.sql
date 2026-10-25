/* Called by scripts/seed-history-courses.ps1 with @seed_json (nvarchar(max))
   and @commit (bit). Additive seed, not a schema migration. No GO batches.
   The marker, all content, and audit rows are committed atomically. */
SET NOCOUNT ON;
SET XACT_ABORT ON;
IF ISJSON(@seed_json) <> 1 THROW 51000, 'Invalid seed JSON.', 1;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=4)
    THROW 51000, 'Apply schema migration 04 first.', 1;
DECLARE @key varchar(60)=JSON_VALUE(@seed_json,'$.seed_key');
IF @key IS NULL OR @key<>'seed.history_6_12.v1' THROW 51000, 'Unexpected seed key.', 1;
BEGIN TRY
 BEGIN TRANSACTION;
 DECLARE @lock int;
 EXEC @lock=sys.sp_getapplock @Resource='seed.history_6_12.v1',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=10000;
 IF @lock<0 THROW 51000, 'Could not acquire seed lock.', 1;
 IF EXISTS(SELECT 1 FROM app_settings WHERE setting_key=@key)
 BEGIN
    SELECT 'ALREADY_APPLIED' AS result,setting_value AS manifest FROM app_settings WHERE setting_key=@key;
    COMMIT;
    RETURN;
 END;
 DECLARE @admin int=(SELECT TOP 1 id FROM users WHERE role='ADMIN' AND deleted=0 AND locked=0 ORDER BY id);
 DECLARE @teacher int=(SELECT TOP 1 id FROM users WHERE role='TEACHER' AND deleted=0 AND locked=0 ORDER BY id);
 IF @admin IS NULL OR @teacher IS NULL THROW 51000, 'An active admin and teacher are required.', 1;
 DECLARE @created TABLE(grade int PRIMARY KEY,course_id int NOT NULL);
 DECLARE @newTopics TABLE(id int PRIMARY KEY);
 DECLARE @courseJson nvarchar(max),@lessonJson nvarchar(max),@grade int,@course int,@subject int,@lesson int,@topic int,@lecture int,@position int;
 DECLARE course_cursor CURSOR LOCAL FAST_FORWARD FOR SELECT value FROM OPENJSON(@seed_json,'$.courses');
 OPEN course_cursor;
 FETCH NEXT FROM course_cursor INTO @courseJson;
 WHILE @@FETCH_STATUS=0
 BEGIN
    SET @grade=TRY_CONVERT(int,JSON_VALUE(@courseJson,'$.grade'));
    SET @subject=(SELECT TOP 1 id FROM subjects WHERE name=N'Lịch sử' AND grade=@grade AND deleted=0 ORDER BY id);
    IF @subject IS NULL
    BEGIN
        INSERT subjects(name,grade,description,created_by)
        VALUES(N'Lịch sử',@grade,N'Lịch sử Việt Nam và thế giới; bài học và câu hỏi luyện tập theo khối lớp.',@admin);
        SET @subject=CONVERT(int,SCOPE_IDENTITY());
    END;
    INSERT courses(subject_id,title,summary,objectives,audience,prerequisites,duration_minutes,enrollment_mode,status,created_by)
    VALUES(@subject,JSON_VALUE(@courseJson,'$.title'),
        JSON_VALUE(@courseJson,'$.summary')+N' Khóa học gồm 3 bài tiêu biểu và 15 câu trắc nghiệm; bộ nội dung học thử, không thay thế chương trình cả năm.',
        JSON_VALUE(@courseJson,'$.objectives'),N'Học sinh lớp '+CONVERT(nvarchar(2),@grade)+N' và người muốn ôn tập kiến thức tương ứng.',
        CASE WHEN @grade=6 THEN N'Không yêu cầu kiến thức chuyên sâu.' ELSE N'Có kiến thức lịch sử cơ bản ở các lớp trước.' END,
        135,'DIRECT','PUBLISHED',@admin);
    SET @course=CONVERT(int,SCOPE_IDENTITY());
    INSERT @created VALUES(@grade,@course);
    INSERT course_teachers(course_id,teacher_id) VALUES(@course,@teacher);
    INSERT audit_logs(actor_id,action,resource_id) VALUES(@admin,'SEED_HISTORY_COURSE',@course);
    DECLARE lesson_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT CONVERT(int,[key])+1,value FROM OPENJSON(@courseJson,'$.lessons') ORDER BY CONVERT(int,[key]);
    OPEN lesson_cursor;
    FETCH NEXT FROM lesson_cursor INTO @position,@lessonJson;
    WHILE @@FETCH_STATUS=0
    BEGIN
        INSERT lessons(subject_id,title,description,created_by)
        VALUES(@subject,JSON_VALUE(@lessonJson,'$.title'),N'Bài học trong khóa '+JSON_VALUE(@courseJson,'$.title'),@admin);
        SET @lesson=CONVERT(int,SCOPE_IDENTITY());
        INSERT topics(lesson_id,title,description,created_by)
        VALUES(@lesson,JSON_VALUE(@lessonJson,'$.title'),N'Đọc bài, mở nguồn học liệu và làm 5 câu trắc nghiệm có giải thích.',@admin);
        SET @topic=CONVERT(int,SCOPE_IDENTITY());
        INSERT @newTopics VALUES(@topic);
        INSERT lectures(topic_id,title,description,body,video_url,created_by,library_visible)
        VALUES(@topic,JSON_VALUE(@lessonJson,'$.title'),N'Bài đọc tự biên soạn; nguồn tham khảo: VietJack / Kết nối tri thức.',
            JSON_VALUE(@lessonJson,'$.body')+NCHAR(10)+NCHAR(10)+N'NGUỒN HỌC LIỆU'+NCHAR(10)
                +JSON_VALUE(@lessonJson,'$.source.source_title')+NCHAR(10)+JSON_VALUE(@lessonJson,'$.source.source_url')
                +NCHAR(10)+N'Bài đọc và câu hỏi được tự biên soạn cho khóa học. Video và tài liệu liên kết thuộc đơn vị cung cấp nguồn.',
            COALESCE(NULLIF(JSON_VALUE(@lessonJson,'$.source.video_url'),N''),JSON_VALUE(@lessonJson,'$.source.source_url')),@admin,1);
        SET @lecture=CONVERT(int,SCOPE_IDENTITY());
        INSERT course_items(course_id,lecture_id,section_title,position,is_preview,required)
        VALUES(@course,@lecture,JSON_VALUE(@lessonJson,'$.section'),@position,CASE WHEN @position=1 THEN 1 ELSE 0 END,1);
        INSERT questions(topic_id,kind,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty,created_by,library_visible)
        SELECT @topic,'MCQ',JSON_VALUE(value,'$.prompt'),JSON_VALUE(value,'$.options[0]'),JSON_VALUE(value,'$.options[1]'),
            JSON_VALUE(value,'$.options[2]'),JSON_VALUE(value,'$.options[3]'),JSON_VALUE(value,'$.answer'),
            JSON_VALUE(value,'$.explanation'),JSON_VALUE(value,'$.difficulty'),@admin,1
        FROM OPENJSON(@lessonJson,'$.questions');
        FETCH NEXT FROM lesson_cursor INTO @position,@lessonJson;
    END;
    CLOSE lesson_cursor;
    DEALLOCATE lesson_cursor;
    FETCH NEXT FROM course_cursor INTO @courseJson;
 END;
 CLOSE course_cursor;
 DEALLOCATE course_cursor;
 IF (SELECT COUNT(*) FROM @created)<>7 OR (SELECT MIN(grade) FROM @created)<>6 OR (SELECT MAX(grade) FROM @created)<>12
    THROW 51000, 'Expected seven courses for grades 6 through 12.', 1;
 IF (SELECT COUNT(*) FROM @newTopics)<>21 THROW 51000, 'Expected 21 topics.', 1;
 IF EXISTS(SELECT t.id FROM @newTopics t LEFT JOIN questions q ON q.topic_id=t.id AND q.kind='MCQ' AND q.deleted=0 AND q.library_visible=1 GROUP BY t.id HAVING COUNT(q.id)<>5)
    THROW 51000, 'Each topic must have five accessible MCQs.', 1;
 IF EXISTS(SELECT c.course_id FROM @created c LEFT JOIN course_items i ON i.course_id=c.course_id GROUP BY c.course_id HAVING COUNT(i.id)<>3 OR SUM(CONVERT(int,i.is_preview))<>1)
    THROW 51000, 'Each course must have three lessons and one preview.', 1;
 IF EXISTS(SELECT 1 FROM @created c JOIN course_items i ON i.course_id=c.course_id JOIN lectures l ON l.id=i.lecture_id
    JOIN topics t ON t.id=l.topic_id JOIN lessons s ON s.id=t.lesson_id JOIN courses co ON co.id=c.course_id
    WHERE s.subject_id<>co.subject_id OR l.video_url NOT LIKE 'https://%' OR l.deleted<>0 OR t.deleted<>0 OR s.deleted<>0)
    THROW 51000, 'Invalid course/content/source link.', 1;
 DECLARE @manifest nvarchar(max)=(SELECT grade,course_id FROM @created ORDER BY grade FOR JSON PATH);
 INSERT app_settings(setting_key,setting_value) VALUES(@key,@manifest);
 SELECT CASE WHEN @commit=1 THEN 'APPLIED' ELSE 'DRY_RUN_ROLLED_BACK' END AS result,@manifest AS manifest;
 SELECT c.grade,c.course_id,co.title,COUNT(DISTINCT i.id) AS lectures,COUNT(q.id) AS questions
 FROM @created c JOIN courses co ON co.id=c.course_id JOIN course_items i ON i.course_id=c.course_id
 JOIN lectures l ON l.id=i.lecture_id JOIN questions q ON q.topic_id=l.topic_id
 GROUP BY c.grade,c.course_id,co.title ORDER BY c.grade;
 IF @commit=1 COMMIT ELSE ROLLBACK;
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK;
 THROW;
END CATCH;
