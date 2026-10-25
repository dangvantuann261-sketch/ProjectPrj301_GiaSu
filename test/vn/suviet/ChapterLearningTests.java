package vn.suviet;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.*;

/** Uses only a disposable UpgradeTest database; optional HTTP checks against its Tomcat. */
public final class ChapterLearningTests {
    private static int checks;
    private static String base;
    private record Account(User user,String email,String password) {}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    private static Account account(String role)throws Exception {
        String email="chapter"+UUID.randomUUID()+"@test.invalid",password=UUID.randomUUID().toString();
        int id=new UserDao().create("Chapter "+role,email,password,role);
        return new Account(new User(new UserDao().byId(id)),email,password);
    }
    private static final class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path)throws Exception {return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,Map<String,String> fields)throws Exception {
            var match=Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(get("/courses").body());
            if(!match.find())throw new AssertionError("CSRF missing");
            StringJoiner body=new StringJoiner("&");fields.forEach((k,v)->body.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("X-CSRF-Token",match.group(1)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account a)throws Exception {check(post("/login",Map.of("email",a.email(),"password",a.password())).statusCode()==302,"Login "+a.user().getRole());}
        String page(String path)throws Exception {var response=get(path);check(response.statusCode()==200,"Page "+path+" status "+response.statusCode());return response.body();}
    }
    private static void finish(PracticeDao dao,int attempt,int uid)throws Exception {
        var a=dao.owned(attempt,uid);
        for(int pos=Sql.intValue(a,"current_pos");pos<Sql.intValue(a,"total");pos++){
            var q=Question.restore((String)dao.item(attempt,pos).get("question_snapshot"));
            dao.save(attempt,uid,pos,q.getCorrectOption(),10.0,q.getExplanation(),"","","");
        }
    }
    public static void main(String[] args)throws Exception {
        try(var c=DBContext.connect()){
            if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Refusing to modify a non-test database");
            check(Sql.one("SELECT version FROM schema_migrations WHERE version=9")!=null,"Migration 9 installed by database administrator");
        }
        var admin=account("ADMIN");var teacher=account("TEACHER");var other=account("TEACHER");var student=account("STUDENT");var second=account("STUDENT");
        var service=new CourseService();var courses=new CourseDao();var dao=new PracticeDao();
        int subject=Sql.insert("INSERT INTO subjects(name,grade,created_by) VALUES(N'Chapter test',6,?)",teacher.user().getId());
        int lesson=Sql.insert("INSERT INTO lessons(subject_id,title,created_by) VALUES(?,N'Chapter lesson',?)",subject,teacher.user().getId());
        int course=service.save(teacher.user(),0,subject,"Chapter <test>","Summary","Objectives","Students","",60,"APPROVAL");
        List<Integer> topics=new ArrayList<>();
        for(int n=1;n<=3;n++){
            int topic=Sql.insert("INSERT INTO topics(lesson_id,title,created_by) VALUES(?,?,?)",lesson,"Topic "+n,teacher.user().getId());topics.add(topic);
            int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,?,N'Theory',?)",topic,"Lesson "+n,teacher.user().getId());
            service.item(teacher.user(),course,"item",0,lecture,n<3?"Chapter A <test>":"Chapter B",n*10,false,true);
            int itemId=Sql.intValue(Sql.one("SELECT TOP 1 id FROM course_items WHERE course_id=? AND lecture_id=?",course,lecture),"id");
            for(int q=1;q<=6;q++){
                int question=Sql.insert("INSERT INTO questions(topic_id,kind,prompt,option_a,option_b,option_c,option_d,correct_option,explanation,difficulty,created_by) VALUES(?,'MCQ',N'Question',N'A',N'B',N'C',N'D','A',N'Explanation','EASY',?)",topic,teacher.user().getId());
                Sql.update("INSERT INTO course_item_questions(course_item_id,question_id,created_by) VALUES(?,?,?)",itemId,question,teacher.user().getId());
            }
        }
        int first=Sql.intValue(courses.items(course,0).get(0),"id");
        boolean denied=false;try{service.active(student.user(),course);}catch(AccessDeniedException expected){denied=true;}check(denied,"Draft is not accessible to students");
        service.status(admin.user(),course,"PUBLISHED");
        var enrollment=service.active(student.user(),course);int eid=Sql.intValue(enrollment,"id");
        check("ACTIVE".equals(enrollment.get("status")),"Published approval curriculum opens immediately");
        check(eid==Sql.intValue(service.active(student.user(),course),"id"),"Progress identity is idempotent");
        int attempt=dao.startScoped(student.user().getId(),first,false,"MCQ");
        check(attempt==dao.startScoped(student.user().getId(),first,false,"MCQ"),"Repeated start resumes unfinished lesson practice");
        check(Sql.intValue(dao.owned(attempt,student.user().getId()),"total")==5,"Lesson practice selects five questions including private attached content");
        finish(dao,attempt,student.user().getId());
        int chapter=dao.startScoped(student.user().getId(),first,true,"MCQ");
        check(Sql.intValue(dao.owned(chapter,student.user().getId()),"total")==10,"Chapter practice selects ten questions");
        for(var row:Sql.query("SELECT question_snapshot FROM attempt_items WHERE attempt_id=?",chapter))check(topics.subList(0,2).contains(Question.restore((String)row.get("question_snapshot")).getTopicId()),"Chapter excludes questions from other chapters");
        finish(dao,chapter,student.user().getId());
        service.learn(student.user(),first,"complete",null);
        var items=courses.items(course,eid);var curriculum=new CurriculumDao();curriculum.decorate(course,student.user().getId(),items);
        var chapters=CurriculumService.chapters(items);curriculum.decorateChapters(course,student.user().getId(),chapters);
        check(chapters.size()==2&&Sql.intValue(chapters.get(0),"progress")==50,"Chapter theory progress uses required lessons");
        check(Sql.intValue(items.get(0),"practice_finished")==1&&((Number)items.get(0).get("best_score")).doubleValue()==10.0,"Lesson practice completion and best score tracked");
        check(Sql.intValue(chapters.get(0),"practice_finished")==1,"Chapter practice tracked separately");
        int nextItem=Sql.intValue(items.get(1),"id");int zeroAttempt=dao.startScoped(student.user().getId(),nextItem,false,"MCQ");
        int zeroTotal=Sql.intValue(dao.owned(zeroAttempt,student.user().getId()),"total");
        for(int pos=0;pos<zeroTotal;pos++)dao.save(zeroAttempt,student.user().getId(),pos,"B",0.0,"Try again","","","");
        int unfinished=dao.startScoped(student.user().getId(),nextItem,false,"MCQ");
        dao.save(unfinished,student.user().getId(),0,"A",10.0,"Correct","","","");
        curriculum.decorate(course,student.user().getId(),items);
        check(((Number)items.get(1).get("best_score")).doubleValue()==0.0&&Sql.intValue(items.get(1),"practice_finished")==1,"Zero score retained; unfinished attempt does not inflate best score");
        var report=new LearningProgressDao();check(report.enrollment(other.user(),eid)==null,"Other teacher cannot inspect progress");
        check(report.enrollment(teacher.user(),eid)!=null,"Assigned teacher sees automatically created learning profile");
        var secondItems=courses.items(course,0);curriculum.decorate(course,second.user().getId(),secondItems);
        check(Sql.intValue(secondItems.get(0),"practice_finished")==0,"Practice results isolated per student");
        if(args.length>0){
            base=args[0];var s=new Browser();s.login(second);var t=new Browser();t.login(teacher);var a=new Browser();a.login(admin);var guest=new Browser();
            String detail=s.page("/course?id="+course);
            check(detail.contains("Chapter A &lt;test&gt;")&&!detail.contains("/student/enroll")&&!detail.contains("Thư viện tự học"),"Chapter UI escapes text and removes enrollment/library");
            check(courses.enrollment(course,second.user().getId())==null,"Viewing outline does not create learning activity");
            s.page("/student/lesson?id="+first);
            check(courses.enrollment(course,second.user().getId())!=null,"First lesson creates progress without enrollment form");
            var started=s.post("/student/start",Map.of("item_id",""+first,"scope","chapter","kind","MCQ"));check(started.statusCode()==302,"Start chapter through HTTP");
            s.page(started.headers().firstValue("location").orElseThrow().replace("/SuVietTutor",""));
            int httpAttempt=Integer.parseInt(started.headers().firstValue("location").orElseThrow().split("id=")[1]);
            check(s.post("/student/practice",Map.of("id",""+httpAttempt,"position","0","chosen","A")).statusCode()==302,"Chapter answer submitted through HTTP");
            check(s.get("/student/practice?id="+chapter).statusCode()==404,"Student cannot read another student's attempt");
            s.page("/student/dashboard");s.page("/student/courses");s.page("/student/history");
            check(s.get("/student/learn").statusCode()==302,"Old library route redirects to curriculum");
            check(s.post("/student/enroll",Map.of("course_id",""+course)).statusCode()==410,"Enrollment endpoint retired");
            check(s.post("/student/start",Map.of("item_id",""+first,"scope","invalid","kind","MCQ")).statusCode()==400,"Invalid practice scope rejected");
            check(s.post("/student/start",Map.of("topic_id",""+topics.get(0),"kind","MCQ")).statusCode()==400,"Unscoped library start rejected");
            String progress=t.page("/teacher/progress?enrollment="+eid);
            check(progress.contains("Tiến độ theo chương")&&progress.contains("Điểm cao nhất"),"Teacher sees chapter and lesson practice results");
            t.page("/teacher/progress?course="+course);t.page("/teacher/courses?id="+course);a.page("/admin/progress?enrollment="+eid);a.page("/admin/courses?id="+course);
            guest.page("/course?id="+course);check(guest.get("/preview?id="+first).statusCode()==404,"Guest cannot preview a non-preview lesson");
            var owner=new Browser();owner.login(student);owner.page("/student/practice?id="+attempt);owner.page("/student/practice?id="+chapter);
            String saved=owner.page("/course?id="+course);check(saved.contains("50%")&&saved.contains("0/10"),"Student sees saved chapter progress and zero score after login");
        }
        System.out.println("PASS: "+checks+" chapter learning checks");
    }
}
