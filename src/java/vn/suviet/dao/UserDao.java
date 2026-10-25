package vn.suviet.dao;
import java.sql.*;
import java.util.*;
import vn.suviet.util.Passwords;
public final class UserDao {
    public Map<String,Object> byEmail(String email)throws SQLException{
        return Sql.one("SELECT * FROM users WHERE email=? AND deleted=0",email);
    }
    public Map<String,Object> byId(int id)throws SQLException{
        return Sql.one("SELECT * FROM users WHERE id=? AND deleted=0",id);
    }
    public List<Map<String,Object>> list(String search,int page)throws SQLException{
        return Sql.query("SELECT id,full_name,email,role,locked FROM users WHERE deleted=0 AND role IN ('TEACHER','STUDENT') AND (full_name LIKE ? OR email LIKE ?) ORDER BY id DESC OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY","%"+search+"%","%"+search+"%",(page-1)*10);
    }
    public int count(String search)throws SQLException{
        return Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM users WHERE deleted=0 AND role IN ('TEACHER','STUDENT') AND (full_name LIKE ? OR email LIKE ?)","%"+search+"%","%"+search+"%"),"n");
    }
    public int create(String name,String email,String password,String role)throws SQLException{
        return Sql.insert("INSERT INTO users(full_name,email,password_hash,role) VALUES(?,?,?,?)",name,email,Passwords.hash(password),role);
    }
    public void edit(int id,String name,String email,String role,String password)throws SQLException{
        if(password.isEmpty()) Sql.update("UPDATE users SET full_name=?,email=?,role=? WHERE id=? AND role<>'ADMIN' AND deleted=0",name,email,role,id);
        else Sql.update("UPDATE users SET full_name=?,email=?,role=?,password_hash=? WHERE id=? AND role<>'ADMIN' AND deleted=0",name,email,role,Passwords.hash(password),id);
    }
    public void lock(int id,boolean locked)throws SQLException{
        Sql.update("UPDATE users SET locked=? WHERE id=? AND role<>'ADMIN' AND deleted=0",locked?1:0,id);
    }
    public void delete(int id)throws SQLException{
        Sql.update("UPDATE users SET deleted=1,locked=1 WHERE id=? AND role<>'ADMIN'",id);
    }
}
