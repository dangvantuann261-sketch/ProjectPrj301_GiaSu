package vn.suviet.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.suviet.dao.*;
import vn.suviet.model.Entity;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet(urlPatterns={"/teacher/courses","/admin/courses"})
public final class TeacherCourseServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        CourseDao dao=new CourseDao();CourseService service=new CourseService();int id=Web.number(r,"id",0);
        String managementBase=user(r).getRole().equals("ADMIN")?"/admin":"/teacher";
        r.setAttribute("managementBase",managementBase);
        if(id<0)throw new IllegalArgumentException("Khóa học không hợp lệ.");
        if(r.getMethod().equals("POST")){
            String action=Web.text(r,"action",20,true);
            switch(action){
                case "save" -> id=service.save(user(r),id,Web.positive(r,"subject_id"),Web.text(r,"title",255,true),Web.text(r,"summary",2000,true),Web.text(r,"objectives",4000,true),Web.text(r,"audience",2000,true),Web.text(r,"prerequisites",2000,false),Web.number(r,"duration_minutes",60),"DIRECT");
                case "item", "remove" -> service.item(user(r),id,action,Web.number(r,"item_id",0),Web.number(r,"lecture_id",0),Web.text(r,"section_title",255,false),Web.number(r,"position",1),"on".equals(r.getParameter("is_preview")),"on".equals(r.getParameter("required")));
                case "status" -> service.status(user(r),id,Web.text(r,"status",10,true));
                case "teacher" -> service.teacher(user(r),id,Web.positive(r,"teacher_id"),"1".equals(r.getParameter("remove")));
                case "questions" -> {
                    String[] raw=r.getParameterValues("question_id");
                    java.util.List<Integer> picked=new java.util.ArrayList<>();
                    if(raw!=null)for(String value:raw)try{picked.add(Integer.valueOf(value));}catch(NumberFormatException ex){throw new IllegalArgumentException("Câu hỏi không hợp lệ.");}
                    service.questions(user(r),id,Web.number(r,"item_id",0),picked.stream().mapToInt(Integer::intValue).toArray());
                }
                default -> throw new IllegalArgumentException("Thao tác không hợp lệ.");
            }
            Web.flash(r,"Đã cập nhật khóa học.");Web.redirect(r,p,managementBase+"/courses?id="+id);return;
        }
        r.setAttribute("rows",dao.managed(user(r)));r.setAttribute("subjects",new CatalogDao().choices(Entity.SUBJECTS));
        if(id>0){
            service.requireManager(user(r),id);var course=dao.get(id);r.setAttribute("course",course);
            r.setAttribute("items",dao.items(id,0));r.setAttribute("teachers",dao.teachers(id));r.setAttribute("teacherChoices",dao.teacherChoices());r.setAttribute("students",dao.students(id));
            r.setAttribute("lectures",dao.availableLectures(user(r),Sql.intValue(course,"subject_id")));
            r.setAttribute("editable","DRAFT".equals(course.get("status"))&&Sql.one("SELECT TOP 1 id FROM enrollments WHERE course_id=?",id)==null);
            r.setAttribute("curation",Boolean.TRUE.equals(r.getAttribute("editable"))&&!user(r).getRole().equals("ADMIN"));
            if(Boolean.TRUE.equals(r.getAttribute("editable"))&&!r.getAttribute("managementBase").equals("/admin")){
                java.util.Map<Integer,java.util.List<java.util.Map<String,Object>>> scope=new java.util.LinkedHashMap<>();
                for(var item:dao.items(id,0))scope.put(Sql.intValue(item,"id"),dao.questionsForItem(Sql.intValue(item,"id"),user(r).getId()));
                r.setAttribute("itemQuestions",scope);
            }
        }else r.setAttribute("editable",true);
        Web.view(r,p,"courses/manage");
    }
}
