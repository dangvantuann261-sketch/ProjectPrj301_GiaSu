package vn.suviet.dao;

import java.util.*;
import vn.suviet.service.AccessDeniedException;
import vn.suviet.util.DBContext;

/** Durable tutoring state; leases prevent duplicated provider calls and concurrent photo replacement. */
public final class EssayDao {
    public Map<String,Object> state(int answer)throws Exception {
        return Sql.one("SELECT * FROM essay_tutoring WHERE answer_id=?",answer);
    }
    public List<Map<String,Object>> revisions(int answer)throws Exception {
        return Sql.query("SELECT revision,score,feedback,grading_status,archived_at FROM essay_answer_revisions WHERE answer_id=? ORDER BY revision DESC",answer);
    }
    public boolean claim(int answer,int uid,int version,String lease)throws Exception {
        return Sql.update("UPDATE essay_tutoring SET lease_id=?,busy_until=DATEADD(MINUTE,2,SYSUTCDATETIME()) WHERE answer_id=? AND version=? AND (busy_until IS NULL OR busy_until<SYSUTCDATETIME()) AND phase<>'RESUBMIT' AND EXISTS(SELECT 1 FROM answers ans JOIN attempts a ON a.id=ans.attempt_id WHERE ans.id=essay_tutoring.answer_id AND a.user_id=? AND ans.grading_status='AI_GRADED' AND ans.score<10)",lease,answer,version,uid)==1;
    }
    public void release(int answer,String lease)throws Exception {
        Sql.update("UPDATE essay_tutoring SET lease_id=NULL,busy_until=NULL WHERE answer_id=? AND lease_id=?",answer,lease);
    }
    public void finish(int answer,String lease,String phase,int step,String formula,String question,String history)throws Exception {
        if(Sql.update("UPDATE essay_tutoring SET phase=?,step_no=?,formula=?,question=?,history=?,version=version+1,lease_id=NULL,busy_until=NULL,updated_at=SYSUTCDATETIME() WHERE answer_id=? AND lease_id=?",phase,step,formula,question,history,answer,lease)!=1)
            throw new IllegalArgumentException("Bước đã thay đổi ở cửa sổ khác. Hãy tải lại trang.");
    }
    public void resubmit(int answer,int uid,int revision,String file,String mime)throws Exception {
        try(var c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var rows=Sql.query(c,"SELECT e.*,ans.grading_status FROM essay_tutoring e WITH (UPDLOCK,HOLDLOCK) JOIN answers ans WITH (UPDLOCK,HOLDLOCK) ON ans.id=e.answer_id JOIN attempts a ON a.id=ans.attempt_id WHERE e.answer_id=? AND a.user_id=? AND a.kind='ESSAY'",answer,uid);
                if(rows.isEmpty())throw new AccessDeniedException();
                var row=rows.get(0);
                if(Sql.intValue(row,"revision")!=revision)throw new IllegalArgumentException("Ảnh đã được nộp lại ở cửa sổ khác. Hãy tải lại trang.");
                if(!"RESUBMIT".equals(row.get("phase"))&&!"MANUAL_REQUIRED".equals(row.get("grading_status")))throw new IllegalArgumentException("Hoàn thành từng bước hướng dẫn trước khi nộp ảnh làm lại.");
                if("PROCESSING".equals(row.get("grading_status")))throw new IllegalArgumentException("AI đang xử lý ảnh; hãy chờ kết quả.");
                Sql.update(c,"INSERT INTO essay_answer_revisions(answer_id,revision,upload_name,upload_mime,score,feedback,transcript,grading_status,tutoring_history) SELECT ans.id,e.revision,ans.upload_name,ans.upload_mime,ans.score,ans.feedback,ans.transcript,ans.grading_status,e.history FROM answers ans JOIN essay_tutoring e ON e.answer_id=ans.id WHERE ans.id=?",answer);
                Sql.update(c,"UPDATE answers SET upload_name=?,upload_mime=?,score=NULL,transcript=N'',feedback=N'Đã lưu ảnh làm lại. Hãy nhận đánh giá AI cho ảnh mới.',grading_status='PENDING',grading_started_at=NULL WHERE id=?",file,mime,answer);
                Sql.update(c,"UPDATE essay_tutoring SET revision=revision+1,version=version+1,phase='NEW',step_no=0,formula=N'',question=N'',history=N'[]',lease_id=NULL,busy_until=NULL,updated_at=SYSUTCDATETIME() WHERE answer_id=?",answer);
                Sql.update(c,"INSERT INTO audit_logs(actor_id,action,resource_id) VALUES(?,'ESSAY_RESUBMITTED',?)",uid,answer);
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
}
