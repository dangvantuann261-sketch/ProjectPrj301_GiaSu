/* Incremental migration after 04-courses.sql. Back up first; existing answers are retained. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=7)
BEGIN
    CREATE TABLE essay_tutoring(
        answer_id INT PRIMARY KEY REFERENCES answers(id), revision INT NOT NULL DEFAULT 1 CHECK(revision>0),
        phase VARCHAR(12) NOT NULL DEFAULT 'NEW' CHECK(phase IN('NEW','WAITING','RESUBMIT')),
        step_no INT NOT NULL DEFAULT 0 CHECK(step_no>=0), version INT NOT NULL DEFAULT 0,
        formula NVARCHAR(1000) NOT NULL DEFAULT N'', question NVARCHAR(1000) NOT NULL DEFAULT N'',
        history NVARCHAR(MAX) NOT NULL DEFAULT N'[]' CHECK(ISJSON(history)=1),
        lease_id VARCHAR(36) NULL, busy_until DATETIME2 NULL,
        updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
    );
    CREATE TABLE essay_answer_revisions(
        answer_id INT NOT NULL REFERENCES answers(id), revision INT NOT NULL,
        upload_name VARCHAR(100) NOT NULL, upload_mime VARCHAR(30) NOT NULL,
        score DECIMAL(5,2) NULL, feedback NVARCHAR(MAX) NOT NULL, transcript NVARCHAR(MAX) NOT NULL,
        grading_status VARCHAR(20) NOT NULL, tutoring_history NVARCHAR(MAX) NOT NULL,
        archived_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        PRIMARY KEY(answer_id,revision)
    );
    EXEC(N'INSERT INTO essay_tutoring(answer_id) SELECT ans.id FROM answers ans JOIN attempts a ON a.id=ans.attempt_id WHERE a.kind=''ESSAY'';');
    INSERT INTO schema_migrations(version) VALUES(7);
END;
COMMIT;
