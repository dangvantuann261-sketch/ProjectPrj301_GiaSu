/* Chạy một lần trong SSMS, chọn database SuVietTutor. Không xóa dữ liệu có sẵn. */

CREATE TABLE users (
 id INT IDENTITY(1,1) PRIMARY KEY,
 full_name NVARCHAR(100) NOT NULL,
 email NVARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(256) NOT NULL,
 role VARCHAR(10) NOT NULL CHECK(role IN ('ADMIN','TEACHER','STUDENT')),
 locked TINYINT NOT NULL DEFAULT 0 CHECK(locked IN(0,1)),
 deleted TINYINT NOT NULL DEFAULT 0 CHECK(deleted IN(0,1)),
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE subjects (
 id INT IDENTITY(1,1) PRIMARY KEY, name NVARCHAR(255) NOT NULL,
 grade INT NOT NULL CHECK(grade BETWEEN 6 AND 12), description NVARCHAR(2000) NOT NULL DEFAULT N'',
 created_by INT NOT NULL REFERENCES users(id), deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE lessons (
 id INT IDENTITY(1,1) PRIMARY KEY,subject_id INT NOT NULL REFERENCES subjects(id),
 title NVARCHAR(255) NOT NULL,description NVARCHAR(2000) NOT NULL DEFAULT N'',
 created_by INT NOT NULL REFERENCES users(id),deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE topics (
 id INT IDENTITY(1,1) PRIMARY KEY,lesson_id INT NOT NULL REFERENCES lessons(id),
 title NVARCHAR(255) NOT NULL,description NVARCHAR(2000) NOT NULL DEFAULT N'',
 created_by INT NOT NULL REFERENCES users(id),deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE lectures (
 id INT IDENTITY(1,1) PRIMARY KEY,topic_id INT NOT NULL REFERENCES topics(id),
 title NVARCHAR(255) NOT NULL,description NVARCHAR(2000) NOT NULL DEFAULT N'',
 body NVARCHAR(MAX) NOT NULL,video_url NVARCHAR(2000) NOT NULL DEFAULT N'',
 created_by INT NOT NULL REFERENCES users(id),deleted TINYINT NOT NULL DEFAULT 0
);
CREATE TABLE questions (
 id INT IDENTITY(1,1) PRIMARY KEY,topic_id INT NOT NULL REFERENCES topics(id),
 kind VARCHAR(10) NOT NULL CHECK(kind IN('MCQ','ESSAY')),prompt NVARCHAR(4000) NOT NULL,
 option_a NVARCHAR(1000) NOT NULL DEFAULT N'',option_b NVARCHAR(1000) NOT NULL DEFAULT N'',
 option_c NVARCHAR(1000) NOT NULL DEFAULT N'',option_d NVARCHAR(1000) NOT NULL DEFAULT N'',
 correct_option VARCHAR(1) NOT NULL DEFAULT '',explanation NVARCHAR(MAX) NOT NULL,
 difficulty VARCHAR(10) NOT NULL CHECK(difficulty IN('EASY','MEDIUM','HARD')),
 created_by INT NOT NULL REFERENCES users(id),deleted TINYINT NOT NULL DEFAULT 0,
 CONSTRAINT ck_mcq CHECK(kind='ESSAY' OR (correct_option IN('A','B','C','D') AND LEN(option_a)>0 AND LEN(option_b)>0 AND LEN(option_c)>0 AND LEN(option_d)>0))
);
CREATE TABLE attempts (
 id INT IDENTITY(1,1) PRIMARY KEY,user_id INT NOT NULL REFERENCES users(id),
 topic_id INT NOT NULL REFERENCES topics(id),kind VARCHAR(10) NOT NULL CHECK(kind IN('MCQ','ESSAY')),
 total INT NOT NULL CHECK(total BETWEEN 1 AND 5),current_pos INT NOT NULL DEFAULT 0,
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,finished_at DATETIME2 NULL,
 CONSTRAINT ck_attempt_progress CHECK(current_pos>=0 AND current_pos<=total)
);
CREATE TABLE attempt_items (
 id INT IDENTITY(1,1) PRIMARY KEY,attempt_id INT NOT NULL REFERENCES attempts(id),
 position INT NOT NULL,question_snapshot NVARCHAR(MAX) NOT NULL,
 CONSTRAINT uq_attempt_position UNIQUE(attempt_id,position)
);
CREATE TABLE answers (
 id INT IDENTITY(1,1) PRIMARY KEY,attempt_id INT NOT NULL,
 position INT NOT NULL,chosen VARCHAR(1) NOT NULL DEFAULT '',score DECIMAL(5,2) NOT NULL CHECK(score BETWEEN 0 AND 10),
 feedback NVARCHAR(MAX) NOT NULL,transcript NVARCHAR(MAX) NOT NULL,
 upload_name VARCHAR(100) NOT NULL DEFAULT '',upload_mime VARCHAR(30) NOT NULL DEFAULT '',
 created_at DATETIME2 NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uq_answer UNIQUE(attempt_id,position),
 CONSTRAINT fk_answer_item FOREIGN KEY(attempt_id,position) REFERENCES attempt_items(attempt_id,position)
);
CREATE TABLE app_settings (setting_key VARCHAR(60) PRIMARY KEY,setting_value NVARCHAR(MAX) NOT NULL);
CREATE INDEX ix_questions_topic ON questions(topic_id,kind,deleted);
CREATE INDEX ix_attempts_user ON attempts(user_id,created_at);
CREATE INDEX ix_topics_lesson ON topics(lesson_id,deleted);
CREATE INDEX ix_lectures_topic ON lectures(topic_id,deleted);
