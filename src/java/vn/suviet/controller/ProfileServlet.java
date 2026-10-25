package vn.suviet.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.service.ProfileService;
import vn.suviet.util.Web;

@WebServlet("/student/profile")
public final class ProfileServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        ProfileService service=new ProfileService();
        if(r.getMethod().equals("POST")){
            if("password".equals(r.getParameter("action"))){
                String next=Objects.toString(r.getParameter("new_password"),"");
                if(!next.equals(r.getParameter("confirm_password")))throw new IllegalArgumentException("Mật khẩu xác nhận không khớp.");
                service.password(user(r),r.getParameter("current_password"),next);r.getSession().invalidate();Web.redirect(r,p,"/login");return;
            }
            Set<Integer> subjects=new HashSet<>();String[] values=r.getParameterValues("subjects");
            if(values!=null){if(values.length>50)throw new IllegalArgumentException("Chọn tối đa 50 môn.");for(String value:values)subjects.add(Integer.parseInt(value));}
            service.save(user(r),Web.text(r,"full_name",100,true),Web.number(r,"grade",6),subjects);
            Web.flash(r,"Đã lưu hồ sơ học tập.");Web.redirect(r,p,"/student/profile");return;
        }
        r.setAttribute("profile",service.get(user(r).getId()));r.setAttribute("subjects",service.subjects(user(r).getId()));Web.view(r,p,"student/profile");
    }
}