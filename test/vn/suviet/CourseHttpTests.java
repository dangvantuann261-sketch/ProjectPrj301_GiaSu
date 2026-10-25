package vn.suviet;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.regex.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.DBContext;

/** HTTP tests use an isolated restored SQL Server database, never production. */
public final class CourseHttpTests {
    private static String base;
    private static int checks;
    private record Account(int id,String email,String password) {}
    private static class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,Map<String,String> fields,boolean csrf)throws Exception {
            var data=new LinkedHashMap<>(fields);
            if(csrf){var response=get("/courses");data.put("csrf",token(response.body()));}
            StringJoiner body=new StringJoiner("&");data.forEach((k,v)->body.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account a)throws Exception {check(post("/login",Map.of("email",a.email,"password",a.password),true).statusCode()==302,"Login "+a.id);}
        HttpResponse<String> upload(int attempt,byte[] image)throws Exception {
            String boundary="Test"+UUID.randomUUID();var out=new ByteArrayOutputStream();
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"id\"\r\n\r\n"+attempt+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"position\"\r\n\r\n0\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"test.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(image);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return http.send(HttpRequest.newBuilder(URI.create(base+"/student/practice?id="+attempt)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-Token",token(get("/courses").body())).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    private static String token(String html){var m=Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(html);if(!m.find())throw new AssertionError("CSRF meta missing");return m.group(1);}
    private static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);System.out.println("PASS "+name);}
    private static void status(HttpResponse<String> r,int expected,String name){check(r.statusCode()==expected,name+" (HTTP "+r.statusCode()+")");}
    private static Account account(String role)throws Exception {String email="http"+UUID.randomUUID()+"@integration.invalid",password=UUID.randomUUID().toString();return new Account(new UserDao().create("HTTP "+role,email,password,role),email,password);}
    public static void main(String[] args)throws Exception {
        try(var c=DBContext.connect()){if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Test database required.");}
        base=args.length==0?"http://127.0.0.1:18086/SuVietTutor":args[0];
        Account admin=account("ADMIN"),teacher=account("TEACHER"),student=account("STUDENT"),outsider=account("TEACHER"),student2=account("STUDENT");
        Browser a=new Browser(),t=new Browser(),s=new Browser(),o=new Browser(),s2=new Browser(),guest=new Browser();
        a.login(admin);t.login(teacher);s.login(student);o.login(outsider);s2.login(student2);
        status(guest.get("/explore"),200,"Public homepage");status(guest.get("/courses?q=notfound"),200,"Empty search");status(guest.get("/assets/academy.css"),200,"New stylesheet");status(guest.get("/student/dashboard"),302,"Guest dashboard blocked");
        int subject=Sql.insert("INSERT INTO subjects(name,grade,created_by) VALUES(N'HTTP subject',6,?)",teacher.id);
        int lesson=Sql.insert("INSERT INTO lessons(subject_id,title,created_by) VALUES(?,N'HTTP lesson',?)",subject,teacher.id);
        int topic=Sql.insert("INSERT INTO topics(lesson_id,title,created_by) VALUES(?,N'HTTP topic',?)",lesson,teacher.id);
        int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,N'HTTP private lecture',N'PRIVATE_BODY_SENTINEL',?)",topic,teacher.id);
        int previewLecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,N'HTTP preview',N'PREVIEW_BODY_SENTINEL',?)",topic,teacher.id);
        status(t.get("/teacher/courses"),200,"Teacher course screen");
        var form=new LinkedHashMap<String,String>();form.put("action","save");form.put("id","0");form.put("subject_id",""+subject);form.put("title","HTTP Course");form.put("summary","Summary");form.put("objectives","Objectives");form.put("audience","Students");form.put("prerequisites","");form.put("duration_minutes","90");form.put("enrollment_mode","APPROVAL");
        status(t.post("/teacher/courses",form,false),403,"Course POST requires CSRF");
        var created=t.post("/teacher/courses",form,true);status(created,302,"Create draft");
        String location=created.headers().firstValue("location").orElseThrow();int course=Integer.parseInt(location.substring(location.lastIndexOf('=')+1));
        status(t.get("/teacher/courses?id="+course),200,"Edit draft JSP");status(guest.get("/course?id="+course),404,"Draft hidden publicly");status(o.get("/teacher/courses?id="+course),403,"Other teacher cannot manage");
        for(int n=0;n<2;n++){var f=new LinkedHashMap<String,String>();f.put("id",""+course);f.put("action","item");f.put("lecture_id",""+(n==0?lecture:previewLecture));f.put("section_title","Chapter");f.put("position",""+(10+n*10));f.put("required","on");if(n==1)f.put("is_preview","on");status(t.post("/teacher/courses",f,true),302,"Add curriculum "+n);}
        var items=new CourseDao().items(course,0);int item=Sql.intValue(items.get(0),"id"),preview=Sql.intValue(items.get(1),"id");
        int essayQuestion=Sql.insert("INSERT INTO questions(topic_id,kind,prompt,explanation,difficulty,created_by,library_visible) VALUES(?,'ESSAY',N'HTTP essay',N'Rubric','EASY',?,1)",topic,teacher.id);
        status(t.post("/teacher/courses",Map.of("id",""+course,"action","questions","item_id",""+item,"question_id",""+essayQuestion),true),302,"Attach question to lesson");
        status(t.post("/teacher/courses",Map.of("id",""+course,"action","status","status","PUBLISHED"),true),302,"Assigned teacher publishes");
        status(a.post("/admin/courses",Map.of("id",""+course,"action","status","status","PUBLISHED"),true),302,"Admin may publish assigned program");
        status(a.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","save","topic_id",""+topic,"title","Admin edit","description","","body","x","video_url",""),true),403,"Admin cannot author lectures");
        status(a.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","delete"),true),403,"Admin cannot delete lectures");
        status(t.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","moderate","hidden","1","reason","Nội dung sai lệch kiến thức"),true),403,"Teacher cannot moderate content");
        status(a.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","moderate","hidden","1","reason","ngắn"),true),400,"Hide requires a reason");
        status(a.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","moderate","hidden","1","reason","Nội dung sai lệch kiến thức"),true),302,"Admin hides violating lecture");
        check(CourseService.flag(Sql.one("SELECT hidden FROM lectures WHERE id=?",lecture).get("hidden")),"Lecture hidden in database");
        status(a.post("/admin/catalog",Map.of("entity","lectures","id",""+lecture,"action","moderate","hidden","0"),true),302,"Admin restores lecture");
        check(!CourseService.flag(Sql.one("SELECT hidden FROM lectures WHERE id=?",lecture).get("hidden")),"Lecture restored in database");
        status(guest.get("/course?id="+course),200,"Published detail JSP");status(guest.get("/preview?id="+preview),200,"Allowed preview");status(guest.get("/preview?id="+item),404,"Private preview denied");
        check(!s.get("/student/topic?id="+topic).body().contains("PRIVATE_BODY_SENTINEL"),"Legacy route does not expose private body");
        status(s.get("/student/lesson?id="+item),200,"Published program opens lesson directly");
        status(s.post("/student/enroll",Map.of("course_id",""+course),true),410,"Legacy enrollment endpoint retired");
        status(s.get("/student/courses"),200,"My courses JSP");
        int eid=Sql.intValue(new CourseDao().enrollment(course,student.id),"id");
        check("ACTIVE".equals(new CourseDao().enrollment(course,student.id).get("status")),"Published enrollment active");
        var lessonPage=s.get("/student/lesson?id="+item);status(lessonPage,200,"Lesson JSP");check(lessonPage.body().contains("PRIVATE_BODY_SENTINEL"),"Enrolled reads content");
        check(!CourseService.flag(new CourseDao().record(eid,item).get("completed")),"Page view not completed");
        status(s.post("/student/lesson",Map.of("id",""+item,"action","note","note","<script>privateNote()</script>"),true),302,"Save private note");
        check(s.get("/student/lesson?id="+item).body().contains("&lt;script&gt;privateNote()&lt;/script&gt;"),"Notes escaped in HTML");
        status(s2.post("/student/lesson",Map.of("id",""+item,"action","note","note","attack"),true),302,"Other student writes own note");
        check(s.get("/student/lesson?id="+item).body().contains("privateNote()"),"Other student cannot overwrite note");
        status(s.post("/student/lesson",Map.of("id",""+item,"action","bookmark"),true),302,"Bookmark POST");
        status(s.post("/student/lesson",Map.of("id",""+item,"action","complete"),true),302,"Complete lesson");
        status(s.get("/student/resume?id="+course),302,"Resume redirect");status(s.get("/student/dashboard"),200,"Dashboard JSP");
        status(t.get("/teacher/courses?id="+course),200,"Teacher progress JSP");status(o.post("/teacher/courses",Map.of("id",""+course,"action","status","status","ARCHIVED"),true),403,"Outside teacher cannot change status");
        status(t.get("/teacher/results"),403,"Teacher cannot see private practice");status(t.get("/upload?id=1"),403,"Teacher cannot read global upload");
        status(o.get("/teacher/catalog?entity=lectures&edit="+lecture),403,"Other content owner blocked");status(t.get("/teacher/catalog?entity=lectures"),200,"Catalog regression");status(t.get("/teacher/generate"),200,"AI generation screen regression");status(t.get("/teacher/students"),200,"Scoped students screen");
        status(s.get("/student/profile"),200,"Profile JSP");status(s.post("/student/profile",Map.of("full_name","HTTP Student Updated","grade","8"),true),302,"Save profile");
        status(s.get("/student/history"),200,"Practice history regression");status(s.get("/student/learn"),302,"Legacy library route redirects");status(a.get("/admin/dashboard"),200,"Admin regression");
        s.post("/logout",Map.of(),true);s.login(student);check(s.get("/student/lesson?id="+item).body().contains("privateNote()"),"Note survives logout");
        status(s2.get("/admin/dashboard"),403,"Student role blocked");
        var start=s.post("/student/start",Map.of("item_id",""+item,"kind","ESSAY"),true);status(start,302,"Start essay HTTP");
        String startLocation=start.headers().firstValue("location").orElseThrow();int attempt=Integer.parseInt(startLocation.substring(startLocation.lastIndexOf('=')+1));
        status(s.get("/student/practice?id="+attempt),200,"Essay practice JSP");status(s.upload(attempt,"not an image".getBytes(StandardCharsets.UTF_8)),400,"Fake image rejected");
        var png=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",png);
        var submission=s.upload(attempt,png.toByteArray());status(submission,302,"Image submission without AI");String answerLocation=submission.headers().firstValue("location").orElseThrow();int answer=Integer.parseInt(answerLocation.substring(answerLocation.lastIndexOf('=')+1));
        status(s.get("/student/review?id="+answer),200,"Pending grade review JSP");status(s.get("/student/practice?id="+attempt),200,"Summary pending grade JSP");
        status(s.get("/upload?id="+answer),200,"Owner reads uploaded image");status(s2.get("/upload?id="+answer),404,"Other student image blocked");
        status(s.post("/student/grade",Map.of("id",""+answer),true),302,"AI unavailable handled HTTP");
        check(new PracticeDao().answer(answer,student.id,false).get("score")==null,"No zero grade after HTTP AI failure");status(s.get("/student/history"),200,"Pending history JSP");
        Browser secondSession=new Browser();secondSession.login(student);
        String newPassword=UUID.randomUUID().toString();status(s.post("/student/profile",Map.of("action","password","current_password",student.password,"new_password",newPassword,"confirm_password",newPassword),true),302,"Password change");
        status(secondSession.get("/student/dashboard"),302,"Password change invalidates other session");
        System.out.println("PASS: "+checks+" HTTP checks on isolated SQL Server/Tomcat; no production data written.");
    }
}