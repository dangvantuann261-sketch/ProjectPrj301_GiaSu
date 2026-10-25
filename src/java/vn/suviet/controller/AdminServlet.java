package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.util.*;
import vn.suviet.service.AiSettingsService;
@WebServlet("/admin/*")
public final class AdminServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception{
        String path=Objects.toString(r.getPathInfo(),"/dashboard");
        boolean post=r.getMethod().equals("POST");
        UserDao dao=new UserDao();
        switch(path){
            case "/dashboard":
            r.setAttribute("stats",Sql.one("SELECT (SELECT COUNT(*) FROM users WHERE role='TEACHER' AND deleted=0) AS teachers,(SELECT COUNT(*) FROM users WHERE role='STUDENT' AND deleted=0) AS students,(SELECT COUNT(*) FROM lectures WHERE deleted=0) AS lectures,(SELECT COUNT(*) FROM questions WHERE deleted=0) AS questions,(SELECT COUNT(*) FROM attempts) AS attempts"));
            Web.view(r,p,"admin/dashboard");
            break;
            case "/users":
            if(post){
                String action=Web.text(r,"action",20,true);
                int id=Web.number(r,"id",0);
                if(id<0)throw new IllegalArgumentException("ID không hợp lệ.");
                if(id>0){
                    var target=dao.byId(id);
                    if(target==null||"ADMIN".equals(target.get("role")))throw new IllegalArgumentException("Chỉ quản lý tài khoản giáo viên và học sinh.");
                }
                switch(action){
                    case "delete":dao.delete(id);
                    break;
                    case "lock":dao.lock(id,"1".equals(r.getParameter("locked")));
                    break;
                    case "save":
                    String name=Web.text(r,"full_name",100,true), email=Web.text(r,"email",254,true).toLowerCase(Locale.ROOT), role=Web.text(r,"role",10,true), password=Objects.toString(r.getParameter("password"),"");
                    if(!Set.of("TEACHER","STUDENT").contains(role)||!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))throw new IllegalArgumentException("Vai trò hoặc email không hợp lệ.");
                    if(id==0||!password.isEmpty())Passwords.validate(password);
                    if(id==0)dao.create(name,email,password,role);
                    else dao.edit(id,name,email,role,password);
                    break;
                    default:throw new IllegalArgumentException("Thao tác không hợp lệ.");
                }
                Web.flash(r,"Đã cập nhật tài khoản.");
                Web.redirect(r,p,"/admin/users");
                return;
            }
            String search=Web.text(r,"q",100,false);
            int pages=Math.max(1,(dao.count(search)+9)/10), page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            r.setAttribute("rows",dao.list(search,page));
            r.setAttribute("page",page);
            r.setAttribute("pages",pages);
            int edit=Web.number(r,"edit",0);
            if(edit>0){
                var row=dao.byId(edit);
                if(row==null||"ADMIN".equals(row.get("role")))throw new IllegalArgumentException("Tài khoản không hợp lệ.");
                r.setAttribute("edit",row);
            }
            Web.view(r,p,"admin/users");
            break;
            case "/settings":
            SettingsDao settings=new SettingsDao();
            AiSettingsService aiSettings=new AiSettingsService();
            if(post){
                Map<String,String> fields=new LinkedHashMap<>();
                for(String prefix:List.of("ai_","essay_ai_")){
                    fields.put(prefix+"url",Web.text(r,prefix+"url",2000,true));
                    fields.put(prefix+"model",Web.text(r,prefix+"model",100,true));
                    fields.put(prefix+"timeout",String.valueOf(Web.number(r,prefix+"timeout",45)));
                    fields.put(prefix+"key",Web.text(r,prefix+"key",500,false));
                    fields.put(prefix+"clear",Objects.toString(r.getParameter(prefix+"clear"),""));
                }
                aiSettings.save(user(r).getId(),Web.text(r,"prompt",12000,true),Web.text(r,"essay_prompt",12000,true),fields);
                Web.flash(r,"Đã lưu cấu hình AI. Cấu hình áp dụng ngay cho yêu cầu tiếp theo.");
                Web.redirect(r,p,"/admin/settings");
                return;
            }
            r.setAttribute("prompt",settings.prompt());
            r.setAttribute("essayPrompt",settings.get("essay_prompt",AiSettingsService.ESSAY_RULES));
            r.setAttribute("generalAi",aiSettings.view(false));r.setAttribute("essayAi",aiSettings.view(true));
            Web.view(r,p,"admin/settings");
            break;
            case "/reports":
            r.setAttribute("report",Sql.one("SELECT COUNT(*) AS submissions,COUNT(score) AS graded,AVG(score) AS average_score,SUM(CASE WHEN grading_status IN('PENDING','RETRY','PROCESSING') THEN 1 ELSE 0 END) AS pending FROM answers"));
            r.setAttribute("subjects",Sql.query("SELECT s.name,s.grade,COUNT(DISTINCT a.id) AS attempts,COUNT(ans.score) AS graded,AVG(ans.score) AS average_score FROM subjects s LEFT JOIN lessons l ON l.subject_id=s.id LEFT JOIN topics t ON t.lesson_id=l.id LEFT JOIN attempts a ON a.topic_id=t.id LEFT JOIN answers ans ON ans.attempt_id=a.id WHERE s.deleted=0 GROUP BY s.id,s.name,s.grade ORDER BY s.grade,s.name"));
            r.setAttribute("activity",Sql.query("SELECT TOP 50 a.action,a.resource_id,a.created_at,u.full_name FROM audit_logs a JOIN users u ON u.id=a.actor_id ORDER BY a.id DESC"));
            Web.view(r,p,"admin/reports");
            break;
            default:p.sendError(404);
        }
    }
}
