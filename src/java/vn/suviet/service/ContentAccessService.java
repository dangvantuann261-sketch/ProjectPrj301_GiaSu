package vn.suviet.service;

import java.sql.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;

public final class ContentAccessService {
    public static boolean admin(int uid)throws SQLException {
        var u=new UserDao().byId(uid);
        return u!=null&&"ADMIN".equals(u.get("role"))&&Sql.intValue(u,"locked")==0;
    }
    /** Lectures and questions are teaching content: authored and owned by teachers only. */
    public static boolean isContent(Entity e){
        return e==Entity.LECTURES||e==Entity.QUESTIONS;
    }
    /**
     * Teaching content can only be changed by its author. Category rows (subject/lesson/topic)
     * stay editable by administrators; the old administrator shortcut for content is gone.
     */
    public static void edit(Connection c,Entity entity,int id,int uid)throws SQLException {
        var rows=Sql.query(c,"SELECT created_by FROM "+entity.table+" WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND deleted=0",id);
        if(rows.isEmpty())throw new AccessDeniedException();
        int owner=Sql.intValue(rows.get(0),"created_by");
        if(isContent(entity)){
            if(owner!=uid)throw new AccessDeniedException("Chỉ tác giả được sửa nội dung này. Hãy dùng chức năng sao chép bản chia sẻ để tạo bản riêng.");
            return;
        }
        if(!admin(uid)&&owner!=uid)throw new AccessDeniedException();
    }
    /** True when the content already reaches learners, so edits must create a new version. */
    public static boolean inUse(Connection c,Entity entity,int id)throws SQLException {
        if(entity==Entity.LECTURES)return Sql.query(c,"SELECT TOP 1 id FROM course_items WHERE lecture_id=?",id).size()>0;
        if(entity==Entity.QUESTIONS)return Sql.query(c,"SELECT TOP 1 question_id FROM course_item_questions WHERE question_id=?",id).size()>0;
        return false;
    }
    public static void parent(Entity entity,int id,int uid)throws SQLException {
        if(entity==Entity.SUBJECTS)return;
        var row=new CatalogDao().get(entity,id);
        if(row==null)throw new AccessDeniedException();
        if(admin(uid))return;
        int owner=Sql.intValue(row,"created_by");
        if(owner==uid)return;
        // Chủ đề do ADMIN tạo thuộc danh mục dùng chung; giáo viên được gắn bài giảng/câu hỏi vào, không sửa chủ đề.
        if(entity==Entity.TOPICS&&Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM users WHERE id=? AND role='ADMIN' AND deleted=0",owner),"n")>0)return;
        throw new AccessDeniedException();
    }
}
