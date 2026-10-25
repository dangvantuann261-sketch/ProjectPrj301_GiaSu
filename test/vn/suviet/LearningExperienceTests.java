package vn.suviet;
import com.google.gson.*;
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

/** Integration tests require a disposable UpgradeTest database and its own HTTP server. */
public final class LearningExperienceTests {
    static int checks,course,first;static String base;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    record Account(User user,String email,String password){}
    static Account account(String role)throws Exception {
        String mail="experience-"+UUID.randomUUID()+"@test.invalid",password=UUID.randomUUID().toString();
        int uid=new UserDao().create("Experience "+role,mail,password,role);
        return new Account(new User(new UserDao().byId(uid)),mail,password);
    }
    static final class Browser {
        HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path)throws Exception{return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,Map<String,String> fields)throws Exception{
            var m=Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(get("/course?id="+course).body());if(!m.find())throw new AssertionError("CSRF missing");
            var body=new StringJoiner("&");fields.forEach((k,v)->body.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(40)).header("X-CSRF-Token",m.group(1)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account a)throws Exception{check(post("/login",Map.of("email",a.email(),"password",a.password())).statusCode()==302,"Login "+a.user().getRole());}
        String page(String path)throws Exception{var r=get(path);check(r.statusCode()==200,"Page "+path+" status "+r.statusCode());return r.body();}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Test Tomcat base URL required");base=args[0];
        try(var c=DBContext.connect()){if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Refusing non-test database");}
        var manifest=JsonParser.parseString(new SettingsDao().get("seed.history6.kntt.v1","{}")).getAsJsonObject();course=manifest.get("course_id").getAsInt();first=manifest.getAsJsonArray("items").get(0).getAsJsonObject().get("item_id").getAsInt();
        var courseDao=new CourseDao();var items=courseDao.items(course,0);
        check(items.size()==20,"20 lessons available");check(CurriculumService.chapters(items).size()==5,"Five chapters");
        for(var i:items)check(Sql.intValue(i,"question_count")==5&&Sql.intValue(i,"essay_count")>=2,"Five MCQ and at least two essays in lesson "+i.get("id"));
        var a=account("STUDENT");var other=account("STUDENT");int uid=a.user().getId();var practice=new PracticeDao();var insights=new LearningInsightsDao();var chats=new ChatDao();
        var b=new Browser();b.login(a);
        String dashboard=b.page("/student/dashboard");check(!dashboard.contains("learner-stats"),"Overview has no duplicated statistics cards");
        var nav=Pattern.compile("<nav aria-label=\"Điều hướng chính\".*?</nav>",Pattern.DOTALL).matcher(dashboard);check(nav.find(),"Student sidebar exists");
        String sidebar=nav.group();check(sidebar.split("<a ",-1).length-1==5&&!sidebar.contains("Hồ sơ"),"Exactly five student navigation links");
        check(dashboard.contains("account-menu")&&dashboard.contains("profile#password"),"Profile and password in account menu");
        b.page("/student/progress");b.page("/student/mistakes");b.page("/student/history");b.page("/student/profile");
        String lesson=b.page("/student/lesson?id="+first);check(lesson.contains("itemId="+first)&&lesson.contains("THẺ GHI NHỚ"),"Lesson links contextual tutor and flashcard");
        String roadmap=b.page("/course?id="+course);check(roadmap.contains("data-lesson-search")&&roadmap.contains("Dòng thời gian"),"Roadmap includes lesson search and timeline");
        String tutor=b.page("/student/tutor?itemId="+first);check(tutor.contains("data-item-id=\""+first+"\""),"Tutor carries lesson context");
        String key="lesson:"+first;var lease=chats.claim(uid,key,false);check(lease!=null&&chats.claim(uid,key,false)==null,"Database lease blocks overlapping chats");
        var messages=List.of(new AIClient.Message("user","Test persisted question"),new AIClient.Message("model","Test persisted hint"));
        check(chats.finish(uid,key,lease,messages),"Chat commits complete exchange");check(new ChatDao().history(uid,key).size()==2,"New DAO reads persistent chat");
        check(chats.history(other.user().getId(),key).isEmpty(),"Chat isolated by learner");
        var fresh=new Browser();fresh.login(a);check(fresh.page("/api/chat?itemId="+first).contains("Test persisted hint"),"Chat survives another login session");
        Sql.update("UPDATE tutor_conversations SET busy_until=NULL WHERE user_id=? AND context_key=?",uid,key);
        var reset=chats.claim(uid,key,true);chats.release(uid,key,reset);check(chats.history(uid,key).size()==2,"Failed reset preserves original chat");
        Sql.update("UPDATE tutor_conversations SET busy_until=NULL WHERE user_id=? AND context_key=?",uid,key);
        reset=chats.claim(uid,key,true);check(chats.finish(uid,key,reset,List.of(new AIClient.Message("user","New question"),new AIClient.Message("model","New hint"))),"Successful reset commits");
        check(Sql.one("SELECT id FROM tutor_chat_archives WHERE user_id=? AND context_key=?",uid,key)!=null,"Reset archives previous history");
        int attempt=practice.startScoped(uid,first,false,"MCQ"),wrongAnswer=0;
        for(int pos=0;pos<5;pos++){
            var q=Question.restore((String)practice.item(attempt,pos).get("question_snapshot"));boolean right=pos>0;
            int answer=practice.save(attempt,uid,pos,right?q.getCorrectOption():q.getCorrectOption().equals("A")?"B":"A",right?10.0:0.0,right?q.getExplanation():"Try again","","","");if(!right)wrongAnswer=answer;
        }
        check(insights.mistakes(uid,course).size()==1,"Wrong answer collected once");
        String review=b.page("/student/review?id="+wrongAnswer);check(!review.contains("Xem đáp án và giải thích")&&!review.contains("Đáp án đúng:"),"Wrong review does not disclose answer");
        String summary=b.page("/student/practice?id="+attempt);check(summary.contains("4 câu đúng")&&summary.contains("Hỏi AI về câu này"),"Summary shows total and per-question AI links");
        check(Math.abs(((Number)insights.trend(uid,course).get(0).get("score")).doubleValue()-8)<.001,"Trend uses completed attempt score");
        var outsider=new Browser();outsider.login(other);check(outsider.get("/student/review?id="+wrongAnswer).statusCode()==404,"Other learner cannot read answer");check(outsider.get("/api/chat?answerId="+wrongAnswer).statusCode()==404,"Other learner cannot load answer chat");
        int retry=insights.retry(a.user(),course);check(retry==insights.retry(a.user(),course),"Repeated mistake retry resumes existing attempt");
        var q=Question.restore((String)practice.item(retry,0).get("question_snapshot"));practice.save(retry,uid,0,q.getCorrectOption(),10.0,q.getExplanation(),"","","");
        check(insights.mistakes(uid,course).isEmpty(),"Correct retry removes mistake");check(practice.answer(wrongAnswer,uid,false)!=null,"Retry preserves original answer history");
        int essay=practice.startScoped(uid,first,false,"ESSAY"),answer=practice.save(essay,uid,0,"",null,"Test upload","","test-only.png","image/png");
        check(practice.claimGrade(answer,uid),"Essay grading claimed");practice.gradeResult(answer,7.0,"Test transcription","Test grading notification","AI_GRADED");
        check(insights.notifications(uid).size()==1&&insights.notifications(other.user().getId()).isEmpty(),"Graded essay notification is owner scoped");
        insights.readNotification(other.user().getId(),answer);check(insights.notifications(uid).size()==1,"Other user cannot mark notification read");
        insights.readNotification(uid,answer);insights.readNotification(uid,answer);check(insights.notifications(uid).isEmpty(),"Mark notification read is idempotent");
        var admin=account("ADMIN");var teacher=account("TEACHER");var ab=new Browser();ab.login(admin);ab.page("/admin/dashboard");ab.page("/admin/courses");
        var tb=new Browser();tb.login(teacher);tb.page("/teacher/courses");check(tb.get("/student/dashboard").statusCode()==403,"Teacher cannot enter student workspace");
        check(b.get("/admin/dashboard").statusCode()==403&&b.get("/teacher/courses").statusCode()==403,"Student cannot enter manager workspaces");
        var service=new CourseService();int subject=Sql.intValue(courseDao.get(course),"subject_id");
        int teacherCourse=service.save(teacher.user(),0,subject,"Experience teacher lesson","Summary","Goals","Students","",30,"DIRECT");
        int topic=Sql.intValue(courseDao.item(first),"topic_id");int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,N'Fresh teacher content',N'Teacher published content',?)",topic,teacher.user().getId());
        service.item(teacher.user(),teacherCourse,"item",0,lecture,"Teacher chapter",1,false,true);service.status(admin.user(),teacherCourse,"PUBLISHED");
        int teacherItem=Sql.intValue(courseDao.items(teacherCourse,0).get(0),"id");check(b.page("/student/lesson?id="+teacherItem).contains("Teacher published content"),"Student reads exact teacher-authored content");
        int draft=service.save(teacher.user(),0,subject,"Draft context","Summary","Goals","Students","",30,"DIRECT");service.item(teacher.user(),draft,"item",0,lecture,"Draft",1,false,true);int draftItem=Sql.intValue(courseDao.items(draft,0).get(0),"id");boolean denied=false;try{LessonContext.accessible(a.user(),draftItem);}catch(AccessDeniedException expected){denied=true;}check(denied,"Draft lesson not accessible to contextual tutor");
        b.page("/student/progress");b.page("/student/notifications");
        System.out.println("PASS: "+checks+" learning experience integration checks");
    }
}
