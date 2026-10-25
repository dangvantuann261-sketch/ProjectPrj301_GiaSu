/* Incremental SQL Server migration for content governance and role separation.
   Additive only: existing lectures, questions, curricula, attempts and answers are retained.
   Run as database administrator after a backup. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF OBJECT_ID('schema_migrations','U') IS NULL
    CREATE TABLE schema_migrations(version INT PRIMARY KEY, applied_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=11)
BEGIN
    /* Moderation by administrators: hide with a reason, never delete teacher work. */
    ALTER TABLE lectures ADD hidden BIT NOT NULL CONSTRAINT df_lecture_hidden DEFAULT 0;
    ALTER TABLE lectures ADD hidden_reason NVARCHAR(500) NULL;
    ALTER TABLE lectures ADD hidden_by INT NULL CONSTRAINT fk_lecture_hidden_by REFERENCES users(id);
    ALTER TABLE lectures ADD hidden_at DATETIME2 NULL;
    /* Provenance and versioning. */
    ALTER TABLE lectures ADD copied_from_id INT NULL CONSTRAINT fk_lecture_copied_from REFERENCES lectures(id);
    ALTER TABLE lectures ADD version INT NOT NULL CONSTRAINT df_lecture_version DEFAULT 1;
    ALTER TABLE lectures ADD root_id INT NULL;
    ALTER TABLE lectures ADD superseded BIT NOT NULL CONSTRAINT df_lecture_superseded DEFAULT 0;

    ALTER TABLE questions ADD hidden BIT NOT NULL CONSTRAINT df_question_hidden DEFAULT 0;
    ALTER TABLE questions ADD hidden_reason NVARCHAR(500) NULL;
    ALTER TABLE questions ADD hidden_by INT NULL CONSTRAINT fk_question_hidden_by REFERENCES users(id);
    ALTER TABLE questions ADD hidden_at DATETIME2 NULL;
    ALTER TABLE questions ADD copied_from_id INT NULL CONSTRAINT fk_question_copied_from REFERENCES questions(id);
    ALTER TABLE questions ADD version INT NOT NULL CONSTRAINT df_question_version DEFAULT 1;
    ALTER TABLE questions ADD root_id INT NULL;
    ALTER TABLE questions ADD superseded BIT NOT NULL CONSTRAINT df_question_superseded DEFAULT 0;

    ALTER TABLE audit_logs ADD detail NVARCHAR(500) NULL;

    /* Explicit practice scope: a course item only practises the questions attached to it. */
    CREATE TABLE course_item_questions(
        course_item_id INT NOT NULL,
        question_id INT NOT NULL,
        created_by INT NOT NULL REFERENCES users(id),
        created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        CONSTRAINT pk_item_question PRIMARY KEY(course_item_id,question_id),
        CONSTRAINT fk_item_question_item FOREIGN KEY(course_item_id) REFERENCES course_items(id),
        CONSTRAINT fk_item_question_question FOREIGN KEY(question_id) REFERENCES questions(id)
    );
    CREATE INDEX ix_item_question_question ON course_item_questions(question_id);

    /* Seed the explicit links from the previous topic-wide rule so published curricula keep working. */
    EXEC(N'INSERT INTO course_item_questions(course_item_id,question_id,created_by)
    SELECT i.id,q.id,c.created_by
    FROM course_items i
    JOIN lectures l ON l.id=i.lecture_id
    JOIN questions q ON q.topic_id=l.topic_id AND q.deleted=0
    JOIN courses c ON c.id=i.course_id
    WHERE NOT EXISTS(SELECT 1 FROM course_item_questions x WHERE x.course_item_id=i.id AND x.question_id=q.id)');

    EXEC(N'UPDATE lectures SET root_id=id WHERE root_id IS NULL');
    EXEC(N'UPDATE questions SET root_id=id WHERE root_id IS NULL');

    INSERT INTO schema_migrations(version) VALUES(11);
END;
COMMIT;
