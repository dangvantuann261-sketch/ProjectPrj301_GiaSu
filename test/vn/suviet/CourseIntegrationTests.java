package vn.suviet;

import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.*;

/** Opt-in integration tests. Runs only on the isolated UpgradeTest database. */
public final class CourseIntegrationTests {
    private static int checks;
    private static final CourseService service=new CourseService();
    private static final CourseDao dao=new CourseDao();
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
    private interface Action {void run()throws Exception;}
    private static void denied(Action action,String message)throws Exception {boolean failed=false;try{action.run();}catch(AccessDeniedException|IllegalArgumentException e){failed=true;}check(failed,message);}
    private static User user(String role,String tag)throws Exception {
        int id=new UserDao().create("Kiểm thử "+role,tag+role+"@integration.invalid",UUID.randomUUID().toString(),role);
        return new User(new UserDao().byId(id));
    }
    public static void main(String[] args)throws Exception {
        try(var c=DBContext.connect()){if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Refusing to test a non-test database.");}
        String tag="it"+System.nanoTime();User admin=user("ADMIN",tag),teacher=user("TEACHER",tag),student=user("STUDENT",tag),other=user("TEACHER",tag+"x"),student2=user("STUDENT",tag+"x");
        int subject=Sql.insert("INSERT INTO subjects(name,grade,created_by) VALUES(?,6,?)","Môn kiểm thử",teacher.getId());
        int lesson=Sql.insert("INSERT INTO lessons(subject_id,title,created_by) VALUES(?,?,?)",subject,"Bài kiểm thử",teacher.getId());
        int topic=Sql.insert("INSERT INTO topics(lesson_id,title,created_by) VALUES(?,?,?)",lesson,"Chủ đề kiểm thử",teacher.getId());
        int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,?,?,?)",topic,"Bài giảng kiểm thử","Nội dung thật được lưu vào SQL Server kiểm thử.",teacher.getId());
        int extra=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,?,?,?)",topic,"Bài bổ sung","Nội dung bổ sung",teacher.getId());
        int course=service.save(teacher,0,subject,"Khóa kiểm thử","Giới thiệu","Mục tiêu","Học sinh","",60,"DIRECT");
        check(dao.manages(teacher,course),"Creator teacher assigned");check(!dao.manages(other,course),"Other teacher denied");
        denied(()->service.status(teacher,course,"PUBLISHED"),"Empty curriculum cannot publish");
        denied(()->service.status(admin,course,"PUBLISHED"),"Empty curriculum denied");
        service.item(teacher,course,"item",0,lecture,"Chương 1",10,true,true);service.item(teacher,course,"item",0,extra,"Mở rộng",20,false,false);
        int item=Sql.intValue(dao.items(course,0).get(0),"id");
        denied(()->service.item(other,course,"remove",item,0,"",0,false,false),"Cross-course editing denied");
        denied(()->service.item(admin,course,"remove",item,0,"",0,false,false),"Admin cannot curate curriculum");
        denied(()->service.status(other,course,"PUBLISHED"),"Unassigned teacher cannot publish");
        service.status(teacher,course,"PUBLISHED");
        var pool=Executors.newFixedThreadPool(6);
        try{List<Callable<Void>> jobs=new ArrayList<>();for(int n=0;n<6;n++)jobs.add(()->{service.enroll(student,course);return null;});for(var f:pool.invokeAll(jobs))f.get();}finally{pool.shutdownNow();}
        check(Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM enrollments WHERE course_id=? AND student_id=?",course,student.getId()),"n")==1,"Concurrent enrollments unique");
        int eid=Sql.intValue(dao.enrollment(course,student.getId()),"id");
        denied(()->service.learn(other,item,"complete",null),"Teacher cannot write learner progress");
        service.learn(student,item,"view",null);check(!CourseService.flag(dao.record(eid,item).get("completed")),"Opening is not completion");
        service.learn(student,item,"note","<script>alert(1)</script> Ghi chú riêng");check(dao.record(eid,item).get("note").toString().contains("Ghi chú riêng"),"Persistent note");
        service.learn(student,item,"bookmark",null);check(CourseService.flag(dao.record(eid,item).get("bookmarked")),"Bookmark persists");
        service.learn(student,item,"complete",null);service.learn(student,item,"complete",null);
        check(dao.enrollment(course,student.getId()).get("completed_at")!=null,"Optional lessons excluded from completion");
        check(Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM learning_records WHERE enrollment_id=?",eid),"n")==1,"Completion idempotent");
        denied(()->service.status(admin,course,"DRAFT"),"Enrolled curriculum frozen");
        denied(()->new CatalogDao().delete(Entity.LECTURES,lecture,teacher.getId()),"Referenced lecture protected");
        denied(()->new CatalogDao().delete(Entity.LECTURES,extra,other.getId()),"Other teacher content protected");
        service.status(admin,course,"ARCHIVED");service.learn(student,item,"view",null);check(true,"Archived enrollment keeps access");
        denied(()->service.enroll(student2,course),"Archived no new enrollments");
        service.decide(teacher,course,eid,"REVOKED");denied(()->service.learn(student,item,"view",null),"Revocation effective");
        service.decide(teacher,course,eid,"ACTIVE");check(service.resume(course,eid)>0,"Resume valid");
        int approval=service.save(teacher,0,subject,"Chờ duyệt","Giới thiệu","Mục tiêu","Học sinh","",60,"APPROVAL");
        service.item(teacher,approval,"item",0,lecture,"",10,false,true);service.status(admin,approval,"PUBLISHED");service.enroll(student2,approval);
        int approvalItem=Sql.intValue(dao.items(approval,0).get(0),"id");
        check("PENDING".equals(dao.enrollment(approval,student2.getId()).get("status")),"Approval enrollment starts pending");
        check(service.active(student2,approval)!=null&&"ACTIVE".equals(dao.enrollment(approval,student2.getId()).get("status")),"Published program opens pending enrollment");
        int essay=Sql.insert("INSERT INTO questions(topic_id,kind,prompt,explanation,difficulty,created_by,library_visible) VALUES(?,'ESSAY',N'Viết bài',N'Hướng dẫn','EASY',?,1)",topic,teacher.getId());
        Sql.update("INSERT INTO course_item_questions(course_item_id,question_id,created_by) VALUES(?,?,?)",approvalItem,essay,teacher.getId());
        Sql.insert("INSERT INTO questions(topic_id,kind,prompt,explanation,difficulty,created_by,library_visible) VALUES(?,'ESSAY',N'Câu không gắn',N'Hướng dẫn','EASY',?,1)",topic,teacher.getId());
        int attempt=new PracticeDao().start(student.getId(),topic,"ESSAY");
        check(Sql.intValue(new PracticeDao().owned(attempt,student.getId()),"total")==1,"Practice uses only explicitly linked questions");
        byte[] image=Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        int answer=new PracticeService().submitImage(attempt,student.getId(),0,new UploadService.Image(image,"image/png",".png"));
        var row=new PracticeDao().answer(answer,student.getId(),false);
        check(row.get("score")==null&&"PENDING".equals(row.get("grading_status")),"Submission before AI has no score");
        check(Files.exists(new UploadService().resolve((String)row.get("upload_name"))),"Submission image persisted");
        denied(()->new PracticeService().submitImage(attempt,student.getId(),0,new UploadService.Image(image,"image/png",".png")),"Duplicate submission blocked");
        new PracticeService().grade(answer,student.getId());row=new PracticeDao().answer(answer,student.getId(),false);
        check(row.get("score")==null&&"RETRY".equals(row.get("grading_status")),"AI unavailable does not assign zero");
        check(Files.exists(new UploadService().resolve((String)row.get("upload_name"))),"AI failure preserves image");
        check(new PracticeDao().answer(answer,student2.getId(),false)==null,"Other student cannot read submission");
        new ProfileService().save(student,"Hồ sơ kiểm thử",7,Set.of(subject));check(((Number)new ProfileService().get(student.getId()).get("grade")).intValue()==7,"Profile persisted");
        Map<String,Object> lectureEdit=new LinkedHashMap<>();
        lectureEdit.put("topic_id",topic);lectureEdit.put("title","Bài giảng kiểm thử");lectureEdit.put("description","");lectureEdit.put("body","Nội dung sửa bởi giáo viên");lectureEdit.put("video_url","");
        denied(()->new CatalogService().save(Entity.LECTURES,lecture,lectureEdit,admin.getId()),"Admin cannot edit lecture content");
        denied(()->new CatalogService().save(Entity.LECTURES,lecture,lectureEdit,other.getId()),"Other teacher cannot edit lecture");
        Sql.update("UPDATE lectures SET library_visible=1 WHERE id=?",lecture);
        int copy=new CatalogService().copy(Entity.LECTURES,lecture,other.getId());
        check(Sql.intValue(Sql.one("SELECT created_by FROM lectures WHERE id=?",copy),"created_by")==other.getId(),"Copy owned by copying teacher");
        check(Sql.intValue(Sql.one("SELECT copied_from_id FROM lectures WHERE id=?",copy),"copied_from_id")==lecture,"Copy keeps source reference");
        check(!"Nội dung sửa bởi giáo viên".equals(Sql.one("SELECT body FROM lectures WHERE id=?",lecture).get("body")),"Copy leaves original untouched");
        denied(()->new ModerationService().moderate(Entity.LECTURES,lecture,admin.getId(),true,"ngắn"),"Hide requires a real reason");
        new ModerationService().moderate(Entity.LECTURES,lecture,admin.getId(),true,"Nội dung chứa thông tin sai lệch cần gỡ");
        check(dao.item(item)==null,"Hidden lecture leaves the student curriculum");
        check(Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM audit_logs WHERE action='CONTENT_HIDE_lectures' AND resource_id=? AND detail LIKE N'%sai lệch%'",lecture),"n")==1,"Hide action audited with reason");
        new ModerationService().moderate(Entity.LECTURES,lecture,admin.getId(),false,"");
        check(dao.item(item)!=null,"Unhide restores the lecture");
        int versioned=new CatalogService().save(Entity.LECTURES,lecture,lectureEdit,teacher.getId());
        check(versioned!=lecture,"Editing used lecture creates a new version");
        check(Sql.intValue(Sql.one("SELECT version FROM lectures WHERE id=?",versioned),"version")==2,"New version number increments");
        check(!Sql.one("SELECT body FROM lectures WHERE id=?",lecture).get("body").equals("Nội dung sửa bởi giáo viên"),"Old version keeps its body");
        Map<String,Object> questionEdit=new LinkedHashMap<>();
        questionEdit.put("topic_id",topic);questionEdit.put("kind","ESSAY");questionEdit.put("prompt","Viết bài");questionEdit.put("option_a","");questionEdit.put("option_b","");questionEdit.put("option_c","");questionEdit.put("option_d","");questionEdit.put("correct_option","");questionEdit.put("explanation","Hướng dẫn mới");questionEdit.put("difficulty","EASY");
        denied(()->new CatalogService().save(Entity.QUESTIONS,essay,questionEdit,admin.getId()),"Admin cannot edit question content");
        int questionVersion=new CatalogService().save(Entity.QUESTIONS,essay,questionEdit,teacher.getId());
        check(questionVersion!=essay,"Editing linked question creates a new version");
        check(Sql.intValue(Sql.one("SELECT question_id FROM course_item_questions WHERE course_item_id=?",approvalItem),"question_id")==questionVersion,"Practice link follows the new question version");
        System.out.println("PASS: "+checks+" SQL Server integration checks; isolated test data retained for inspection.");
    }
}