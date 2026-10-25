package vn.suviet.service;

import java.sql.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.util.DBContext;

/**
 * Administrators moderate teaching content instead of authoring it: they may hide or restore
 * a lecture/question, must give a reason when hiding, and every action is audited.
 */
public final class ModerationService {
    public void moderate(Entity e,int id,int adminId,boolean hide,String reason)throws SQLException{
        if(!ContentAccessService.isContent(e))throw new IllegalArgumentException("Chỉ ẩn/gỡ được bài giảng hoặc câu hỏi.");
        if(!ContentAccessService.admin(adminId))throw new AccessDeniedException("Chỉ quản trị viên được ẩn hoặc gỡ nội dung.");
        String note=reason==null?"":reason.trim();
        if(hide&&note.length()<10)throw new IllegalArgumentException("Phải ghi lý do ít nhất 10 ký tự khi ẩn nội dung.");
        if(note.length()>500)throw new IllegalArgumentException("Lý do tối đa 500 ký tự.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                int rows=Sql.update(c,"UPDATE "+e.table+" SET hidden=?,"
                    +"hidden_reason=CASE WHEN ?=1 THEN ? ELSE NULL END,"
                    +"hidden_by=CASE WHEN ?=1 THEN ? ELSE NULL END,"
                    +"hidden_at=CASE WHEN ?=1 THEN SYSUTCDATETIME() ELSE NULL END WHERE id=? AND deleted=0",
                    hide,hide,note,hide,adminId,hide,id);
                if(rows!=1)throw new IllegalArgumentException("Không tìm thấy nội dung cần xử lý.");
                Sql.insert(c,"INSERT INTO audit_logs(actor_id,action,resource_id,detail) VALUES(?,?,?,?)",
                    adminId,(hide?"CONTENT_HIDE_":"CONTENT_UNHIDE_")+e.table,id,note);
                c.commit();
            }catch(Exception ex){
                c.rollback();
                throw ex;
            }
        }
    }
}
