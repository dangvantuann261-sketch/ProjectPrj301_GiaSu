/* Additive, repeatable. Back up before applying to the selected database. */
SET XACT_ABORT ON;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM schema_migrations WHERE version=10)
BEGIN
 CREATE TABLE tutor_conversations(
  user_id INT NOT NULL REFERENCES users(id), context_key VARCHAR(50) NOT NULL,
  messages NVARCHAR(MAX) NOT NULL DEFAULT N'[]' CHECK(ISJSON(messages)=1),
  lease_id VARCHAR(36) NULL, busy_until DATETIME2 NULL, updated_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT pk_tutor_conversations PRIMARY KEY(user_id,context_key));
 CREATE TABLE tutor_chat_archives(id INT IDENTITY PRIMARY KEY,user_id INT NOT NULL REFERENCES users(id),
  context_key VARCHAR(50) NOT NULL,messages NVARCHAR(MAX) NOT NULL,created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME());
 CREATE TABLE learning_notification_reads(user_id INT NOT NULL REFERENCES users(id),answer_id INT NOT NULL REFERENCES answers(id),
  revision INT NOT NULL,CONSTRAINT pk_learning_notification_reads PRIMARY KEY(user_id,answer_id,revision));
 ALTER TABLE attempts ADD review_mode BIT NOT NULL CONSTRAINT df_attempt_review_mode DEFAULT 0;
 INSERT INTO schema_migrations(version) VALUES(10);
END;
COMMIT;
