package vn.suviet;
import vn.suviet.util.*;
import vn.suviet.model.*;
import java.util.*;
public final class CoreTests {
    private static int checks;
    private static void check(boolean result,String message){
        checks++;
        if(!result)throw new AssertionError(message);
    }
    public static void main(String[] args){
        String password="MậtKhẩu#2026",a=Passwords.hash(password),b=Passwords.hash(password);
        check(!a.equals(b),"Salt phải ngẫu nhiên");
        check(Passwords.verify(password,a),"Đúng mật khẩu");
        check(!Passwords.verify("sai",a),"Sai mật khẩu");
        check(!Passwords.verify(password,"broken"),"Hash hỏng");
        check(Web.youtubeEmbed("https://youtu.be/abcdefghijk").equals("https://www.youtube-nocookie.com/embed/abcdefghijk"),"YouTube short URL");
        check(Web.youtubeEmbed("https://www.youtube.com/watch?v=abcdefghijk&t=10").endsWith("abcdefghijk"),"YouTube watch URL");
        check(Web.youtubeEmbed("https://youtube.com.evil.example/watch?v=abcdefghijk").isEmpty(),"Chặn giả mạo host YouTube");
        Map<String,Object> lesson=new HashMap<>();
        lesson.put("body","Bài đọc\n\n[Nguồn bài học] https://loigiaihay.com/example.html");
        lesson.put("video_url","https://www.youtube.com/watch?v=abcdefghijk");
        Web.prepareLecture(lesson);
        check(lesson.get("body").equals("Bài đọc"),"Nguồn được tách khỏi bài đọc");
        check(lesson.get("source_url").equals("https://loigiaihay.com/example.html"),"Nguồn bài học được giữ");
        check(lesson.get("embed").equals("https://www.youtube-nocookie.com/embed/abcdefghijk"),"Video nhúng ngay trong bài");
        Map<String,Object> spoof=new HashMap<>();
        spoof.put("body","Bài đọc\n[Nguồn bài học] https://loigiaihay.com.evil.example/a.html");
        Web.prepareLecture(spoof);
        check(!spoof.containsKey("source_url") && spoof.get("body").toString().contains("evil.example"),"Không tạo liên kết nguồn giả mạo");
        boolean blocked=false;
        try{
            Web.safeUrl("javascript:alert(1)");
        }
        catch(IllegalArgumentException e){
            blocked=true;
        }
        check(blocked,"Chặn URL script");
        blocked=false;
        try{
            Entity.parse("users; DROP TABLE users");
        }
        catch(IllegalArgumentException e){
            blocked=true;
        }
        check(blocked,"Tên bảng phải qua allowlist");
        Question q=Question.restore("{\"id\":1,\"topicId\":2,\"kind\":\"MCQ\",\"prompt\":\"Một thế kỷ?\",\"optionA\":\"10\",\"optionB\":\"100\",\"optionC\":\"1000\",\"optionD\":\"1\",\"correctOption\":\"B\",\"explanation\":\"100 năm\",\"difficulty\":\"EASY\"}");
        q.validate();
        check(Question.restore(q.snapshot()).getCorrectOption().equals("B"),"Bản chụp câu hỏi giữ đáp án");
        blocked=false;
        try{
            Question.restore(q.snapshot().replace("\"correctOption\":\"B\"","\"correctOption\":\"X\"")).validate();
        }
        catch(IllegalArgumentException e){
            blocked=true;
        }
        check(blocked,"Chặn đáp án không hợp lệ");
        check(vn.suviet.service.CourseService.progress(0,0)==0,"Khóa rỗng không chia cho 0");
        check(vn.suviet.service.CourseService.progress(1,3)==33,"Tiến độ theo bài bắt buộc");
        check(vn.suviet.service.CourseService.progress(8,4)==100,"Tiến độ không vượt 100%");
        check(vn.suviet.service.CourseService.progress(-1,4)==0,"Tiến độ không âm");
        check(vn.suviet.service.CourseService.flag(Boolean.TRUE),"SQL BIT boolean");
        check(vn.suviet.service.CourseService.flag(1),"SQL COALESCE integer");
        check(!vn.suviet.service.CourseService.flag(null),"Chưa có tiến độ");
        vn.suviet.service.AiSettingsService.validate("https://generativelanguage.googleapis.com/v1beta/models/","test-model",45,"");
        for(String bad:List.of("http://example.com/models/","https://example.com/models/?key=secret","https://127.0.0.1/models/","https://user:secret@example.com/models/","https://example.com/v1/")){
            blocked=false;try{vn.suviet.service.AiSettingsService.validate(bad,"test",45,"");}catch(IllegalArgumentException expected){blocked=true;}
            check(blocked,"Chặn cấu hình link API không hợp lệ");
        }
        try{
            var step=vn.suviet.service.TutorService.parseEssayStep("{\"accepted\":false,\"complete\":false,\"formula\":\"a+b=___\",\"question\":\"Điền tổng\",\"feedback\":\"\"}",true);
            check(step.formula().contains("___")&&!step.complete(),"Bước đầu phải chờ học sinh tự điền");
            for(String invalid:List.of("{\"accepted\":true,\"complete\":true,\"formula\":\"\",\"question\":\"\",\"feedback\":\"\"}","{\"accepted\":false,\"complete\":false,\"formula\":\"a+b=42\",\"question\":\"Điền tổng\",\"feedback\":\"\"}")){
                blocked=false;try{vn.suviet.service.TutorService.parseEssayStep(invalid,true);}catch(vn.suviet.service.AIClient.AIException expected){blocked=true;}
                check(blocked,"Chặn bước đầu tiết lộ đáp số hoặc bỏ qua hướng dẫn");
            }
        }catch(Exception e){throw new AssertionError(e);}
        System.out.println("PASS: "+checks+" core checks");
    }
}
