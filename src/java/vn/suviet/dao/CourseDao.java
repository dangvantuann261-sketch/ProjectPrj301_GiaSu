package vn.suviet.dao;

import java.sql.*;
import java.util.*;
import vn.suviet.model.User;

public final class CourseDao {
    private static final String BASE="SELECT c.*,s.name AS subject_name,s.grade,u.full_name AS author_name,(SELECT COUNT(*) FROM course_items i WHERE i.course_id=c.id) AS item_count FROM courses c JOIN subjects s ON s.id=c.subject_id JOIN users u ON u.id=c.created_by ";
    public Map<String,Object> get(int id)throws SQLException { return Sql.one(BASE+"WHERE c.id=? AND s.deleted=0",id); }
    public boolean manages(User u,int course)throws SQLException {
        return u!=null && (u.getRole().equals("ADMIN") || u.getRole().equals("TEACHER") && Sql.one("SELECT course_id FROM course_teachers WHERE course_id=? AND teacher_id=?",course,u.getId())!=null);
    }
    public List<Map<String,Object>> catalog(String q,int subject,int grade,int teacher,int page)throws SQLException {
        return Sql.query(BASE+"WHERE c.status='PUBLISHED' AND s.deleted=0 AND c.title LIKE ? AND (?=0 OR c.subject_id=?) AND (?=0 OR s.grade=?) AND (?=0 OR EXISTS(SELECT 1 FROM course_teachers ct WHERE ct.course_id=c.id AND ct.teacher_id=?)) ORDER BY c.id DESC OFFSET ? ROWS FETCH NEXT 9 ROWS ONLY","%"+q+"%",subject,subject,grade,grade,teacher,teacher,(page-1)*9);
    }
    public int count(String q,int subject,int grade,int teacher)throws SQLException {
        return Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM courses c JOIN subjects s ON s.id=c.subject_id WHERE c.status='PUBLISHED' AND s.deleted=0 AND c.title LIKE ? AND (?=0 OR c.subject_id=?) AND (?=0 OR s.grade=?) AND (?=0 OR EXISTS(SELECT 1 FROM course_teachers ct WHERE ct.course_id=c.id AND ct.teacher_id=?))","%"+q+"%",subject,subject,grade,grade,teacher,teacher),"n");
    }
    /** Real totals shown on the public landing page; keeps promotional copy honest. */
    public Map<String,Object> landingStats()throws SQLException {
        return Sql.one("SELECT (SELECT COUNT(*) FROM courses c JOIN subjects s ON s.id=c.subject_id WHERE c.status='PUBLISHED' AND s.deleted=0) AS programs,"
            +"(SELECT COUNT(*) FROM course_items i JOIN courses c ON c.id=i.course_id JOIN subjects s ON s.id=c.subject_id WHERE c.status='PUBLISHED' AND s.deleted=0) AS lessons,"
            +"(SELECT COUNT(*) FROM questions q WHERE q.deleted=0) AS questions");
    }
    public List<Map<String,Object>> managed(User u)throws SQLException {
        return Sql.query(BASE+"WHERE s.deleted=0 AND (?=1 OR EXISTS(SELECT 1 FROM course_teachers ct WHERE ct.course_id=c.id AND ct.teacher_id=?)) ORDER BY c.id DESC",u.getRole().equals("ADMIN")?1:0,u.getId());
    }
    public List<Map<String,Object>> teachers(int course)throws SQLException {
        return Sql.query("SELECT u.id,u.full_name FROM users u JOIN course_teachers ct ON ct.teacher_id=u.id WHERE ct.course_id=? AND u.deleted=0",course);
    }
    public List<Map<String,Object>> teacherChoices()throws SQLException {
        return Sql.query("SELECT id,full_name FROM users WHERE role='TEACHER' AND deleted=0 AND locked=0 ORDER BY full_name");
    }
    public List<Map<String,Object>> items(int course,int enrollment)throws SQLException {
        return Sql.query("SELECT i.*,l.title,l.topic_id,(SELECT COUNT(*) FROM course_item_questions cq JOIN questions q ON q.id=cq.question_id WHERE cq.course_item_id=i.id AND q.deleted=0 AND q.hidden=0 AND q.kind='MCQ') AS question_count,(SELECT COUNT(*) FROM course_item_questions cq JOIN questions q ON q.id=cq.question_id WHERE cq.course_item_id=i.id AND q.deleted=0 AND q.hidden=0 AND q.kind='ESSAY') AS essay_count,COALESCE(r.completed,0) AS completed,COALESCE(r.bookmarked,0) AS bookmarked,r.last_viewed_at FROM course_items i JOIN lectures l ON l.id=i.lecture_id LEFT JOIN learning_records r ON r.course_item_id=i.id AND r.enrollment_id=? WHERE i.course_id=? AND l.deleted=0 AND l.hidden=0 ORDER BY i.position",enrollment,course);
    }
    public Map<String,Object> item(int id)throws SQLException {
        return Sql.one("SELECT i.*,l.title,l.body,l.video_url,l.topic_id FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.id=? AND l.deleted=0 AND l.hidden=0",id);
    }
    public List<Map<String,Object>> questionsForItem(int itemId,int uid)throws SQLException {
        return Sql.query("SELECT q.id,q.prompt,q.kind,q.difficulty,CASE WHEN q.created_by=? THEN 1 ELSE 0 END AS own,(SELECT COUNT(*) FROM course_item_questions cq WHERE cq.course_item_id=? AND cq.question_id=q.id) AS selected FROM questions q WHERE q.topic_id=(SELECT l.topic_id FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.id=?) AND q.deleted=0 AND q.hidden=0 AND (q.created_by=? OR q.library_visible=1) ORDER BY q.id",uid,itemId,itemId,uid);
    }
    public Map<String,Object> enrollment(int course,int uid)throws SQLException {
        return Sql.one("SELECT * FROM enrollments WHERE course_id=? AND student_id=?",course,uid);
    }
    public List<Map<String,Object>> mine(int uid)throws SQLException {
        return Sql.query("SELECT c.id,c.title,c.summary,c.status AS course_status,s.name AS subject_name,s.grade,e.id AS enrollment_id,e.status,e.completed_at,(SELECT COUNT(*) FROM course_items i WHERE i.course_id=c.id AND i.required=1) AS total,(SELECT COUNT(*) FROM learning_records r JOIN course_items i ON i.id=r.course_item_id WHERE r.enrollment_id=e.id AND r.completed=1 AND i.required=1) AS done FROM enrollments e JOIN courses c ON c.id=e.course_id JOIN subjects s ON s.id=c.subject_id WHERE e.student_id=? AND s.deleted=0 ORDER BY e.created_at DESC",uid);
    }
    public List<Map<String,Object>> students(int course)throws SQLException {
        return Sql.query("SELECT e.*,u.full_name,(SELECT COUNT(*) FROM learning_records r JOIN course_items i ON i.id=r.course_item_id WHERE r.enrollment_id=e.id AND r.completed=1 AND i.required=1) AS done,(SELECT COUNT(*) FROM course_items i WHERE i.course_id=e.course_id AND i.required=1) AS total FROM enrollments e JOIN users u ON u.id=e.student_id WHERE e.course_id=? ORDER BY e.created_at DESC",course);
    }
    public List<Map<String,Object>> availableLectures(User u,int subject)throws SQLException {
        return Sql.query("SELECT x.id,x.title,x.version,x.superseded FROM lectures x JOIN topics t ON t.id=x.topic_id JOIN lessons l ON l.id=t.lesson_id WHERE x.deleted=0 AND x.hidden=0 AND t.deleted=0 AND l.deleted=0 AND l.subject_id=? AND (?=1 OR x.created_by=? OR x.library_visible=1) ORDER BY x.id",subject,u.getRole().equals("ADMIN")?1:0,u.getId());
    }
    public Map<String,Object> record(int enrollment,int item)throws SQLException {
        return Sql.one("SELECT * FROM learning_records WHERE enrollment_id=? AND course_item_id=?",enrollment,item);
    }
    public static void audit(Connection c,int uid,String action,int id)throws SQLException {
        Sql.insert(c,"INSERT INTO audit_logs(actor_id,action,resource_id) VALUES(?,?,?)",uid,action,id);
    }
}
