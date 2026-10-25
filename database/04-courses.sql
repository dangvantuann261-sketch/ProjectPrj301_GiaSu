/* Incremental SQL Server migration. Back up first. Run as database administrator.
   Existing users, content and practice history are retained. No sample courses. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('schema_migrations','U') IS NULL
    CREATE TABLE schema_migrations(version INT PRIMARY KEY, applied_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=4)
BEGIN
    CREATE TABLE courses(
        id INT IDENTITY PRIMARY KEY, subject_id INT NOT NULL REFERENCES subjects(id),
        title NVARCHAR(255) NOT NULL, summary NVARCHAR(2000) NOT NULL,
        objectives NVARCHAR(4000) NOT NULL, audience NVARCHAR(2000) NOT NULL,
        prerequisites NVARCHAR(2000) NOT NULL, duration_minutes INT NOT NULL CHECK(duration_minutes BETWEEN 1 AND 100000),
        enrollment_mode VARCHAR(10) NOT NULL CHECK(enrollment_mode IN('DIRECT','APPROVAL')),
        status VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','PUBLISHED','ARCHIVED')),
        created_by INT NOT NULL REFERENCES users(id), created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE TABLE course_teachers(
        course_id INT NOT NULL REFERENCES courses(id), teacher_id INT NOT NULL REFERENCES users(id),
        PRIMARY KEY(course_id,teacher_id)
    );
    CREATE INDEX ix_course_teacher ON course_teachers(teacher_id,course_id);
    CREATE TABLE course_items(
        id INT IDENTITY PRIMARY KEY, course_id INT NOT NULL REFERENCES courses(id),
        lecture_id INT NOT NULL REFERENCES lectures(id), section_title NVARCHAR(255) NOT NULL,
        position INT NOT NULL CHECK(position>0), is_preview BIT NOT NULL DEFAULT 0, required BIT NOT NULL DEFAULT 1,
        CONSTRAINT uq_course_lecture UNIQUE(course_id,lecture_id),
        CONSTRAINT uq_course_position UNIQUE(course_id,position),
        CONSTRAINT uq_item_course UNIQUE(id,course_id)
    );
    CREATE TABLE enrollments(
        id INT IDENTITY PRIMARY KEY, course_id INT NOT NULL REFERENCES courses(id),
        student_id INT NOT NULL REFERENCES users(id),
        status VARCHAR(10) NOT NULL CHECK(status IN('PENDING','ACTIVE','REJECTED','REVOKED')),
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(), completed_at DATETIME2 NULL,
        CONSTRAINT uq_enrollment UNIQUE(course_id,student_id),
        CONSTRAINT uq_enrollment_course UNIQUE(id,course_id)
    );
    CREATE INDEX ix_enrollment_student ON enrollments(student_id,status);
    CREATE TABLE learning_records(
        enrollment_id INT NOT NULL, course_item_id INT NOT NULL, course_id INT NOT NULL,
        completed BIT NOT NULL DEFAULT 0, bookmarked BIT NOT NULL DEFAULT 0,
        note NVARCHAR(MAX) NOT NULL DEFAULT N'' CHECK(DATALENGTH(note)<=16000), last_viewed_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        PRIMARY KEY(enrollment_id,course_item_id),
        FOREIGN KEY(enrollment_id,course_id) REFERENCES enrollments(id,course_id),
        FOREIGN KEY(course_item_id,course_id) REFERENCES course_items(id,course_id)
    );
    CREATE TABLE learner_profiles(user_id INT PRIMARY KEY REFERENCES users(id), grade INT NULL CHECK(grade BETWEEN 6 AND 12));
    CREATE TABLE subject_follows(user_id INT NOT NULL REFERENCES users(id),subject_id INT NOT NULL REFERENCES subjects(id),PRIMARY KEY(user_id,subject_id));
    CREATE TABLE audit_logs(id BIGINT IDENTITY PRIMARY KEY,actor_id INT NOT NULL REFERENCES users(id),action VARCHAR(50) NOT NULL,resource_id INT NOT NULL,created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
    CREATE INDEX ix_courses_catalog ON courses(status,subject_id,id);
    /* Old library stays available to signed-in students; new content is private by default. */
    ALTER TABLE lectures ADD library_visible BIT NOT NULL CONSTRAINT df_lecture_library DEFAULT 0;
    ALTER TABLE questions ADD library_visible BIT NOT NULL CONSTRAINT df_question_library DEFAULT 0;
    EXEC(N'UPDATE lectures SET library_visible=1; UPDATE questions SET library_visible=1;');
    ALTER TABLE answers ALTER COLUMN score DECIMAL(5,2) NULL;
    ALTER TABLE answers ADD grading_status VARCHAR(20) NOT NULL CONSTRAINT df_answer_grading DEFAULT 'PENDING', grading_started_at DATETIME2 NULL;
    EXEC(N'UPDATE answers SET grading_status=CASE WHEN a.kind=''MCQ'' THEN ''RULE_GRADED'' ELSE ''AI_GRADED'' END FROM answers x JOIN attempts a ON a.id=x.attempt_id;');
    EXEC(N'ALTER TABLE answers ADD CONSTRAINT ck_grading_status CHECK(grading_status IN(''PENDING'',''PROCESSING'',''AI_GRADED'',''RULE_GRADED'',''RETRY'',''MANUAL_REQUIRED''));');
    INSERT INTO schema_migrations(version) VALUES(4);
END;
COMMIT;