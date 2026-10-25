package vn.suviet;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.Question;
import vn.suviet.util.*;

/** Explicit, idempotent demo/content command; never runs in normal app startup. */
public final class HistoryExperienceSeed {
    public static void main(String[] args)throws Exception {
        if(args.length!=1||!args[0].equals("--apply"))throw new IllegalArgumentException("Use --apply after a verified backup.");
        var data=JsonParser.parseString(Files.readString(Path.of("src/conf/history-study-tools.json"),StandardCharsets.UTF_8)).getAsJsonObject();
        var manifest=JsonParser.parseString(new SettingsDao().get("seed.history6.kntt.v1","{}")).getAsJsonObject();
        if(!manifest.has("items")||manifest.getAsJsonArray("items").size()!=20)throw new IllegalStateException("History 6 curriculum must contain 20 lessons.");
        int course=manifest.get("course_id").getAsInt();
        try(var c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                if(!Sql.query(c,"SELECT setting_key FROM app_settings WITH(UPDLOCK,HOLDLOCK) WHERE setting_key='seed.history6.experience.v1'").isEmpty()){c.rollback();System.out.println("History experience content/demo already applied.");return;}
                int author=Sql.intValue(Sql.query(c,"SELECT created_by FROM courses WHERE id=?",course).get(0),"created_by");
                for(var entry:manifest.getAsJsonArray("items")){
                    var item=entry.getAsJsonObject();int number=item.get("number").getAsInt();
                    var extra=data.getAsJsonArray("lessons").get(number-1).getAsJsonObject();
                    if(extra.get("number").getAsInt()!=number)throw new IllegalStateException("Lesson order mismatch");
                    for(var essay:extra.getAsJsonArray("essays")){
                        var q=essay.getAsJsonObject();
                        Sql.insert(c,"INSERT INTO questions(topic_id,kind,prompt,explanation,difficulty,created_by,library_visible) VALUES(?,'ESSAY',?,?,'MEDIUM',?,1)",item.get("topic_id").getAsInt(),q.get("prompt").getAsString(),q.get("rubric").getAsString(),author);
                    }
                }
                if(!Sql.query(c,"SELECT id FROM users WHERE email='demo.history6@suviet.local'").isEmpty())throw new IllegalStateException("Demo email already exists without seed marker; refusing to modify that account.");
                int uid=Sql.insert(c,"INSERT INTO users(full_name,email,password_hash,role) VALUES(N'Học sinh minh họa – Lịch sử 6','demo.history6@suviet.local',?,'STUDENT')",Passwords.hash("DemoSuViet6!2026"));
                Sql.update(c,"INSERT INTO learner_profiles(user_id,grade) VALUES(?,6)",uid);
                int enrollment=Sql.insert(c,"INSERT INTO enrollments(course_id,student_id,status) VALUES(?,?,'ACTIVE')",course,uid);
                int[] lessonNumbers={1,2,4,7,14,18};int[] correctCounts={2,3,1,4,3,5};
                for(int n=0;n<6;n++){
                    var item=manifest.getAsJsonArray("items").get(lessonNumbers[n]-1).getAsJsonObject();int itemId=item.get("item_id").getAsInt(),topic=item.get("topic_id").getAsInt();
                    var questions=Sql.query(c,"SELECT TOP 5 * FROM questions WHERE topic_id=? AND kind='MCQ' AND deleted=0 ORDER BY id",topic);
                    if(questions.size()!=5)throw new IllegalStateException("Missing MCQ content");
                    int attempt=Sql.insert(c,"INSERT INTO attempts(user_id,topic_id,kind,total,current_pos,course_id,course_item_id,created_at,finished_at) VALUES(?,?,'MCQ',5,5,?,?,DATEADD(DAY,?,SYSUTCDATETIME()),DATEADD(DAY,?,SYSUTCDATETIME()))",uid,topic,course,itemId,-7+n,-7+n);
                    for(int pos=0;pos<5;pos++){
                        var q=Question.from(questions.get(pos));boolean right=pos<correctCounts[n];String chosen=right?q.getCorrectOption():q.getCorrectOption().equals("A")?"B":"A";
                        Sql.insert(c,"INSERT INTO attempt_items(attempt_id,position,question_snapshot) VALUES(?,?,?)",attempt,pos,q.snapshot());
                        Sql.insert(c,"INSERT INTO answers(attempt_id,position,chosen,score,feedback,transcript,grading_status,created_at) VALUES(?,?,?,?,?,N'','RULE_GRADED',DATEADD(DAY,?,SYSUTCDATETIME()))",attempt,pos,chosen,right?10:0,"[Dữ liệu minh họa] "+(right?q.getExplanation():"Câu trả lời chưa đúng. Hãy thử giải thích từng bước với gia sư."),-7+n);
                    }
                    Sql.update(c,"INSERT INTO learning_records(enrollment_id,course_item_id,course_id,completed,bookmarked,note,last_viewed_at) VALUES(?,?,?,1,?,?,DATEADD(DAY,?,SYSUTCDATETIME()))",enrollment,itemId,course,n==2?1:0,n==2?"Ghi chú minh họa: ôn lại các dấu tích về nguồn gốc loài người.":"",-7+n);
                    Sql.update(c,"INSERT INTO learning_activity(enrollment_id,course_item_id,course_id,action,occurred_at) VALUES(?,?,?,'COMPLETE',DATEADD(DAY,?,SYSUTCDATETIME()))",enrollment,itemId,course,-7+n);
                }
                var next=manifest.getAsJsonArray("items").get(2).getAsJsonObject();
                Sql.update(c,"INSERT INTO learning_records(enrollment_id,course_item_id,course_id,note) VALUES(?,?,?,N'Ghi chú minh họa: kiểm tra cách tính thế kỉ.')",enrollment,next.get("item_id").getAsInt(),course);
                SettingsDao.put(c,"seed.history6.experience.v1",Web.JSON.toJson(Map.of("course_id",course,"student_id",uid,"synthetic_demo",true)));
                CourseDao.audit(c,author,"SEED_HISTORY6_EXPERIENCE",course);
                c.commit();System.out.println("Applied 40 essay questions and a clearly labeled demo learner with 6 completed MCQ attempts. Existing learners unchanged.");
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
