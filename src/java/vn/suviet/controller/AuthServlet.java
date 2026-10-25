package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.User;
import vn.suviet.util.*;
@WebServlet(urlPatterns={
    "/login","/register","/logout","/home"
}
)
public final class AuthServlet extends BaseServlet {
    public static String home(User u){
        return switch(u.getRole()){
            case "ADMIN"->"/admin/dashboard";
            case "TEACHER"->"/teacher/courses";
            default->"/student/dashboard";
        }
        ;
    }
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception{
        String path=r.getServletPath();
        boolean post="POST".equals(r.getMethod());
        if(path.equals("/logout")){
            if(!post){
                p.sendError(405);
                return;
            }
            r.getSession().invalidate();
            Web.redirect(r,p,"/login");
            return;
        }
        if(user(r)!=null){
            Web.redirect(r,p,home(user(r)));
            return;
        }
        if(path.equals("/home")){
            Web.redirect(r,p,"/login");
            return;
        }
        boolean register=path.equals("/register");
        r.setAttribute("register",register);
        if(post){
            String email=Web.text(r,"email",254,true).toLowerCase(Locale.ROOT);
            String password=Objects.toString(r.getParameter("password"),"");
            if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Email không hợp lệ.");
            UserDao dao=new UserDao();
            if(register){
                Passwords.validate(password);
                String name=Web.text(r,"full_name",100,true);
                if(dao.byEmail(email)!=null){
                    r.setAttribute("error","Email đã được sử dụng.");
                    Web.view(r,p,"auth");
                    return;
                }
                try{
                    dao.create(name,email,password,"STUDENT");
                }
                catch(java.sql.SQLException e){
                    if(e.getSQLState()!=null&&e.getSQLState().startsWith("23")){
                        r.setAttribute("error","Email đã được sử dụng.");
                        Web.view(r,p,"auth");
                        return;
                    }
                    throw e;
                }
                Web.flash(r,"Đăng ký thành công. Em hãy đăng nhập để bắt đầu.");
                Web.redirect(r,p,"/login");
                return;
            }
            Long until=(Long)r.getSession().getAttribute("loginWait");
            if(until!=null&&System.currentTimeMillis()<until){
                r.setAttribute("error","Hãy chờ một phút trước khi thử lại.");
                Web.view(r,p,"auth");
                return;
            }
            var row=dao.byEmail(email);
            if(row==null || Sql.intValue(row,"locked")!=0 || password.length()>128 || !Passwords.verify(password,(String)row.get("password_hash"))){
                Integer n=(Integer)r.getSession().getAttribute("loginFailures");
                n=n==null?1:n+1;
                r.getSession().setAttribute("loginFailures",n);
                if(n>=5){
                    r.getSession().setAttribute("loginWait",System.currentTimeMillis()+60000);
                    r.getSession().setAttribute("loginFailures",0);
                }
                r.setAttribute("error","Email hoặc mật khẩu không đúng, hoặc tài khoản đã bị khóa.");
                Web.view(r,p,"auth");
                return;
            }
            r.getSession().invalidate();
            HttpSession s=r.getSession(true);
            s.setMaxInactiveInterval(1800);
            User u=new User(row);
            s.setAttribute("user",u);
            s.setAttribute("passwordVersion",row.get("password_hash"));
            s.setAttribute("csrf",UUID.randomUUID().toString());
            Web.redirect(r,p,home(u));
            return;
        }
        Web.view(r,p,"auth");
    }
}
