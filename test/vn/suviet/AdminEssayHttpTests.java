package vn.suviet;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.Pattern;
import vn.suviet.dao.*;
import vn.suviet.service.*;
import vn.suviet.util.*;

/** Only runs against a restored UpgradeTest database with a loopback fake provider. */
public final class AdminEssayHttpTests {
    private static String base;
    private static int checks;
    private static final String SECRET="LOCAL_TEST_KEY_"+UUID.randomUUID();
    private record Account(int id,String email,String password) {}
    private static void check(boolean b,String name){checks++;if(!b)throw new AssertionError(name);System.out.println("PASS "+name);}
    private static void status(HttpResponse<String> response,int expected,String name){check(response.statusCode()==expected,name+" HTTP "+response.statusCode());}
    private static Account account(String role)throws Exception {String email="essay"+UUID.randomUUID()+"@integration.invalid",password=UUID.randomUUID().toString();return new Account(new UserDao().create("Essay "+role,email,password,role),email,password);}
    private static String token(String html){var m=Pattern.compile("name=\"csrf-token\" content=\"([^\"]+)\"").matcher(html);if(!m.find())throw new AssertionError("CSRF missing");return m.group(1);}
    private static final class Browser {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> get(String path)throws Exception{return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofString());}
        HttpResponse<String> post(String path,Map<String,String> fields,boolean csrf)throws Exception {
            StringJoiner body=new StringJoiner("&");fields.forEach((k,v)->body.add(URLEncoder.encode(k,StandardCharsets.UTF_8)+"="+URLEncoder.encode(v,StandardCharsets.UTF_8)));
            var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","application/x-www-form-urlencoded");
            if(csrf)builder.header("X-CSRF-Token",token(get("/courses").body()));
            return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        }
        void login(Account a)throws Exception {status(post("/login",Map.of("email",a.email(),"password",a.password()),true),302,"Login "+a.id());}
        HttpResponse<String> image(String path,Map<String,String> fields,byte[] image)throws Exception {
            String boundary="Essay"+UUID.randomUUID();var out=new ByteArrayOutputStream();
            for(var field:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"image\"; filename=\"essay.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(image);out.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            return http.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+boundary).header("X-CSRF-Token",token(get("/courses").body())).POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonObject step(int id,int version,String action,String value)throws Exception {
            var res=post("/api/essay-step",Map.of("answerId",""+id,"version",""+version,"action",action,"value",value),true);status(res,200,"Step "+action);return JsonParser.parseString(res.body()).getAsJsonObject();
        }
    }
    private static int redirectId(HttpResponse<String> response){String location=response.headers().firstValue("location").orElseThrow();return Integer.parseInt(location.substring(location.lastIndexOf('=')+1));}
    public static void main(String[] args)throws Exception {
        try(var c=DBContext.connect()){if(!c.getCatalog().contains("_UpgradeTest_"))throw new IllegalStateException("Isolated test database required.");}
        String provider=Config.get("ai.baseUrl","");URI uri=URI.create(provider);
        if(!"http".equals(uri.getScheme())||!"127.0.0.1".equals(uri.getHost()))throw new IllegalStateException("Loopback stub config required.");
        base=args.length==0?"http://127.0.0.1:18090/SuVietTutor":args[0];
        AtomicInteger calls=new AtomicInteger(),grades=new AtomicInteger();AtomicBoolean malformed=new AtomicBoolean(),failed=new AtomicBoolean();AtomicReference<String> seenKey=new AtomicReference<>();
        HttpServer stub=HttpServer.create(new InetSocketAddress("127.0.0.1",uri.getPort()),0);
        stub.createContext("/",exchange->{
            try{
                calls.incrementAndGet();seenKey.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                JsonObject payload=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject schema=payload.getAsJsonObject("generationConfig").getAsJsonObject("responseJsonSchema").getAsJsonObject("properties");
                String result;
                if(failed.get()){byte[] error="{\"error\":{}}".getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(503,error.length);exchange.getResponseBody().write(error);return;}
                if(schema.has("score"))result="{\"readable\":true,\"transcript\":\"Bài làm thử\",\"score\":"+(grades.incrementAndGet()==1?4:10)+",\"feedback\":\"Em cần kiểm tra phép cộng ở bước đầu.\"}";
                else{
                    JsonArray contents=payload.getAsJsonArray("contents");String last=contents.get(contents.size()-1).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString();
                    if(malformed.get())result="{\"accepted\":false,\"complete\":false,\"formula\":\"2+3=5\",\"question\":\"Điền\",\"feedback\":\"\"}";
                    else if(last.equals("5"))result="{\"accepted\":true,\"complete\":false,\"formula\":\"5 × 2 = ___\",\"question\":\"Điền tích\",\"feedback\":\"\"}";
                    else if(last.equals("10"))result="{\"accepted\":true,\"complete\":true,\"formula\":\"\",\"question\":\"\",\"feedback\":\"\"}";
                    else if(last.equals("wrong"))result="{\"accepted\":false,\"complete\":false,\"formula\":\"LEAK_SENTINEL = 999\",\"question\":\"LEAK_SENTINEL\",\"feedback\":\"LEAK_SENTINEL\"}";
                    else result="{\"accepted\":false,\"complete\":false,\"formula\":\"2 + 3 = ___\",\"question\":\"Điền tổng\",\"feedback\":\"\"}";
                }
                JsonObject root=new JsonObject(),candidate=new JsonObject(),content=new JsonObject(),part=new JsonObject();JsonArray candidates=new JsonArray(),parts=new JsonArray();
                part.addProperty("text",result);parts.add(part);content.add("parts",parts);candidate.addProperty("finishReason","STOP");candidate.add("content",content);candidates.add(candidate);root.add("candidates",candidates);
                byte[] body=root.toString().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);
            }finally{exchange.close();}
        });stub.start();
        try{
            Account admin=account("ADMIN"),teacher=account("TEACHER"),student=account("STUDENT"),other=account("STUDENT");
            Browser a=new Browser(),t=new Browser(),s=new Browser(),o=new Browser();a.login(admin);t.login(teacher);s.login(student);o.login(other);
            for(String path:List.of("/admin/settings","/admin/users","/admin/catalog?entity=questions","/admin/courses","/admin/results","/admin/reports")){
                var page=a.get(path);status(page,200,"Admin page "+path);check(!page.body().contains("/teacher/generate"),"Admin menu excludes AI teaching action");status(t.get(path),403,"Teacher blocked "+path);status(s.get(path),403,"Student blocked "+path);
            }
            for(String path:List.of("/teacher/courses","/teacher/generate","/student/tutor","/api/essay-step?answerId=1"))status(a.get(path),403,"Admin cannot use teaching/student action");
            var settings=new LinkedHashMap<String,String>();settings.put("prompt","TEST_GENERAL_PROMPT");settings.put("essay_prompt","TEST_ESSAY_PROMPT");
            for(String prefix:List.of("ai_","essay_ai_")){settings.put(prefix+"url",AiSettingsService.BASE);settings.put(prefix+"model","test-model");settings.put(prefix+"timeout","5");settings.put(prefix+"key",SECRET);}
            status(a.post("/admin/settings",settings,false),403,"Settings CSRF enforced");status(t.post("/admin/settings",settings,true),403,"Teacher cannot update settings");
            status(a.post("/admin/settings",settings,true),302,"Admin saves API settings");
            String stored=new SettingsDao().get("ai_key","");check(!stored.isBlank()&&!stored.contains(SECRET),"API key encrypted in SQL");check(!a.get("/admin/settings").body().contains(SECRET),"API key never echoed to HTML");
            settings.put("ai_key","");settings.put("essay_ai_key","");status(a.post("/admin/settings",settings,true),302,"Blank key retains stored key");check(stored.equals(new SettingsDao().get("ai_key","")),"Blank key preserves ciphertext");
            settings.put("ai_url","http://127.0.0.1/models/");status(a.post("/admin/settings",settings,true),400,"Unsafe endpoint rejected");check(new SettingsDao().get("ai_url","").equals(AiSettingsService.BASE),"Invalid save is atomic");
            // Test fixture permits loopback at the data layer; admin forms never accept it.
            Sql.update("UPDATE app_settings SET setting_value=? WHERE setting_key IN('ai_url','essay_ai_url')",provider);
            int subject=Sql.insert("INSERT INTO subjects(name,grade,created_by) VALUES(N'Essay test',6,?)",teacher.id());
            int lesson=Sql.insert("INSERT INTO lessons(subject_id,title,created_by) VALUES(?,N'Essay lesson',?)",subject,teacher.id());
            int topic=Sql.insert("INSERT INTO topics(lesson_id,title,created_by) VALUES(?,N'Essay topic',?)",lesson,teacher.id());
            int lecture=Sql.insert("INSERT INTO lectures(topic_id,title,body,created_by) VALUES(?,N'Essay lecture',N'Theory',?)",topic,teacher.id());
            int course=Sql.insert("INSERT INTO courses(subject_id,title,summary,objectives,audience,prerequisites,duration_minutes,enrollment_mode,status,created_by) VALUES(?,N'Essay course',N'',N'',N'',N'',60,'DIRECT','PUBLISHED',?)",subject,teacher.id());
            Sql.update("INSERT INTO course_teachers(course_id,teacher_id) VALUES(?,?)",course,teacher.id());
            int item=Sql.insert("INSERT INTO course_items(course_id,lecture_id,section_title,position,is_preview,required) VALUES(?,?,N'',10,0,1)",course,lecture);
            int essay=Sql.insert("INSERT INTO questions(topic_id,kind,prompt,explanation,difficulty,created_by,library_visible) VALUES(?,'ESSAY',N'Tính (2+3) × 2',N'Cộng rồi nhân','EASY',?,1)",topic,teacher.id());
            Sql.update("INSERT INTO course_item_questions(course_item_id,question_id,created_by) VALUES(?,?,?)",item,essay,teacher.id());
            int attempt=redirectId(s.post("/student/start",Map.of("item_id",""+item,"kind","ESSAY"),true));
            var image=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB),"png",image);
            var revisedImage=new ByteArrayOutputStream();var revisedBitmap=new BufferedImage(24,24,BufferedImage.TYPE_INT_RGB);revisedBitmap.setRGB(1,1,0xffffff);ImageIO.write(revisedBitmap,"png",revisedImage);
            int answer=redirectId(s.image("/student/practice?id="+attempt,Map.of("position","0"),image.toByteArray()));
            status(s.post("/api/essay-step",Map.of("answerId",""+answer,"version","0","action","start"),true),400,"Cannot tutor before grading image");
            status(s.image("/student/resubmit",Map.of("id",""+answer,"revision","1"),image.toByteArray()),400,"Cannot bypass steps with image resubmission");
            status(s.post("/student/grade",Map.of("id",""+answer),true),302,"Grade first photo via local provider");check(SECRET.equals(seenKey.get()),"Saved essay API key used immediately");
            var review=s.get("/student/review?id="+answer);status(review,200,"Essay review JSP");check(review.body().contains("data-essay")&&!review.body().contains("data-chat data-answer"),"Essay uses controlled step interface");
            malformed.set(true);status(s.post("/api/essay-step",Map.of("answerId",""+answer,"version","0","action","start"),true),502,"Nonblank answer from provider rejected");check(Sql.intValue(new EssayDao().state(answer),"version")==0,"Malformed response does not advance");malformed.set(false);
            JsonObject state=s.step(answer,0,"start","");check(state.get("step").getAsInt()==1&&state.get("formula").getAsString().contains("___"),"First blank waits for student");
            int count=calls.get();status(s.post("/api/essay-step",Map.of("answerId",""+answer,"version","0","action","answer","value","5"),true),400,"Stale step blocked");check(calls.get()==count,"Stale step causes no provider call");
            state=s.step(answer,1,"answer","wrong");check(state.get("step").getAsInt()==1&&!state.toString().contains("LEAK_SENTINEL"),"Wrong answer preserves blank; discards untrusted replacement");
            Browser second=new Browser();second.login(student);check(JsonParser.parseString(second.get("/api/essay-step?answerId="+answer).body()).getAsJsonObject().get("version").getAsInt()==2,"Progress survives a new session");
            status(o.get("/api/essay-step?answerId="+answer),403,"Other student cannot inspect steps");
            status(s.post("/api/chat",Map.of("answerId",""+answer,"message","Show answer"),true),409,"Generic chat cannot bypass essay workflow");
            state=s.step(answer,2,"answer","5");check(state.get("step").getAsInt()==2&&state.get("phase").getAsString().equals("WAITING"),"Correct answer advances exactly one step");
            state=s.step(answer,3,"answer","10");check(state.get("phase").getAsString().equals("RESUBMIT"),"Final correct step requires new photo");
            count=calls.get();status(s.post("/api/essay-step",Map.of("answerId",""+answer,"version","4","action","answer","value","10"),true),400,"No further steps until photo");check(calls.get()==count,"No AI call after final step");
            status(o.image("/student/resubmit",Map.of("id",""+answer,"revision","1"),image.toByteArray()),403,"Other student cannot replace image");
            status(s.image("/student/resubmit",Map.of("id",""+answer,"revision","1"),image.toByteArray()),400,"Reusing old photo rejected");
            status(s.image("/student/resubmit",Map.of("id",""+answer,"revision","1"),revisedImage.toByteArray()),302,"Resubmit fresh photo");
            var saved=new PracticeDao().answer(answer,student.id(),false);check(saved.get("score")==null&&"PENDING".equals(saved.get("grading_status")),"New photo resets grade to pending");check(new EssayDao().revisions(answer).size()==1&&Sql.intValue(new EssayDao().state(answer),"revision")==2,"Prior photo and grade archived");
            status(s.get("/upload?id="+answer+"&revision=1"),200,"Owner reads previous photo");status(o.get("/upload?id="+answer+"&revision=1"),404,"Other student cannot read old photo");
            status(s.image("/student/resubmit",Map.of("id",""+answer,"revision","1"),image.toByteArray()),400,"Duplicate photo submission rejected");
            status(s.post("/student/grade",Map.of("id",""+answer),true),302,"Grade revised photo");check(((Number)new PracticeDao().answer(answer,student.id(),false).get("score")).doubleValue()==10,"Revised grade persisted");
            check(s.get("/student/review?id="+answer).body().contains("Các lần nộp trước"),"Revision history rendered");status(a.get("/review?id="+answer),200,"Admin inspects business data");
            System.out.println("PASS: "+checks+" admin and essay HTTP checks; isolated SQL and local AI stub only.");
        }finally{stub.stop(0);}
    }
}
