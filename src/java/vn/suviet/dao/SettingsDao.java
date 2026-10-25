package vn.suviet.dao;
import java.sql.SQLException;
import java.sql.Connection;
import vn.suviet.util.Config;
public final class SettingsDao {
    public String prompt()throws SQLException{
        var r=Sql.one("SELECT setting_value FROM app_settings WHERE setting_key=?","system_prompt");
        return r==null?Config.defaultPrompt():(String)r.get("setting_value");
    }
    public void savePrompt(String value)throws SQLException{
        try(var c=vn.suviet.util.DBContext.connect()){put(c,"system_prompt",value);}
    }
    public String get(String key,String fallback)throws SQLException{
        var row=Sql.one("SELECT setting_value FROM app_settings WHERE setting_key=?",key);
        return row==null?fallback:(String)row.get("setting_value");
    }
    public static void put(Connection c,String key,String value)throws SQLException{
        if(Sql.update(c,"UPDATE app_settings WITH (UPDLOCK,SERIALIZABLE) SET setting_value=? WHERE setting_key=?",value,key)==0)
            Sql.update(c,"INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?)",key,value);
    }
}
