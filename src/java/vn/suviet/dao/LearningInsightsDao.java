package vn.suviet.dao;

import java.sql.*;
import java.util.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.DBContext;

public final class LearningInsightsDao {
    // Latest submitted response per question and curriculum; a successful retry clears the mistake.
    private static final String LATEST="WITH latest AS (SELECT ans.id,ans.score,ans.chosen,a.course_id,i.question_snapshot,TRY_CONVERT(INT,JSON_VALUE(i.question_snapshot,'$.topicId')) AS topic_id,ROW_NUMBER() OVER(PARTITION BY a.course_id,JSON_VALUE(i.question_snapshot,'$.id') ORDER BY ans.id DESC) AS rn FROM answers ans JOIN attempts a ON a.id=ans.attempt_id JOIN attempt_items i ON i.attempt_id=a.id AND i.position=ans.position WHERE a.user_id=? AND a.kind='MCQ' AND a.course_id=?) ";
    public List<Map<String,Object>> mistakes(int uid,int course)throws SQLException {
        var rows=Sql.query(LATEST+"SELECT x.*,l.title FROM latest x JOIN topics t ON t.id=x.topic_id JOIN lessons l ON l.id=t.lesson_id WHERE x.rn=1 AND x.score<10 ORDER BY x.id DESC",uid,course);
        for(var row:rows)row.put("prompt",Question.restore((String)row.get("question_snapshot")).getPrompt());
        return rows;
    }
    public List<Map<String,Object>> weakTopics(int uid,int course)throws SQLException {
        return Sql.query(LATEST+"SELECT t.title,COUNT(*) AS total,SUM(CASE WHEN x.score=10 THEN 1 ELSE 0 END) AS correct,AVG(x.score) AS score FROM latest x JOIN topics t ON t.id=x.topic_id WHERE x.rn=1 GROUP BY t.id,t.title HAVING AVG(x.score)<6 ORDER BY score,t.title",uid,course);
    }
    public List<Map<String,Object>> chapterScores(int uid,int course)throws SQLException {
        return Sql.query("SELECT ci.section_title,AVG(ans.score) AS score,COUNT(*) AS responses FROM answers ans JOIN attempts a ON a.id=ans.attempt_id JOIN attempt_items ai ON ai.attempt_id=a.id AND ai.position=ans.position CROSS APPLY(SELECT TOP 1 i.section_title FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.course_id=a.course_id AND l.topic_id=TRY_CONVERT(INT,JSON_VALUE(ai.question_snapshot,'$.topicId')) ORDER BY i.position) ci WHERE a.user_id=? AND a.course_id=? AND a.kind='MCQ' AND a.current_pos=a.total GROUP BY ci.section_title",uid,course);
    }
    public List<Map<String,Object>> trend(int uid,int course)throws SQLException {
        return Sql.query("SELECT TOP 20 a.id,CONVERT(datetime2,a.finished_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS finished_local,AVG(ans.score) AS score FROM attempts a JOIN answers ans ON ans.attempt_id=a.id WHERE a.user_id=? AND a.course_id=? AND a.kind='MCQ' AND a.current_pos=a.total GROUP BY a.id,a.finished_at HAVING COUNT(ans.score)=MAX(a.total) ORDER BY a.finished_at DESC,a.id DESC",uid,course);
    }
    public List<Map<String,Object>> notifications(int uid)throws SQLException {
        return Sql.query("SELECT TOP 10 ans.id,ans.score,ans.feedback,e.revision FROM answers ans JOIN attempts a ON a.id=ans.attempt_id JOIN essay_tutoring e ON e.answer_id=ans.id WHERE a.user_id=? AND ans.grading_status='AI_GRADED' AND NOT EXISTS(SELECT 1 FROM learning_notification_reads n WHERE n.user_id=? AND n.answer_id=ans.id AND n.revision=e.revision) ORDER BY ans.id DESC",uid,uid);
    }
    public void readNotification(int uid,int answer)throws SQLException {
        Sql.update("INSERT INTO learning_notification_reads(user_id,answer_id,revision) SELECT ?,ans.id,e.revision FROM answers ans JOIN attempts a ON a.id=ans.attempt_id JOIN essay_tutoring e ON e.answer_id=ans.id WHERE ans.id=? AND a.user_id=? AND ans.grading_status='AI_GRADED' AND NOT EXISTS(SELECT 1 FROM learning_notification_reads n WITH(UPDLOCK,HOLDLOCK) WHERE n.user_id=? AND n.answer_id=ans.id AND n.revision=e.revision)",uid,answer,uid,uid);
    }
    public int retry(User user,int course)throws SQLException {
        var enrollment=new CourseService().active(user,course);
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                Sql.query(c,"SELECT id FROM enrollments WITH(UPDLOCK,HOLDLOCK) WHERE id=?",enrollment.get("id"));
                var old=Sql.query(c,"SELECT TOP 1 id FROM attempts WHERE user_id=? AND course_id=? AND review_mode=1 AND current_pos<total ORDER BY id DESC",user.getId(),course);
                if(!old.isEmpty()){c.commit();return Sql.intValue(old.get(0),"id");}
                var wrong=Sql.query(c,LATEST+"SELECT TOP 5 * FROM latest WHERE rn=1 AND score<10 ORDER BY id DESC",user.getId(),course);
                if(wrong.isEmpty())throw new IllegalArgumentException("Em đã ôn đúng tất cả câu sai. Hãy tiếp tục bài học nhé.");
                int id=Sql.insert(c,"INSERT INTO attempts(user_id,topic_id,kind,total,current_pos,course_id,chapter_title,review_mode,created_at) VALUES(?,?,'MCQ',?,0,?,N'Sổ câu sai',1,SYSUTCDATETIME())",user.getId(),wrong.get(0).get("topic_id"),wrong.size(),course);
                for(int i=0;i<wrong.size();i++)Sql.insert(c,"INSERT INTO attempt_items(attempt_id,position,question_snapshot) VALUES(?,?,?)",id,i,wrong.get(i).get("question_snapshot"));
                c.commit();return id;
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
