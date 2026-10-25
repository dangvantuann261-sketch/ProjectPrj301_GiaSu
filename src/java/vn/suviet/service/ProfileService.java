package vn.suviet.service;

import java.sql.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.User;
import vn.suviet.util.*;

public final class ProfileService {
    public Map<String,Object> get(int uid)throws SQLException {return Sql.one("SELECT u.full_name,u.email,p.grade FROM users u LEFT JOIN learner_profiles p ON p.user_id=u.id WHERE u.id=? AND u.deleted=0",uid);}
    public List<Map<String,Object>> subjects(int uid)throws SQLException {return Sql.query("SELECT s.id,s.name,s.grade,CASE WHEN f.user_id IS NULL THEN 0 ELSE 1 END AS followed FROM subjects s LEFT JOIN subject_follows f ON f.subject_id=s.id AND f.user_id=? WHERE s.deleted=0 ORDER BY s.name,s.grade",uid);}
    public void save(User u,String name,int grade,Set<Integer> subjects)throws SQLException {
        if(!u.getRole().equals("STUDENT"))throw new AccessDeniedException();
        if(name==null||name.isBlank()||name.length()>100||grade<6||grade>12||subjects.size()>50)throw new IllegalArgumentException("Thông tin hồ sơ không hợp lệ.");
        try(Connection c=DBContext.connect()){
            c.setAutoCommit(false);
            try{
                Sql.query(c,"SELECT id FROM users WITH(UPDLOCK,HOLDLOCK) WHERE id=?",u.getId());
                Sql.update(c,"UPDATE users SET full_name=? WHERE id=?",name,u.getId());
                if(Sql.update(c,"UPDATE learner_profiles SET grade=? WHERE user_id=?",grade,u.getId())==0)Sql.update(c,"INSERT INTO learner_profiles(user_id,grade) VALUES(?,?)",u.getId(),grade);
                Sql.update(c,"DELETE FROM subject_follows WHERE user_id=?",u.getId());
                for(int subject:subjects){
                    if(Sql.query(c,"SELECT id FROM subjects WHERE id=? AND deleted=0",subject).isEmpty())throw new IllegalArgumentException("Môn quan tâm không hợp lệ.");
                    Sql.update(c,"INSERT INTO subject_follows(user_id,subject_id) VALUES(?,?)",u.getId(),subject);
                }
                c.commit();
            }catch(Exception e){c.rollback();throw e;}
        }
    }
    public void password(User u,String current,String next)throws SQLException {
        Passwords.validate(next);
        var row=new UserDao().byId(u.getId());
        if(current==null||current.length()>128||row==null||!Passwords.verify(current,(String)row.get("password_hash")))throw new IllegalArgumentException("Mật khẩu hiện tại chưa đúng.");
        if(Sql.update("UPDATE users SET password_hash=? WHERE id=? AND password_hash=?",Passwords.hash(next),u.getId(),row.get("password_hash"))!=1)throw new IllegalArgumentException("Mật khẩu đã thay đổi. Hãy đăng nhập lại.");
    }
}