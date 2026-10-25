package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.*;
@WebServlet(urlPatterns={"/teacher/*","/admin/catalog","/admin/results"})
public final class TeacherServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception{
        String path=r.getServletPath().startsWith("/admin/")?r.getServletPath().substring(6):Objects.toString(r.getPathInfo(),"/catalog");
        CatalogDao dao=new CatalogDao();
        boolean post=r.getMethod().equals("POST");
        boolean admin=user(r).getRole().equals("ADMIN");
        r.setAttribute("managementBase",admin?"/admin":"/teacher");
        int uid=user(r).getId();
        if(path.equals("/catalog")){
            Entity entity=Entity.parse(Objects.toString(r.getParameter("entity"),admin?"lessons":"lectures"));
            boolean content=ContentAccessService.isContent(entity);
            if(!admin&&!content)throw new AccessDeniedException("Môn học, bài học và chủ đề thuộc danh mục do quản trị viên phụ trách. Giáo viên quản lý bài giảng và câu hỏi.");
            r.setAttribute("entity",entity);
            r.setAttribute("adminView",admin);
            r.setAttribute("contentEntity",content);
            if(post){
                String action=Web.text(r,"action",20,true);
                int id=Web.number(r,"id",0);
                if(id<0)throw new IllegalArgumentException("ID không hợp lệ.");
                if(action.equals("moderate")){
                    if(!content)throw new IllegalArgumentException("Chỉ ẩn/gỡ được bài giảng hoặc câu hỏi.");
                    boolean hide="1".equals(r.getParameter("hidden"));
                    new ModerationService().moderate(entity,id,uid,hide,Web.text(r,"reason",500,false));
                    Web.flash(r,hide?"Đã ẩn nội dung, ghi lý do và nhật ký thao tác.":"Đã hiện lại nội dung và ghi nhật ký thao tác.");
                }else if(action.equals("copy")){
                    if(!content)throw new IllegalArgumentException("Chỉ sao chép được bài giảng hoặc câu hỏi.");
                    int copy=new CatalogService().copy(entity,id,uid);
                    Web.flash(r,"Đã sao chép thành bản riêng #"+copy+" và giữ thông tin nguồn #"+id+".");
                }else if(action.equals("delete")){
                    if(content&&admin)throw new AccessDeniedException("Quản trị viên không xóa nội dung giảng dạy. Hãy ẩn nội dung kèm lý do để giữ nhật ký.");
                    dao.delete(entity,id,uid);
                    Web.flash(r,"Đã xóa "+entity.label.toLowerCase()+".");
                }else if(action.equals("save")){
                    if(content&&admin)throw new AccessDeniedException("Quản trị viên không trực tiếp soạn, sửa bài giảng, câu hỏi, đáp án hoặc hướng dẫn chấm.");
                    CatalogService service=new CatalogService();
                    int saved=service.save(entity,id,service.validate(r,entity),uid);
                    if(id>0&&content&&saved!=id)Web.flash(r,"Nội dung đã được sử dụng: đã tạo phiên bản mới #"+saved+" và giữ nguyên bản cũ #"+id+" cho học sinh đã học.");
                    else Web.flash(r,"Đã cập nhật "+entity.label.toLowerCase()+".");
                }
                else throw new IllegalArgumentException("Thao tác không hợp lệ.");
                Web.redirect(r,p,(admin?"/admin":"/teacher")+"/catalog?entity="+entity.table);
                return;
            }
            String search=Web.text(r,"q",100,false);
            int pages=Math.max(1,(dao.count(entity,search,uid,admin)+9)/10),page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            r.setAttribute("rows",dao.list(entity,search,page,uid,admin));
            r.setAttribute("page",page);
            r.setAttribute("pages",pages);
            if(content&&!admin)r.setAttribute("shared",dao.shared(entity,uid));
            if(entity.parent!=null)r.setAttribute("parents",dao.choices(Entity.parse(entity.parent),uid,admin));
            int id=Web.number(r,"edit",0);
            if(id>0&&!(admin&&content)){
                var row=dao.get(entity,id);
                if(row==null)throw new IllegalArgumentException("Không tìm thấy mục cần sửa.");
                if(!admin&&Sql.intValue(row,"created_by")!=uid)throw new AccessDeniedException();
                r.setAttribute("edit",row);
            }
            Web.view(r,p,"teacher/catalog");
            return;
        }
        if(path.equals("/students")){
            if(!admin){
                r.setAttribute("rows",Sql.query("SELECT DISTINCT u.id,u.full_name,u.email,u.locked FROM users u JOIN enrollments e ON e.student_id=u.id JOIN course_teachers ct ON ct.course_id=e.course_id WHERE ct.teacher_id=? AND u.deleted=0",uid));
                r.setAttribute("page",1);r.setAttribute("pages",1);Web.view(r,p,"teacher/students");return;
            }
            String q=Web.text(r,"q",100,false);
            int count=Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM users WHERE role='STUDENT' AND deleted=0 AND (full_name LIKE ? OR email LIKE ?)","%"+q+"%","%"+q+"%"),"n");
            int pages=Math.max(1,(count+9)/10),page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            r.setAttribute("rows",Sql.query("SELECT id,full_name,email,locked FROM users WHERE role='STUDENT' AND deleted=0 AND (full_name LIKE ? OR email LIKE ?) ORDER BY id OFFSET ? ROWS FETCH NEXT 10 ROWS ONLY","%"+q+"%","%"+q+"%",(page-1)*10));
            r.setAttribute("page",page);
            r.setAttribute("pages",pages);
            Web.view(r,p,"teacher/students");
            return;
        }
        if(path.equals("/results")){
            if(!admin)throw new AccessDeniedException();
            int student=Web.number(r,"student",0);
            int count=Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM attempts WHERE (?=0 OR user_id=?)",student,student),"n");
            int pages=Math.max(1,(count+9)/10),page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            r.setAttribute("rows",new PracticeDao().history(student,page));
            r.setAttribute("page",page);
            r.setAttribute("pages",pages);
            r.setAttribute("teacherView",true);
            Web.view(r,p,"student/history");
            return;
        }
        if(path.equals("/generate")){
            r.setAttribute("topics",dao.choices(Entity.TOPICS,uid,admin));
            if(post){
                String action=Web.text(r,"action",20,true);
                if(action.equals("generate")){
                    int topicId=Web.positive(r,"topic_id"),count=Web.number(r,"count",3);
                    String kind=Web.text(r,"kind",10,true);
                    if(count<1||count>10||!Set.of("MCQ","ESSAY").contains(kind))throw new IllegalArgumentException("Chọn 1–10 câu hỏi và loại hợp lệ.");
                    var topic=dao.get(Entity.TOPICS,topicId);
                    if(topic==null)throw new IllegalArgumentException("Chủ đề không tồn tại.");
                    ContentAccessService.parent(Entity.TOPICS,topicId,uid);
                    try{
                        List<Question> drafts=new TutorService().generate(topicId,kind,count);
                        r.getSession().setAttribute("drafts",drafts);
                        r.getSession().setAttribute("draftToken",UUID.randomUUID().toString());
                    }
                    catch(AIClient.AIException e){
                        r.setAttribute("error",e.getMessage());
                    }
                }
                else if(action.equals("approve")){
                    synchronized(r.getSession()){
                        Object stored=r.getSession().getAttribute("drafts");
                        if(!(stored instanceof List<?> drafts)||!Objects.equals(r.getSession().getAttribute("draftToken"),r.getParameter("draftToken")))throw new IllegalArgumentException("Bản nháp đã hết hạn hoặc đã được duyệt.");
                        List<Question> approved=new ArrayList<>();
                        for(int i=0;i<drafts.size();i++){
                            if(!"on".equals(r.getParameter("include_"+i)))continue;
                            Question original=(Question)drafts.get(i);
                            Map<String,Object> m=new LinkedHashMap<>();
                            m.put("id",0);
                            m.put("topic_id",original.getTopicId());
                            m.put("kind",original.getKind());
                            for(String field:List.of("prompt","option_a","option_b","option_c","option_d","correct_option","explanation","difficulty"))m.put(field,Web.text(r,field+"_"+i,field.equals("explanation")?8000:field.equals("prompt")?4000:field.startsWith("option_")?1000:20,false));
                            Question question=Question.from(m);
                            question.validate();
                            approved.add(question);
                        }
                        new CatalogService().approve(approved,user(r).getId());
                        r.getSession().removeAttribute("drafts");
                        r.getSession().removeAttribute("draftToken");
                    }
                    Web.flash(r,"Đã duyệt và lưu câu hỏi vào ngân hàng.");
                    Web.redirect(r,p,"/teacher/catalog?entity=questions");
                    return;
                }
                else if(action.equals("discard")){
                    r.getSession().removeAttribute("drafts");
                    r.getSession().removeAttribute("draftToken");
                }
                else throw new IllegalArgumentException("Thao tác không hợp lệ.");
            }
            Web.view(r,p,"teacher/generate");
            return;
        }
        p.sendError(404);
    }
}
