package vn.suviet.dao;
import vn.suviet.model.*;
import java.sql.*;
import java.util.*;
public final class CatalogDao {
    public List<Map<String,Object>> list(Entity e,String search,int page,int uid,boolean admin)throws SQLException{
        return Sql.query("SELECT * FROM "+e.table+" WHERE deleted=0 AND (?=1 OR created_by=?) AND "+e.title+" LIKE ? ORDER BY id DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY",admin?1:0,uid,"%"+search+"%",(page-1)*10);
    }
    public int count(Entity e,String search,int uid,boolean admin)throws SQLException{
        return Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM "+e.table+" WHERE deleted=0 AND (?=1 OR created_by=?) AND "+e.title+" LIKE ?",admin?1:0,uid,"%"+search+"%"),"n");
    }
    public List<Map<String,Object>> choices(Entity e,int uid,boolean admin)throws SQLException{
        // Danh mục do ADMIN tạo là dữ liệu dùng chung: giáo viên được gắn bài giảng/câu hỏi vào chủ đề đó.
        if(e==Entity.TOPICS&&!admin)return Sql.query("SELECT id,title AS label FROM topics WHERE deleted=0 AND (created_by=? OR created_by IN(SELECT id FROM users WHERE role='ADMIN' AND deleted=0)) ORDER BY id",uid);
        return Sql.query("SELECT id,"+e.title+" AS label FROM "+e.table+" WHERE deleted=0 AND (?=1 OR created_by=?) ORDER BY id",admin||e==Entity.SUBJECTS?1:0,uid);
    }
    // Tên bảng/cột chỉ lấy từ enum nội bộ, giá trị luôn đi qua PreparedStatement.
    public List<Map<String,Object>> list(Entity e,String search,int page)throws SQLException{
        return Sql.query("SELECT * FROM "+e.table+" WHERE deleted=0 AND "+e.title+" LIKE ? ORDER BY id DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY","%"+search+"%",(page-1)*10);
    }
    public int count(Entity e,String search)throws SQLException{
        return Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM "+e.table+" WHERE deleted=0 AND "+e.title+" LIKE ?","%"+search+"%"),"n");
    }
    public List<Map<String,Object>> shared(Entity e,int uid)throws SQLException{
        return Sql.query("SELECT * FROM "+e.table+" WHERE deleted=0 AND hidden=0 AND library_visible=1 AND created_by<>? ORDER BY id DESC",uid);
    }
    public List<Map<String,Object>> shared(Entity e)throws SQLException{
        return Sql.query("SELECT * FROM "+e.table+" WHERE deleted=0 AND hidden=0 AND library_visible=1 ORDER BY id DESC");
    }
    public Map<String,Object> get(Entity e,int id)throws SQLException{
        return Sql.one("SELECT * FROM "+e.table+" WHERE id=? AND deleted=0",id);
    }
    public List<Map<String,Object>> choices(Entity e)throws SQLException{
        return Sql.query("SELECT id,"+e.title+" AS label FROM "+e.table+" WHERE deleted=0 ORDER BY id");
    }
    public int save(Connection c,Entity e,int id,Map<String,Object> data,int userId)throws SQLException{
        List<Object> values=new ArrayList<>();
        for(String f:e.fields)values.add(data.get(f));
        if(id==0){
            values.add(userId);
            return Sql.insert(c,"INSERT INTO "+e.table+" ("+String.join(",",e.fields)+",created_by) VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")",values.toArray());
        }
        values.add(id);
        StringJoiner fields=new StringJoiner(",");
        for(String f:e.fields)fields.add(f+"=?");
        if(Sql.update(c,"UPDATE "+e.table+" SET "+fields+" WHERE id=? AND deleted=0",values.toArray())!=1)throw new IllegalArgumentException("Không tìm thấy bản ghi.");
        return id;
    }
    public void delete(Entity e,int id,int uid)throws SQLException{
        try(Connection c=vn.suviet.util.DBContext.connect()){
            c.setAutoCommit(false);
            try{
                vn.suviet.service.ContentAccessService.edit(c,e,id,uid);
                if(vn.suviet.service.ContentAccessService.isContent(e)&&vn.suviet.service.ContentAccessService.inUse(c,e,id))
                    throw new IllegalArgumentException("Nội dung đang được chương trình sử dụng. Hãy tạo phiên bản mới hoặc nhờ quản trị viên ẩn nội dung.");
                for(Entity child:Entity.values())if(e.table.equals(child.parent)){
                    if(!Sql.query(c,"SELECT id FROM "+child.table+" WHERE deleted=0 AND "+child.parentColumn+"=?",id).isEmpty())throw new IllegalArgumentException("Hãy xóa các "+child.label.toLowerCase()+" thuộc mục này trước.");
                }
                if(e==Entity.SUBJECTS&&!Sql.query(c,"SELECT id FROM courses WHERE subject_id=?",id).isEmpty())throw new IllegalArgumentException("Môn học đang có khóa học.");
                Sql.update(c,"UPDATE "+e.table+" SET deleted=1 WHERE id=?",id);
                CourseDao.audit(c,uid,"CONTENT_DELETE_"+e.table,id);c.commit();
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
}
