package vn.suviet;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import vn.suviet.dao.*;
import vn.suviet.model.User;
import vn.suviet.service.*;
import vn.suviet.util.*;

/** Integration and HTTP checks: refuses any database outside an isolated UpgradeTest copy. */
public final class LearningProgressTests {
    private static int checks;
    private static String base;
    private static final CourseService service=new CourseService();
    private static final LearningProgressDao progress=new LearningProgressDao();
    private record Account(User user,String email,String password) {}
    private static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);}
    private static Account account(String role)throws Exception {
        String email="progress"+UUID.randomUUID()+"@integration.invalid",password=UUID.randomUUID().toString();
        int id=new UserDao().create("Progress "+role,email,password,role);
        return new Account(new User(new UserDao().byId(id)),email,password);
    }
    private static LearningProgressDao.Filter filter(int course,String state,String learning){return new LearningProgressDao.Filter(course,"",state,learning,"name");}
    private static int number(Map<String,Object> row,String key){return Sql.intValue(row,key);}
    private static class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path)throws Exception {return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,Map<String,String> fields)throws Exception {
            var token=Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(get("/courses").body());if(!token.find())throw new AssertionError("Missing CSRF");
            StringJoiner data=new StringJoiner("&");fields.forEach((k,v)->data.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-Token",token.group(1)).POST(HttpRequest.BodyPublishers.ofString(data.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account account)throws Exception {status(post("/login",Map.of("email",account.email(),"password",account.password())),302,"Login "+account.user().getRole());}
    }
    private static void status(HttpResponse<String> response,int expected,String name){check(response.statusCode()==expected,name+" HTTP "+response.statusCode());}
    public static void main(String[] args)throws Exception {
        try(var c=DBContext.connect()){if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Isolated database required.");}
        base=args.length==0?"http://127.0.0.1:18092/SuVietTutor":args[0];
        Account admin=account("ADMIN"),teacher=account("TEACHER"),other=account("TEACHER"),student=account("STUDENT"),student2=account("STUDENT");
        Sql.update("UPDATE users SET full_name=? WHERE id=?","An <script>name</script>",student.user().getId());
        Sql.update("UPDATE users SET full_name=? WHERE id=?","Binh 100%",student2.user().getId());
        int subject=Sql.insert("INSERT INTO subjects(name,grade,created_by) VALUES(N'Progress test',6,?)",teacher.user().getId());
        int lesson=Sql.insert("INSERT INTO lessons(subject_id,title,created_by) VALUES(?,N'Progress lesson',?)",subject,teacher.user().getId());
        int topic=Sql.insert("INSERT INTO topics(lesson_id,title,created_by) VALUES(?,N'Progress topic',?)",lesson,teacher.user().getId());
        int course=service.save(teacher.user(),0,subject,"Assigned progress course","Summary","Objectives","Students","",60,"DIRECT");
        for(int n=1;n<=3;n++){
            int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by,library_visible) VALUES(?,?,N'Content',?,1)",topic,"Progress lesson "+n,teacher.user().getId());
            service.item(teacher.user(),course,"item",0,lecture,"Module "+n,n*10,false,n<3);
        }
        service.status(admin.user(),course,"PUBLISHED");service.enroll(student.user(),course);service.enroll(student2.user(),course);
        var items=new CourseDao().items(course,0);int first=number(items.get(0),"id"),second=number(items.get(1),"id"),optional=number(items.get(2),"id");
        int eid=number(new CourseDao().enrollment(course,student.user().getId()),"id"),eid2=number(new CourseDao().enrollment(course,student2.user().getId()),"id");
        int hidden=service.save(other.user(),0,subject,"PRIVATE_COURSE_SENTINEL","Summary","Objectives","Students","",60,"DIRECT");
        service.item(other.user(),hidden,"item",0,number(items.get(0),"lecture_id"),"",10,false,true);service.status(admin.user(),hidden,"PUBLISHED");service.enroll(student.user(),hidden);
        int hiddenEid=number(new CourseDao().enrollment(hidden,student.user().getId()),"id");
        var summary=progress.summary(teacher.user(),filter(0,"ACTIVE","ALL"));
        check(number(summary,"enrollment_count")==2&&number(summary,"not_started")==2,"Only assigned enrollments counted; no activity means not started");
        check(progress.enrollment(teacher.user(),hiddenEid)==null&&progress.lessons(teacher.user(),hiddenEid).isEmpty()&&progress.activity(teacher.user(),hiddenEid,1).isEmpty(),"Every detail query scopes assignments");
        boolean denied=false;try{progress.summary(student.user(),filter(0,"ALL","ALL"));}catch(AccessDeniedException e){denied=true;}check(denied,"DAO rejects student role");
        service.learn(student.user(),first,"view",null);service.learn(student.user(),first,"view",null);
        check(progress.activityCount(teacher.user(),eid)==1,"Repeated page views coalesced within five minutes");
        check(number(progress.enrollment(teacher.user(),eid),"percent_complete")==0&&"IN_PROGRESS".equals(progress.enrollment(teacher.user(),eid).get("learning_status")),"Opening starts learning without completing a lesson");
        service.learn(student.user(),first,"note","PRIVATE_NOTE_SENTINEL");service.learn(student.user(),first,"bookmark",null);
        check(progress.activityCount(teacher.user(),eid)==1,"Private note and bookmark are not teacher history");
        service.learn(student.user(),first,"complete",null);service.learn(student.user(),first,"complete",null);service.learn(student.user(),optional,"complete",null);
        check(progress.activityCount(teacher.user(),eid)==3,"Completion is logged once, including optional item");
        check(number(progress.enrollment(teacher.user(),eid),"percent_complete")==50,"Optional completion does not inflate required progress");
        check(!progress.lessons(teacher.user(),eid).toString().contains("PRIVATE_NOTE_SENTINEL")&&!progress.lessons(teacher.user(),eid).get(0).containsKey("note"),"Report excludes private notes");
        Sql.update("UPDATE learning_activity SET occurred_at='2026-10-19T01:02:03' WHERE enrollment_id=? AND course_item_id=? AND action='COMPLETE'",eid,first);
        var firstRow=progress.lessons(teacher.user(),eid).get(0);
        check(((java.sql.Timestamp)firstRow.get("completed_local")).toLocalDateTime().equals(LocalDateTime.of(2026,10,19,8,2,3)),"UTC events displayed in Vietnam local time");
        check(number(progress.summary(teacher.user(),filter(course,"ACTIVE","IN_PROGRESS")),"enrollment_count")==1,"Learning status filter");
        check(number(progress.summary(teacher.user(),new LearningProgressDao.Filter(course,"%","ACTIVE","ALL","name")),"enrollment_count")==1,"Search treats percent as literal text");
        Browser t=new Browser(),o=new Browser(),s=new Browser(),a=new Browser(),guest=new Browser();t.login(teacher);o.login(other);s.login(student);a.login(admin);
        status(guest.get("/teacher/progress"),302,"Guest cannot read progress");status(s.get("/teacher/progress"),403,"Student cannot read teacher report");status(a.get("/teacher/progress"),403,"Admin stays in admin routes");
        status(a.get("/admin/progress?course="+course),200,"Admin progress report");status(t.get("/admin/progress"),403,"Teacher cannot use admin report");
        var report=t.get("/teacher/progress?course="+course);status(report,200,"Teacher progress JSP");check(report.body().contains("50%")&&report.body().contains("&lt;script&gt;name&lt;/script&gt;"),"Percent rendered and learner name escaped");
        check(!t.get("/teacher/progress").body().contains("PRIVATE_COURSE_SENTINEL"),"Other course absent from options and rows");
        status(t.get("/teacher/progress?course="+hidden),403,"Cannot select unassigned course by URL");status(t.get("/teacher/progress?enrollment="+hiddenEid),403,"Cannot inspect same student in another teacher course");status(o.get("/teacher/progress?enrollment="+eid),403,"Other teacher cannot inspect student history");
        var detail=t.get("/teacher/progress?enrollment="+eid);status(detail,200,"Progress detail JSP");check(detail.body().contains("Nhật ký học tập")&&detail.body().contains("19/10/2026 08:02"),"Timeline and local completion time rendered");check(!detail.body().contains("PRIVATE_NOTE_SENTINEL"),"Private note absent from HTML");
        status(t.get("/teacher/students"),200,"Existing student-list link opens progress screen");
        status(t.get("/teacher/progress?learning=INVALID"),400,"Invalid learning filter rejected");status(t.get("/teacher/progress?sort=DROP"),400,"Sort uses allowlist");
        status(t.post("/teacher/progress",Map.of()),405,"Teacher report is read only");status(t.get("/teacher/results"),403,"Private practice results still blocked");
        status(s.get("/student/lesson?id="+second),200,"Student reads lesson with new history logging");
        status(s.post("/student/lesson",Map.of("id",""+second,"action","complete")),302,"Student completes lesson through HTTP");
        var completed=progress.enrollment(teacher.user(),eid);check(number(completed,"percent_complete")==100&&"COMPLETED".equals(completed.get("learning_status")),"Completing all required lessons reaches 100 percent");
        service.decide(teacher.user(),course,eid2,"REVOKED");check(number(progress.summary(teacher.user(),filter(course,"ACTIVE","ALL")),"enrollment_count")==1,"Default active filter excludes revoked enrollment");check(number(progress.summary(teacher.user(),filter(course,"ALL","ALL")),"enrollment_count")==2,"Historical revoked enrollment remains inspectable");
        status(t.get("/teacher/progress?enrollment="+eid2),200,"Inactive learner empty history page");
        service.teacher(admin.user(),course,other.user().getId(),false);service.teacher(admin.user(),course,teacher.user().getId(),true);
        status(t.get("/teacher/progress?enrollment="+eid),403,"Removing assignment immediately revokes report access");check(progress.activityCount(teacher.user(),eid)==0,"Revoked teacher cannot query timeline");
        service.teacher(admin.user(),course,teacher.user().getId(),false);
        String hash=Passwords.hash(UUID.randomUUID().toString());
        for(int n=0;n<21;n++){
            int uid=Sql.insert("INSERT INTO users(full_name,email,password_hash,role) VALUES(?,?,?,'STUDENT')","Page learner "+n,"page"+UUID.randomUUID()+"@integration.invalid",hash);
            Sql.insert("INSERT INTO enrollments(course_id,student_id,status) VALUES(?,?,'ACTIVE')",course,uid);
        }
        check(progress.list(teacher.user(),filter(course,"ACTIVE","ALL"),1).size()==20&&progress.list(teacher.user(),filter(course,"ACTIVE","ALL"),2).size()==2,"Enrollment pagination is bounded");
        var paged=t.get("/teacher/progress?course="+course+"&status=ACTIVE&learning=ALL&sort=recent&page=2");status(paged,200,"Second progress page");check(paged.body().contains("Trang 2 / 2")&&paged.body().contains("course="+course)&&paged.body().contains("sort=recent"),"Pagination retains filters and sorting");
        for(int n=0;n<21;n++)Sql.insert("INSERT INTO learning_activity(enrollment_id,course_item_id,course_id,action) VALUES(?,?,?,'VIEW')",eid,first,course);
        var historyPage=t.get("/teacher/progress?enrollment="+eid+"&page=2");status(historyPage,200,"Second history page");check(historyPage.body().contains("Trang 2 / 2")&&historyPage.body().contains("enrollment="+eid),"History pagination retains enrollment scope");
        int emptyCourse=service.save(teacher.user(),0,subject,"Empty curriculum","Summary","Objectives","Students","",60,"DIRECT");
        int emptyEid=Sql.insert("INSERT INTO enrollments(course_id,student_id,status) VALUES(?,?,'PENDING')",emptyCourse,student.user().getId());
        check(number(progress.enrollment(teacher.user(),emptyEid),"percent_complete")==0&&!"COMPLETED".equals(progress.enrollment(teacher.user(),emptyEid).get("learning_status")),"Zero required lessons never means complete");
        status(t.get("/teacher/progress?enrollment="+emptyEid),200,"Empty curriculum detail");
        System.out.println("PASS: "+checks+" learning-progress integration/HTTP checks on isolated SQL Server.");
    }
}
