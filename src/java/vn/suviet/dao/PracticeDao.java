package vn.suviet.dao;
import java.sql.*;
import java.util.*;
import vn.suviet.model.Question;
import vn.suviet.model.User;
import vn.suviet.service.*;
import vn.suviet.util.*;
public final class PracticeDao {
    public int start(int uid,int topicId,String kind)throws SQLException{
        var item=Sql.one("SELECT TOP 1 i.id FROM course_items i JOIN lectures l ON l.id=i.lecture_id JOIN courses c ON c.id=i.course_id JOIN course_item_questions cq ON cq.course_item_id=i.id JOIN questions q ON q.id=cq.question_id WHERE l.topic_id=? AND l.deleted=0 AND l.hidden=0 AND q.deleted=0 AND q.hidden=0 AND q.kind=? AND c.status='PUBLISHED' ORDER BY c.id,i.position",topicId,kind);
        if(item==null)throw new IllegalArgumentException("Hãy chọn bài luyện tập trong chương trình học.");
        return startScoped(uid,Sql.intValue(item,"id"),false,kind);
    }
    public int startScoped(int uid,int itemId,boolean chapter,String kind)throws SQLException {
        if(!Set.of("MCQ","ESSAY").contains(kind))throw new IllegalArgumentException("Loại bài luyện tập không hợp lệ.");
        var item=new CourseDao().item(itemId);
        if(item==null)throw new IllegalArgumentException("Không tìm thấy bài học.");
        var account=new UserDao().byId(uid);if(account==null)throw new AccessDeniedException();
        int course=Sql.intValue(item,"course_id");
        var enrollment=new CourseService().active(new User(account),course);
        String section=(String)item.get("section_title");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                // Serialize starts per student/curriculum so repeated clicks resume one unfinished attempt.
                Sql.query(c,"SELECT id FROM enrollments WITH(UPDLOCK,HOLDLOCK) WHERE id=?",enrollment.get("id"));
                var unfinished=Sql.query(c,"SELECT TOP 1 id FROM attempts WHERE user_id=? AND course_id=? AND kind=? AND review_mode=0 AND current_pos<total AND "+(chapter?"course_item_id IS NULL AND chapter_title=?":"course_item_id=?")+" ORDER BY id DESC",uid,course,kind,chapter?section:itemId);
                if(!unfinished.isEmpty()){c.commit();return Sql.intValue(unfinished.get(0),"id");}
                var source=Sql.query(c,"SELECT DISTINCT q.* FROM questions q JOIN course_item_questions cq ON cq.question_id=q.id JOIN course_items i ON i.id=cq.course_item_id JOIN lectures l ON l.id=i.lecture_id WHERE q.deleted=0 AND q.hidden=0 AND l.deleted=0 AND l.hidden=0 AND q.kind=? AND i.course_id=? AND "+(chapter?"i.section_title=?":"i.id=?")+" ORDER BY q.id",kind,course,chapter?section:itemId);
                Collections.shuffle(source);
                int count=Math.min(source.size(),kind.equals("ESSAY")?1:chapter?10:5);
                if(count==0)throw new IllegalArgumentException("Bài học chưa có câu hỏi thuộc loại này.");
                int attempt=Sql.insert(c,"INSERT INTO attempts(user_id,topic_id,kind,total,current_pos,course_id,course_item_id,chapter_title,created_at) VALUES(?,?,?,?,0,?,?,?,SYSUTCDATETIME())",uid,source.get(0).get("topic_id"),kind,count,course,chapter?null:itemId,chapter?section:null);
                for(int i=0;i<count;i++){
                    Question q=Question.from(source.get(i));
                    Sql.insert(c,"INSERT INTO attempt_items(attempt_id,position,question_snapshot) VALUES(?,?,?)",attempt,i,q.snapshot());
                }
                c.commit();
                return attempt;
            }
            catch(Exception e){
                c.rollback();
                throw e;
            }
        }
    }
    public Map<String,Object> owned(int id,int uid)throws SQLException{
        return Sql.one("SELECT a.*,COALESCE(a.chapter_title,l.title,t.title) AS topic_title FROM attempts a JOIN topics t ON t.id=a.topic_id LEFT JOIN course_items ci ON ci.id=a.course_item_id LEFT JOIN lectures l ON l.id=ci.lecture_id WHERE a.id=? AND a.user_id=?",id,uid);
    }
    public Map<String,Object> item(int attempt,int position)throws SQLException{
        return Sql.one("SELECT * FROM attempt_items WHERE attempt_id=? AND position=?",attempt,position);
    }
    public Map<String,Object> answer(int id,int uid,boolean teacher)throws SQLException{
        return Sql.one("SELECT ans.*,a.user_id,a.kind,a.total,a.current_pos,i.question_snapshot,u.full_name,COALESCE(a.chapter_title,l.title,t.title) AS topic_title FROM answers ans JOIN attempts a ON a.id=ans.attempt_id JOIN attempt_items i ON i.attempt_id=a.id AND i.position=ans.position JOIN users u ON u.id=a.user_id JOIN topics t ON t.id=a.topic_id LEFT JOIN course_items ci ON ci.id=a.course_item_id LEFT JOIN lectures l ON l.id=ci.lecture_id WHERE ans.id=? AND (?=1 OR a.user_id=?)",id,teacher?1:0,uid);
    }
    public int save(int attempt,int uid,int pos,String chosen,Double score,String feedback,String transcript,String file,String mime)throws SQLException{
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                // Cập nhật lạc quan và unique(attempt_id,position) chống nộp trùng / nhiều tab.
                if(Sql.update(c,"UPDATE attempts SET current_pos=current_pos+1 WHERE id=? AND user_id=? AND current_pos=? AND current_pos<total",attempt,uid,pos)!=1)throw new IllegalArgumentException("Câu này đã được nộp ở một cửa sổ khác. Hãy mở lịch sử làm bài.");
                int id=Sql.insert(c,"INSERT INTO answers(attempt_id,position,chosen,score,feedback,transcript,upload_name,upload_mime,grading_status) VALUES(?,?,?,?,?,?,?,?,?)",attempt,pos,chosen,score,feedback,transcript,file,mime,score==null?"PENDING":file.isEmpty()?"RULE_GRADED":"AI_GRADED");
                if(!file.isEmpty())Sql.update(c,"INSERT INTO essay_tutoring(answer_id) VALUES(?)",id);
                Sql.update(c,"UPDATE attempts SET finished_at=CASE WHEN course_id IS NULL THEN CURRENT_TIMESTAMP ELSE SYSUTCDATETIME() END WHERE id=? AND current_pos=total",attempt);
                c.commit();
                return id;
            }
            catch(Exception e){
                c.rollback();
                throw e;
            }
        }
    }
    public List<Map<String,Object>> history(int uid,int page)throws SQLException{
        return Sql.query("SELECT a.*,COALESCE(a.chapter_title,l.title,t.title) AS topic_title,CASE WHEN a.course_id IS NULL THEN a.created_at ELSE CONVERT(datetime2,a.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') END AS created_local,u.full_name,(SELECT AVG(ans.score) FROM answers ans WHERE ans.attempt_id=a.id) AS score,(SELECT COUNT(*) FROM answers ans WHERE ans.attempt_id=a.id AND ans.score IS NULL) AS pending_grades FROM attempts a JOIN topics t ON t.id=a.topic_id JOIN users u ON u.id=a.user_id LEFT JOIN course_items ci ON ci.id=a.course_item_id LEFT JOIN lectures l ON l.id=ci.lecture_id WHERE (?=0 OR a.user_id=?) ORDER BY a.id DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY",uid,uid,(page-1)*10);
    }
    public List<Map<String,Object>> answers(int attempt)throws SQLException{
        return Sql.query("SELECT a.id,a.position,a.score,a.chosen,a.feedback,a.grading_status,e.phase AS tutoring_phase,e.revision FROM answers a LEFT JOIN essay_tutoring e ON e.answer_id=a.id WHERE a.attempt_id=? ORDER BY a.position",attempt);
    }
    public boolean claimGrade(int id,int uid)throws SQLException{
        return Sql.update("UPDATE answers SET grading_status='PROCESSING',grading_started_at=SYSUTCDATETIME() WHERE id=? AND score IS NULL AND (grading_status IN('PENDING','RETRY') OR (grading_status='PROCESSING' AND grading_started_at<DATEADD(MINUTE,-3,SYSUTCDATETIME()))) AND EXISTS(SELECT 1 FROM attempts a WHERE a.id=answers.attempt_id AND a.user_id=?)",id,uid)==1;
    }
    public void gradeResult(int id,Double score,String transcript,String feedback,String status)throws SQLException{
        Sql.update("UPDATE answers SET score=?,transcript=?,feedback=?,grading_status=? WHERE id=? AND grading_status='PROCESSING'",score,transcript,feedback,status,id);
    }
}
