package vn.suviet.filter;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import java.sql.SQLException;
import vn.suviet.dao.UserDao;
import vn.suviet.model.User;
import vn.suviet.util.Web;
@WebFilter("/*")
public final class AppFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        HttpServletRequest r=(HttpServletRequest)request;
        HttpServletResponse p=(HttpServletResponse)response;
        r.setCharacterEncoding("UTF-8");
        p.setCharacterEncoding("UTF-8");
        p.setHeader("X-Content-Type-Options","nosniff");
        p.setHeader("X-Frame-Options","DENY");
        p.setHeader("Referrer-Policy","strict-origin-when-cross-origin");
        p.setHeader("Content-Security-Policy","default-src 'self'; style-src 'self' https://cdn.jsdelivr.net; script-src 'self' https://cdn.jsdelivr.net; img-src 'self' data:; frame-src https://www.youtube-nocookie.com; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
        String path=r.getServletPath()+Objects.toString(r.getPathInfo(),"");
        if(path.startsWith("/assets/")){
            chain.doFilter(r,p);
            return;
        }
        p.setHeader("Cache-Control","no-store");
        HttpSession s=r.getSession();
        if(s.getAttribute("csrf")==null)s.setAttribute("csrf",UUID.randomUUID().toString());
        try{
            if("POST".equals(r.getMethod())){
                String token=r.getHeader("X-CSRF-Token");
                if(token==null)token=r.getParameter("csrf");
                if(!Objects.equals(s.getAttribute("csrf"),token)){
                    fail(r,p,403,"Phiên biểu mẫu đã hết hạn. Hãy tải lại trang.");
                    return;
                }
            }
            boolean pub=Set.of("/login","/register","/","","/index.jsp","/explore","/courses","/course","/preview").contains(path);
            User u=(User)s.getAttribute("user");
            if(u!=null){
                var fresh=new UserDao().byId(u.getId());
                if(fresh==null||((Number)fresh.get("locked")).intValue()!=0||s.getAttribute("passwordVersion")!=null&&!Objects.equals(s.getAttribute("passwordVersion"),fresh.get("password_hash"))){
                    s.invalidate();
                    if(path.startsWith("/api/"))Web.json(p,401,Map.of("error","Tài khoản đã bị khóa hoặc xóa."));
                    else Web.redirect(r,p,"/login");
                    return;
                }
                u=new User(fresh);
                s.setAttribute("user",u);
            }
            if(!pub && u==null){
                if(path.startsWith("/api/"))Web.json(p,401,Map.of("error","Hãy đăng nhập lại."));
                else Web.redirect(r,p,"/login");
                return;
            }
            if(u!=null){
                String role=u.getRole();
                boolean allowed=true;
                if(path.equals("/admin")||path.startsWith("/admin/"))allowed=role.equals("ADMIN");
                else if(path.equals("/teacher")||path.startsWith("/teacher/"))allowed=role.equals("TEACHER");
                else if(path.equals("/student")||path.startsWith("/student/")||path.startsWith("/api/"))allowed=role.equals("STUDENT");
                if(!allowed){
                    fail(r,p,403,"Bạn không có quyền truy cập chức năng này.");
                    return;
                }
            }
            Object flash=s.getAttribute("flash");
            if(flash!=null){
                r.setAttribute("flash",flash);
                s.removeAttribute("flash");
            }
            chain.doFilter(r,p);
        }
        catch(vn.suviet.service.AccessDeniedException e){
            fail(r,p,403,e.getMessage());
        }
        catch(IllegalArgumentException e){
            fail(r,p,400,e.getMessage());
        }
        catch(SQLException e){
            r.getServletContext().log("Database operation failed; SQLState="+e.getSQLState(),e);
            fail(r,p,503,"Không thể thực hiện thao tác dữ liệu. Kiểm tra cấu hình SQL Server hoặc giá trị trùng lặp.");
        }
        catch(IllegalStateException e){
            r.getServletContext().log("Application configuration/request failure",e);
            fail(r,p,503,"Chưa thể xử lý yêu cầu. Kiểm tra cấu hình hệ thống; ảnh tải lên tối đa 5 MB.");
        }
        catch(ServletException e){
            Throwable cause=e.getRootCause();
            if(cause instanceof SQLException sql){
                r.getServletContext().log("Database operation failed; SQLState="+sql.getSQLState(),sql);
                if(sql.getSQLState()!=null&&sql.getSQLState().startsWith("23"))fail(r,p,409,"Dữ liệu bị trùng hoặc liên kết không còn hợp lệ. Hãy kiểm tra lại.");
                else fail(r,p,503,"Không kết nối được cơ sở dữ liệu. Hãy nhờ quản trị viên kiểm tra SQL Server.");
            }
            else{
                r.getServletContext().log("Servlet failure",e);
                fail(r,p,500,"Có lỗi khi xử lý yêu cầu. Vui lòng thử lại.");
            }
        }
        catch(Exception e){
            r.getServletContext().log("Unhandled request failure",e);
            fail(r,p,500,"Có lỗi khi xử lý yêu cầu. Vui lòng thử lại.");
        }
    }
    private void fail(HttpServletRequest r,HttpServletResponse p,int status,String message)throws IOException,ServletException{
        if(p.isCommitted())return;
        if(r.getServletPath().startsWith("/api/")){
            Web.json(p,status,Map.of("error",message));
            return;
        }
        p.setStatus(status);
        r.setAttribute("error",message);
        Web.view(r,p,"error");
    }
}
