package vn.suviet.service;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.util.*;
public final class CatalogService {
    private final CatalogDao dao=new CatalogDao();
    public Map<String,Object> validate(HttpServletRequest r,Entity e)throws SQLException{
        Map<String,Object> m=new LinkedHashMap<>();
        if(e.parent!=null){
            int id=Web.positive(r,e.parentColumn);
            if(dao.get(Entity.parse(e.parent),id)==null)throw new IllegalArgumentException("Mục cha không tồn tại.");
            m.put(e.parentColumn,id);
        }
        for(String field:e.fields){
            if(field.equals(e.parentColumn))continue;
            if(field.equals("grade")){
                int grade=Web.number(r,field,6);
                if(grade<6||grade>12)throw new IllegalArgumentException("Lớp phải từ 6 đến 12.");
                m.put(field,grade);
            }
            else {
                int max=field.equals("body")?30000:field.equals("explanation")?8000:field.equals("prompt")?4000:field.equals("description")?2000:field.startsWith("option_")?1000:field.equals("video_url")?2000:255;
                String value=Web.text(r,field,max,field.equals(e.title));
                if(field.equals("video_url"))value=Web.safeUrl(value);
                m.put(field,value);
            }
        }
        if(e==Entity.QUESTIONS){
            Map<String,Object> q=new LinkedHashMap<>(m);
            q.put("id",0);
            Question.from(q).validate();
        }
        if(ContentAccessService.isContent(e))m.put("library_visible","on".equals(r.getParameter("library_visible")));
        return m;
    }
    /**
     * Saves category rows for administrators and teaching content for its author.
     * Content that already reaches learners is never mutated: a new version row is created
     * and the original stays intact for existing curricula and attempt history.
     */
    public int save(Entity e,int id,Map<String,Object> m,int uid)throws SQLException{
        boolean content=ContentAccessService.isContent(e);
        if(content&&ContentAccessService.admin(uid))
            throw new AccessDeniedException("Quản trị viên không trực tiếp soạn, sửa bài giảng, câu hỏi, đáp án hoặc hướng dẫn chấm.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                if(e.parent!=null)ContentAccessService.parent(Entity.parse(e.parent),((Number)m.get(e.parentColumn)).intValue(),uid);
                int saved;
                if(id>0){
                    ContentAccessService.edit(c,e,id,uid);
                    saved=content&&ContentAccessService.inUse(c,e,id)?version(c,e,id,m,uid):dao.save(c,e,id,m,uid);
                }else{
                    saved=dao.save(c,e,0,m,uid);
                }
                if(content)Sql.update(c,"UPDATE "+e.table+" SET library_visible=? WHERE id=?",Boolean.TRUE.equals(m.get("library_visible")),saved);
                audit(c,uid,"CONTENT_SAVE_"+e.table,saved,"");
                c.commit();
                return saved;
            }catch(Exception ex){
                c.rollback();
                throw ex;
            }
        }
    }
    private int version(Connection c,Entity e,int sourceId,Map<String,Object> m,int uid)throws SQLException{
        var source=dao.get(e,sourceId);
        int next=Sql.intValue(source,"version")+1;
        int saved=dao.save(c,e,0,m,uid);
        String table=e.table;
        // The new version inherits the moderation state: content hidden by an administrator cannot reappear.
        Sql.update(c,"UPDATE "+table+" SET version=?,root_id=ISNULL((SELECT root_id FROM "+table+" WHERE id=?),?),"
            +"hidden=ISNULL((SELECT hidden FROM "+table+" WHERE id=?),0),"
            +"hidden_reason=(SELECT hidden_reason FROM "+table+" WHERE id=?),"
            +"hidden_by=(SELECT hidden_by FROM "+table+" WHERE id=?),"
            +"hidden_at=(SELECT hidden_at FROM "+table+" WHERE id=?) WHERE id=?",
            next,sourceId,sourceId,sourceId,sourceId,sourceId,sourceId,saved);
        Sql.update(c,"UPDATE "+table+" SET superseded=1 WHERE id=?",sourceId);
        if(e==Entity.QUESTIONS){
            // Practice links follow the newest version; submitted attempts already hold a snapshot.
            Sql.update(c,"UPDATE course_item_questions SET question_id=? WHERE question_id=?",saved,sourceId);
        }else{
            // Draft curricula without learners move to the new version. Published cohorts keep the old one.
            Sql.update(c,"UPDATE i SET lecture_id=? FROM course_items i JOIN courses co ON co.id=i.course_id WHERE i.lecture_id=? AND co.status='DRAFT'"
                +" AND NOT EXISTS(SELECT 1 FROM enrollments en WHERE en.course_id=co.id)"
                +" AND NOT EXISTS(SELECT 1 FROM course_items x WHERE x.course_id=co.id AND x.lecture_id=?)",saved,sourceId,saved);
        }
        audit(c,uid,"CONTENT_VERSION_"+table,saved,"Phien ban "+next+" tu #"+sourceId);
        return saved;
    }
    /** Copies a shared lecture/question into the teacher's own library, keeping the source reference. */
    public int copy(Entity e,int sourceId,int uid)throws SQLException{
        if(!ContentAccessService.isContent(e))throw new IllegalArgumentException("Chỉ sao chép được bài giảng hoặc câu hỏi.");
        if(ContentAccessService.admin(uid))throw new AccessDeniedException("Quản trị viên không sao chép nội dung giảng dạy.");
        var source=dao.get(e,sourceId);
        if(source==null)throw new IllegalArgumentException("Không tìm thấy nội dung cần sao chép.");
        if(!CourseService.flag(source.get("library_visible"))||CourseService.flag(source.get("hidden")))
            throw new AccessDeniedException("Nội dung này không được chia sẻ để tái sử dụng.");
        Map<String,Object> m=new LinkedHashMap<>();
        for(String field:e.fields)m.put(field,source.get(field));
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                int saved=dao.save(c,e,0,m,uid);
                Sql.update(c,"UPDATE "+e.table+" SET copied_from_id=?,library_visible=0,version=1,root_id=NULL,hidden=0,hidden_reason=NULL,hidden_by=NULL,hidden_at=NULL WHERE id=?",sourceId,saved);
                audit(c,uid,"CONTENT_COPY_"+e.table,saved,"Sao chep tu #"+sourceId);
                c.commit();
                return saved;
            }catch(Exception ex){
                c.rollback();
                throw ex;
            }
        }
    }
    public void approve(List<Question> drafts,int uid)throws SQLException{
        if(drafts.isEmpty()||drafts.size()>10)throw new IllegalArgumentException("Chỉ duyệt từ 1 đến 10 câu.");
        for(Question q:drafts){
            q.validate();
            if(dao.get(Entity.TOPICS,q.getTopicId())==null)throw new IllegalArgumentException("Chủ đề không tồn tại.");
            ContentAccessService.parent(Entity.TOPICS,q.getTopicId(),uid);
        }
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                for(Question q:drafts)dao.save(c,Entity.QUESTIONS,0,q.values(),uid);
                c.commit();
            }
            catch(Exception ex){
                c.rollback();
                throw ex;
            }
        }
    }
    private static void audit(Connection c,int uid,String action,int id,String detail)throws SQLException{
        Sql.insert(c,"INSERT INTO audit_logs(actor_id,action,resource_id,detail) VALUES(?,?,?,?)",uid,action,id,detail);
    }
}
