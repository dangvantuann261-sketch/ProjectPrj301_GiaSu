package vn.suviet.model;
import java.io.Serializable;
import java.util.Map;
public final class User implements Serializable {
    private final int id;
    private final String name, email, role;
    public User(Map<String,Object> r) {
        id=((Number)r.get("id")).intValue();
        name=(String)r.get("full_name");
        email=(String)r.get("email");
        role=(String)r.get("role");
    }
    public int getId(){
        return id;
    }
    public String getName(){
        return name;
    }
    public String getEmail(){
        return email;
    }
    public String getRole(){
        return role;
    }
}
