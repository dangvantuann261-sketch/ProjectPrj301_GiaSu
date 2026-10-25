package vn.suviet.dao;
import java.sql.*;
import java.util.*;
import vn.suviet.util.DBContext;
public final class Sql {
    private Sql() {
    }
    public static void bind(PreparedStatement p,Object... args)throws SQLException{
        for(int i=0;i<args.length;i++) p.setObject(i+1,args[i]);
    }
    public static List<Map<String,Object>> query(Connection c,String sql,Object... args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql)){
            bind(p,args);
            try(ResultSet rs=p.executeQuery()){
                List<Map<String,Object>> rows=new ArrayList<>();
                ResultSetMetaData md=rs.getMetaData();
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=md.getColumnCount();i++) row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT),rs.getObject(i));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
    public static List<Map<String,Object>> query(String sql,Object... args)throws SQLException{
        try(Connection c=DBContext.connect()){
            return query(c,sql,args);
        }
    }
    public static Map<String,Object> one(String sql,Object... args)throws SQLException {
        var list=query(sql,args);
        return list.isEmpty()?null:list.get(0);
    }
    public static Map<String,Object> one(Connection c,String sql,Object... args)throws SQLException {
        var list=query(c,sql,args);
        return list.isEmpty()?null:list.get(0);
    }
    public static int update(Connection c,String sql,Object...args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql)){
            bind(p,args);
            return p.executeUpdate();
        }
    }
    public static int update(String sql,Object...args)throws SQLException{
        try(Connection c=DBContext.connect()){
            return update(c,sql,args);
        }
    }
    public static int insert(Connection c,String sql,Object...args)throws SQLException{
        try(PreparedStatement p=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS)){
            bind(p,args);
            p.executeUpdate();
            try(ResultSet r=p.getGeneratedKeys()){
                if(!r.next())throw new SQLException("Missing identity");
                return r.getInt(1);
            }
        }
    }
    public static int insert(String sql,Object...args)throws SQLException{
        try(Connection c=DBContext.connect()){
            return insert(c,sql,args);
        }
    }
    public static int intValue(Map<String,Object> m,String key){
        return ((Number)m.get(key)).intValue();
    }
}
