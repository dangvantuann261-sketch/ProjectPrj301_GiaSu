package vn.suviet.service;

import java.sql.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.User;
import vn.suviet.util.DBContext;

public final class CourseService {
    private final CourseDao dao=new CourseDao();
    public void requireManager(User u,int course)throws SQLException {
        if(!dao.manages(u,course))throw new AccessDeniedException();
    }
    private Map<String,Object> lock(Connection c,int id)throws SQLException {
        var rows=Sql.query(c,"SELECT * FROM courses WITH(UPDLOCK,HOLDLOCK) WHERE id=?",id);
        if(rows.isEmpty())throw new IllegalArgumentException("Không tìm thấy khóa học.");
        return rows.get(0);
    }
    private void editable(Connection c,int id)throws SQLException {
        var course=lock(c,id);
        if(!"DRAFT".equals(course.get("status")) || !Sql.query(c,"SELECT id FROM enrollments WHERE course_id=?",id).isEmpty())
            throw new IllegalArgumentException("Chỉ sửa chương trình khi khóa ở bản nháp và chưa có ghi danh. Khóa đã có người học được giữ nguyên để bảo toàn tiến độ.");
    }
    private void authorized(Connection c,User u,int id)throws SQLException {
        if(!u.getRole().equals("ADMIN")&&(!u.getRole().equals("TEACHER")||Sql.query(c,"SELECT teacher_id FROM course_teachers WITH(HOLDLOCK) WHERE course_id=? AND teacher_id=?",id,u.getId()).isEmpty()))throw new AccessDeniedException();
    }
    public int save(User u,int id,int subject,String title,String summary,String objectives,String audience,String prerequisites,int duration,String mode)throws SQLException {
        if(!Set.of("ADMIN","TEACHER").contains(u.getRole()))throw new AccessDeniedException();
        if(id>0)requireManager(u,id);
        if(duration<1||duration>100000||!Set.of("DIRECT","APPROVAL").contains(mode))throw new IllegalArgumentException("Thời lượng hoặc hình thức ghi danh không hợp lệ.");
        if(Sql.one("SELECT id FROM subjects WHERE id=? AND deleted=0",subject)==null)throw new IllegalArgumentException("Môn học không hợp lệ.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                if(id==0){
                    id=Sql.insert(c,"INSERT INTO courses(subject_id,title,summary,objectives,audience,prerequisites,duration_minutes,enrollment_mode,created_by) VALUES(?,?,?,?,?,?,?,?,?)",subject,title,summary,objectives,audience,prerequisites,duration,mode,u.getId());
                    if(u.getRole().equals("TEACHER"))Sql.update(c,"INSERT INTO course_teachers(course_id,teacher_id) VALUES(?,?)",id,u.getId());
                }else{
                    editable(c,id);
                    authorized(c,u,id);
                    if(!Sql.query(c,"SELECT i.id FROM course_items i JOIN lectures x ON x.id=i.lecture_id JOIN topics t ON t.id=x.topic_id JOIN lessons l ON l.id=t.lesson_id WHERE i.course_id=? AND l.subject_id<>?",id,subject).isEmpty())throw new IllegalArgumentException("Bài giảng trong chương trình phải cùng môn với khóa học.");
                    Sql.update(c,"UPDATE courses SET subject_id=?,title=?,summary=?,objectives=?,audience=?,prerequisites=?,duration_minutes=?,enrollment_mode=? WHERE id=?",subject,title,summary,objectives,audience,prerequisites,duration,mode,id);
                }
                CourseDao.audit(c,u.getId(),"COURSE_SAVE",id); c.commit();return id;
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void item(User u,int course,String action,int item,int lecture,String section,int position,boolean preview,boolean required)throws SQLException {
        requireManager(u,course);
        if(u.getRole().equals("ADMIN"))throw new AccessDeniedException("Quản trị viên không trực tiếp sắp xếp chương, bài. Hãy phân công giáo viên phụ trách.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                editable(c,course);
                authorized(c,u,course);
                if(action.equals("remove")){
                    if(Sql.update(c,"DELETE FROM course_items WHERE id=? AND course_id=?",item,course)!=1)throw new IllegalArgumentException("Không tìm thấy mục học.");
                }else if(action.equals("item")){
                    if(position<1||position>10000)throw new IllegalArgumentException("Thứ tự phải từ 1 đến 10000.");
                    if(Sql.query(c,"SELECT x.id FROM lectures x WITH(UPDLOCK,HOLDLOCK) JOIN topics t ON t.id=x.topic_id JOIN lessons l ON l.id=t.lesson_id JOIN courses co ON co.subject_id=l.subject_id WHERE co.id=? AND x.id=? AND x.deleted=0 AND x.hidden=0 AND t.deleted=0 AND l.deleted=0 AND (?=1 OR x.created_by=? OR x.library_visible=1)",course,lecture,u.getRole().equals("ADMIN")?1:0,u.getId()).isEmpty())throw new AccessDeniedException();
                    if(item==0)Sql.insert(c,"INSERT INTO course_items(course_id,lecture_id,section_title,position,is_preview,required) VALUES(?,?,?,?,?,?)",course,lecture,section,position,preview,required);
                    else if(Sql.update(c,"UPDATE course_items SET section_title=?,position=?,is_preview=?,required=? WHERE id=? AND course_id=? AND lecture_id=?",section,position,preview,required,item,course,lecture)!=1)throw new IllegalArgumentException("Mục học không hợp lệ.");
                }else throw new IllegalArgumentException("Thao tác không hợp lệ.");
                CourseDao.audit(c,u.getId(),"COURSE_CURRICULUM",course);c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    /** Practice scope is explicit: only the questions the assigned teacher attaches to this item. */
    public void questions(User u,int course,int itemId,int[] ids)throws SQLException{
        requireManager(u,course);
        if(u.getRole().equals("ADMIN"))throw new AccessDeniedException("Quản trị viên không chọn câu hỏi luyện tập thay giáo viên.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                editable(c,course);
                authorized(c,u,course);
                var item=Sql.one(c,"SELECT i.id,i.course_id,l.topic_id FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.id=? AND l.deleted=0 AND l.hidden=0",itemId);
                if(item==null||Sql.intValue(item,"course_id")!=course)throw new IllegalArgumentException("Bài học không thuộc chương trình.");
                int topic=Sql.intValue(item,"topic_id");
                LinkedHashSet<Integer> unique=new LinkedHashSet<>();
                if(ids!=null)for(int q:ids)if(q>0)unique.add(q);
                if(unique.size()>100)throw new IllegalArgumentException("Tối đa 100 câu hỏi cho một bài học.");
                for(int q:unique)if(Sql.query(c,"SELECT id FROM questions WHERE id=? AND topic_id=? AND deleted=0 AND hidden=0 AND (created_by=? OR library_visible=1)",q,topic,u.getId()).isEmpty())
                    throw new IllegalArgumentException("Câu hỏi #"+q+" không thuộc bài học hoặc chưa được chia sẻ.");
                Sql.update(c,"DELETE FROM course_item_questions WHERE course_item_id=?",itemId);
                for(int q:unique)Sql.update(c,"INSERT INTO course_item_questions(course_item_id,question_id,created_by) VALUES(?,?,?)",itemId,q,u.getId());
                CourseDao.audit(c,u.getId(),"COURSE_ITEM_QUESTIONS",itemId);c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void teacher(User u,int course,int teacher,boolean remove)throws SQLException {
        if(!u.getRole().equals("ADMIN"))throw new AccessDeniedException();
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var row=lock(c,course);
                if(remove){
                    if("PUBLISHED".equals(row.get("status"))&&Sql.query(c,"SELECT teacher_id FROM course_teachers WHERE course_id=?",course).size()<=1)throw new IllegalArgumentException("Khóa xuất bản cần ít nhất một giáo viên.");
                    Sql.update(c,"DELETE FROM course_teachers WHERE course_id=? AND teacher_id=?",course,teacher);
                }else{
                    if(Sql.query(c,"SELECT id FROM users WHERE id=? AND role='TEACHER' AND deleted=0 AND locked=0",teacher).isEmpty())throw new IllegalArgumentException("Giáo viên không hợp lệ.");
                    if(Sql.query(c,"SELECT teacher_id FROM course_teachers WHERE course_id=? AND teacher_id=?",course,teacher).isEmpty())Sql.update(c,"INSERT INTO course_teachers(course_id,teacher_id) VALUES(?,?)",course,teacher);
                }
                CourseDao.audit(c,u.getId(),"COURSE_TEACHER",course);c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void status(User u,int course,String status)throws SQLException {
        requireManager(u,course);
        if(!Set.of("DRAFT","PUBLISHED","ARCHIVED").contains(status))throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                lock(c,course);
                authorized(c,u,course);
                if(status.equals("DRAFT")&&!Sql.query(c,"SELECT id FROM enrollments WHERE course_id=?",course).isEmpty())throw new IllegalArgumentException("Khóa đã có ghi danh không được chuyển về nháp.");
                if(status.equals("PUBLISHED")){
                    if(Sql.query(c,"SELECT i.id FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.course_id=? AND i.required=1 AND l.deleted=0 AND l.hidden=0",course).isEmpty())throw new IllegalArgumentException("Cần ít nhất một bài giảng bắt buộc đang hiển thị.");
                    if(Sql.query(c,"SELECT ct.teacher_id FROM course_teachers ct JOIN users u ON u.id=ct.teacher_id WHERE ct.course_id=? AND u.deleted=0 AND u.locked=0 AND u.role='TEACHER'",course).isEmpty())throw new IllegalArgumentException("Cần phân công giáo viên đang hoạt động trước khi xuất bản.");
                    if(!Sql.query(c,"SELECT i.id FROM course_items i JOIN lectures l ON l.id=i.lecture_id WHERE i.course_id=? AND l.deleted=1",course).isEmpty())throw new IllegalArgumentException("Chương trình chứa bài giảng đã xóa.");
                }
                Sql.update(c,"UPDATE courses SET status=? WHERE id=?",status,course);
                CourseDao.audit(c,u.getId(),"COURSE_"+status,course);c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void enroll(User u,int course)throws SQLException {
        if(!u.getRole().equals("STUDENT"))throw new AccessDeniedException();
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var row=lock(c,course);
                if(!"PUBLISHED".equals(row.get("status")))throw new IllegalArgumentException("Khóa học hiện không nhận đăng ký.");
                if(Sql.query(c,"SELECT id FROM enrollments WHERE course_id=? AND student_id=?",course,u.getId()).isEmpty()){
                    Sql.insert(c,"INSERT INTO enrollments(course_id,student_id,status) VALUES(?,?,?)",course,u.getId(),"DIRECT".equals(row.get("enrollment_mode"))?"ACTIVE":"PENDING");
                    CourseDao.audit(c,u.getId(),"ENROLL",course);
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void decide(User u,int course,int enrollment,String status)throws SQLException {
        requireManager(u,course);
        if(!Set.of("ACTIVE","REJECTED","REVOKED").contains(status))throw new IllegalArgumentException("Trạng thái không hợp lệ.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                lock(c,course);
                authorized(c,u,course);
                String condition=status.equals("REJECTED")?"status='PENDING'":status.equals("REVOKED")?"status='ACTIVE'":"status IN('PENDING','REJECTED','REVOKED')";
                if(Sql.update(c,"UPDATE enrollments SET status=? WHERE id=? AND course_id=? AND "+condition,status,enrollment,course)!=1)throw new IllegalArgumentException("Ghi danh đã thay đổi hoặc thao tác không hợp lệ.");
                CourseDao.audit(c,u.getId(),"ENROLLMENT_"+status,enrollment);c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public Map<String,Object> active(User u,int course)throws SQLException {
        if(u==null||!u.getRole().equals("STUDENT"))throw new AccessDeniedException();
        // Enrollment rows now serve only as progress identities. Published curricula open directly.
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var curriculum=lock(c,course);
                if(Sql.query(c,"SELECT id FROM subjects WHERE id=? AND deleted=0",curriculum.get("subject_id")).isEmpty())throw new AccessDeniedException();
                var rows=Sql.query(c,"SELECT * FROM enrollments WITH(UPDLOCK,HOLDLOCK) WHERE course_id=? AND student_id=?",course,u.getId());
                if("PUBLISHED".equals(curriculum.get("status"))){
                    if(rows.isEmpty())Sql.insert(c,"INSERT INTO enrollments(course_id,student_id,status) VALUES(?,?,'ACTIVE')",course,u.getId());
                    else if(!"ACTIVE".equals(rows.get(0).get("status")))Sql.update(c,"UPDATE enrollments SET status='ACTIVE' WHERE id=?",rows.get(0).get("id"));
                }else if(!"ARCHIVED".equals(curriculum.get("status"))||rows.isEmpty()||!"ACTIVE".equals(rows.get(0).get("status")))throw new AccessDeniedException();
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
        return dao.enrollment(course,u.getId());
    }
    public void learn(User u,int item,String action,String note)throws SQLException {
        var i=dao.item(item);if(i==null)throw new IllegalArgumentException("Không tìm thấy bài giảng.");
        int course=Sql.intValue(i,"course_id");
        active(u,course);
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var es=Sql.query(c,"SELECT id FROM enrollments WITH(UPDLOCK,HOLDLOCK) WHERE course_id=? AND student_id=? AND status='ACTIVE'",course,u.getId());
                if(es.isEmpty()||!u.getRole().equals("STUDENT"))throw new AccessDeniedException();
                int eid=Sql.intValue(es.get(0),"id");
                if(Sql.query(c,"SELECT enrollment_id FROM learning_records WHERE enrollment_id=? AND course_item_id=?",eid,item).isEmpty())Sql.update(c,"INSERT INTO learning_records(enrollment_id,course_item_id,course_id) VALUES(?,?,?)",eid,item,course);
                switch(action){
                    case "view" -> {
                        Sql.update(c,"UPDATE learning_records SET last_viewed_at=SYSUTCDATETIME() WHERE enrollment_id=? AND course_item_id=?",eid,item);
                        // Reloading a lesson within five minutes updates recency without flooding its history.
                        Sql.update(c,"INSERT INTO learning_activity(enrollment_id,course_item_id,course_id,action) SELECT ?,?,?,'VIEW' WHERE NOT EXISTS(SELECT 1 FROM learning_activity WHERE enrollment_id=? AND course_item_id=? AND action='VIEW' AND occurred_at>DATEADD(MINUTE,-5,SYSUTCDATETIME()))",eid,item,course,eid,item);
                    }
                    case "complete" -> {
                        if(Sql.update(c,"UPDATE learning_records SET completed=1 WHERE enrollment_id=? AND course_item_id=? AND completed=0",eid,item)==1)
                            Sql.update(c,"INSERT INTO learning_activity(enrollment_id,course_item_id,course_id,action) VALUES(?,?,?,'COMPLETE')",eid,item,course);
                    }
                    case "bookmark" -> Sql.update(c,"UPDATE learning_records SET bookmarked=CASE WHEN bookmarked=1 THEN 0 ELSE 1 END WHERE enrollment_id=? AND course_item_id=?",eid,item);
                    case "note" -> { if(note==null||note.length()>8000)throw new IllegalArgumentException("Ghi chú tối đa 8000 ký tự.");Sql.update(c,"UPDATE learning_records SET note=? WHERE enrollment_id=? AND course_item_id=?",note,eid,item); }
                    default -> throw new IllegalArgumentException("Thao tác không hợp lệ.");
                }
                Sql.update(c,"UPDATE enrollments SET completed_at=COALESCE(completed_at,SYSUTCDATETIME()) WHERE id=? AND EXISTS(SELECT 1 FROM course_items WHERE course_id=? AND required=1) AND NOT EXISTS(SELECT 1 FROM course_items i WHERE i.course_id=? AND i.required=1 AND NOT EXISTS(SELECT 1 FROM learning_records r WHERE r.course_item_id=i.id AND r.enrollment_id=? AND r.completed=1))",eid,course,course,eid);
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public static int progress(int done,int total){ return total<=0?0:(int)Math.min(100,100L*Math.max(0,done)/total); }
    public static boolean flag(Object value){ return Boolean.TRUE.equals(value)||value instanceof Number n&&n.intValue()!=0; }
    public int resume(int course,int enrollment)throws SQLException {
        var rows=dao.items(course,enrollment);
        return rows.stream().filter(r->!flag(r.get("completed")))
            .max(Comparator.comparing((Map<String,Object> r)->(Timestamp)r.get("last_viewed_at"),Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(r->-Sql.intValue(r,"position")))
            .or(()->rows.stream().findFirst()).map(r->Sql.intValue(r,"id")).orElse(0);
    }
}
