package vn.suviet.dao;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.sql.*;
import java.util.*;
import vn.suviet.service.AIClient;
import vn.suviet.util.DBContext;

/** A database lease prevents overlapping requests from different browser sessions. */
public final class ChatDao {
    private static final Gson JSON=new Gson();
    public record Lease(String id,List<AIClient.Message> messages,boolean reset) {}
    private List<AIClient.Message> decode(Object value){
        return JSON.fromJson(value.toString(),new TypeToken<ArrayList<AIClient.Message>>(){}.getType());
    }
    public List<AIClient.Message> history(int uid,String key)throws SQLException {
        var row=Sql.one("SELECT messages FROM tutor_conversations WHERE user_id=? AND context_key=?",uid,key);
        return row==null?List.of():decode(row.get("messages"));
    }
    public Lease claim(int uid,String key,boolean reset)throws SQLException {
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var rows=Sql.query(c,"SELECT messages,CASE WHEN busy_until>SYSUTCDATETIME() THEN 1 ELSE 0 END AS busy FROM tutor_conversations WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND context_key=?",uid,key);
                if(rows.isEmpty())Sql.update(c,"INSERT INTO tutor_conversations(user_id,context_key) VALUES(?,?)",uid,key);
                else if(Sql.intValue(rows.get(0),"busy")==1){c.rollback();return null;}
                var history=reset||rows.isEmpty()?new ArrayList<AIClient.Message>():decode(rows.get(0).get("messages"));
                if(history.size()>=40)throw new IllegalArgumentException("Hội thoại đã đạt 20 lượt. Bấm Bắt đầu lại để hỏi tiếp; lịch sử cũ vẫn được lưu.");
                String token=UUID.randomUUID().toString();
                Sql.update(c,"UPDATE tutor_conversations SET lease_id=?,busy_until=DATEADD(MINUTE,3,SYSUTCDATETIME()) WHERE user_id=? AND context_key=?",token,uid,key);
                c.commit();return new Lease(token,history,reset);
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
    public boolean finish(int uid,String key,Lease lease,List<AIClient.Message> messages)throws SQLException {
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                var rows=Sql.query(c,"SELECT messages FROM tutor_conversations WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND context_key=? AND lease_id=?",uid,key,lease.id());
                if(rows.isEmpty()){c.rollback();return false;}
                if(lease.reset()&&!"[]".equals(rows.get(0).get("messages")))Sql.insert(c,"INSERT INTO tutor_chat_archives(user_id,context_key,messages) VALUES(?,?,?)",uid,key,rows.get(0).get("messages"));
                Sql.update(c,"UPDATE tutor_conversations SET messages=?,lease_id=NULL,busy_until=DATEADD(SECOND,2,SYSUTCDATETIME()),updated_at=SYSUTCDATETIME() WHERE user_id=? AND context_key=?",JSON.toJson(messages),uid,key);
                c.commit();return true;
            }catch(Exception ex){c.rollback();throw ex;}
        }
    }
    public void release(int uid,String key,Lease lease)throws SQLException {
        Sql.update("UPDATE tutor_conversations SET lease_id=NULL,busy_until=DATEADD(SECOND,2,SYSUTCDATETIME()) WHERE user_id=? AND context_key=? AND lease_id=?",uid,key,lease.id());
    }
}
