/* Additive migration after 04. Existing progress is retained; no historical events are invented. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=8)
BEGIN
    CREATE TABLE learning_activity(
        id BIGINT IDENTITY PRIMARY KEY,
        enrollment_id INT NOT NULL, course_item_id INT NOT NULL, course_id INT NOT NULL,
        action VARCHAR(10) NOT NULL CHECK(action IN('VIEW','COMPLETE')),
        occurred_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
        FOREIGN KEY(enrollment_id,course_id) REFERENCES enrollments(id,course_id),
        FOREIGN KEY(course_item_id,course_id) REFERENCES course_items(id,course_id)
    );
    CREATE INDEX ix_learning_activity_enrollment ON learning_activity(enrollment_id,id DESC) INCLUDE(course_item_id,action,occurred_at);
    CREATE UNIQUE INDEX uq_learning_activity_completion ON learning_activity(enrollment_id,course_item_id) WHERE action='COMPLETE';
    INSERT INTO schema_migrations(version) VALUES(8);
END;
COMMIT;
