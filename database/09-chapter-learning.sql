/* Additive migration after 04, 07 and 08. No old attempts or progress are rewritten.
   Run with SSMS on the selected SuVietTutor database after taking a backup. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=9)
BEGIN
    ALTER TABLE attempts ADD course_id INT NULL, course_item_id INT NULL, chapter_title NVARCHAR(255) NULL;
    EXEC(N'ALTER TABLE attempts ADD CONSTRAINT fk_attempt_course FOREIGN KEY(course_id) REFERENCES courses(id),
        CONSTRAINT fk_attempt_item FOREIGN KEY(course_item_id,course_id) REFERENCES course_items(id,course_id),
        CONSTRAINT ck_attempt_scope CHECK(
          (course_id IS NULL AND course_item_id IS NULL AND chapter_title IS NULL) OR
          (course_id IS NOT NULL AND ((course_item_id IS NOT NULL AND chapter_title IS NULL) OR
           (course_item_id IS NULL AND chapter_title IS NOT NULL))));');
    EXEC(N'CREATE INDEX ix_attempt_learning ON attempts(user_id,course_id,course_item_id) INCLUDE(chapter_title,current_pos,total,kind);');
    INSERT INTO schema_migrations(version) VALUES(9);
END;
/* The original schema capped all attempts at five. Allow ten only for chapter MCQ.
   Discover the generated name of the original column-level total constraint. */
DECLARE @oldTotalCheck sysname, @dropCheck nvarchar(500);
SELECT @oldTotalCheck=name FROM sys.check_constraints
WHERE parent_object_id=OBJECT_ID('attempts') AND parent_column_id=COLUMNPROPERTY(OBJECT_ID('attempts'),'total','ColumnId');
IF @oldTotalCheck IS NOT NULL
BEGIN
    SET @dropCheck=N'ALTER TABLE attempts DROP CONSTRAINT '+QUOTENAME(@oldTotalCheck);
    EXEC(@dropCheck);
END;
IF NOT EXISTS(SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('attempts') AND name='ck_attempt_scope_total')
    EXEC(N'ALTER TABLE attempts ADD CONSTRAINT ck_attempt_scope_total CHECK(total BETWEEN 1 AND 5 OR (total BETWEEN 6 AND 10 AND kind=''MCQ'' AND course_id IS NOT NULL AND course_item_id IS NULL AND chapter_title IS NOT NULL));');
COMMIT;
