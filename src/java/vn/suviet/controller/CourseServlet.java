package vn.suviet.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.suviet.dao.*;
import vn.suviet.model.Entity;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet(urlPatterns={"/explore","/courses","/course","/preview"})
public final class CourseServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        if(!r.getMethod().equals("GET")){p.sendError(405);return;}
        CourseDao dao=new CourseDao();CourseService service=new CourseService();
        r.setAttribute("publicPage",user(r)==null);
        switch(r.getServletPath()){
            case "/explore", "/courses" -> {
                // Shortcut only for the catalog route; /explore must always render the public landing page.
                if(r.getServletPath().equals("/courses") && dao.count("",0,0,0)==1){Web.redirect(r,p,"/course?id="+dao.catalog("",0,0,0,1).get(0).get("id"));return;}
                String q=Web.text(r,"q",100,false);int subject=Web.number(r,"subject",0),grade=Web.number(r,"grade",0),teacher=0;
                int total=dao.count(q,subject,grade,teacher);
                int pages=Math.max(1,(total+8)/9),page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
                r.setAttribute("rows",dao.catalog(q,subject,grade,teacher,page));r.setAttribute("total",total);r.setAttribute("pages",pages);r.setAttribute("page",page);r.setAttribute("pageStart",Math.max(1,page-3));r.setAttribute("pageEnd",Math.min(pages,page+3));
                r.setAttribute("subjects",new CatalogDao().choices(Entity.SUBJECTS));r.setAttribute("teachers",dao.teacherChoices());
                boolean landing=r.getServletPath().equals("/explore");
                r.setAttribute("landing",landing);
                if(landing){
                    r.setAttribute("stats",dao.landingStats());
                    var featuredRows=dao.catalog("",0,0,0,1);
                    var featured=featuredRows.isEmpty()?null:featuredRows.get(0);
                    r.setAttribute("featured",featured);
                    if(featured!=null)r.setAttribute("chapters",CurriculumService.chapters(dao.items(Sql.intValue(featured,"id"),0)));
                }
                Web.view(r,p,"courses/catalog");
            }
            case "/course" -> {
                int id=Web.positive(r,"id");var course=dao.get(id);
                boolean manager=dao.manages(user(r),id);
                var enrollment=user(r)==null?null:dao.enrollment(id,user(r).getId());
                if(course==null || (!"PUBLISHED".equals(course.get("status"))&&!manager&&!(enrollment!=null&&"ACTIVE".equals(enrollment.get("status"))))){p.sendError(404);return;}
                r.setAttribute("course",course);r.setAttribute("teachers",dao.teachers(id));r.setAttribute("enrollment",enrollment);r.setAttribute("manager",manager);
                var items=dao.items(id,enrollment==null?0:Sql.intValue(enrollment,"id"));
                int student=user(r)!=null&&"STUDENT".equals(user(r).getRole())?user(r).getId():0;
                var curriculum=new CurriculumDao();curriculum.decorate(id,student,items);
                var chapters=CurriculumService.chapters(items);curriculum.decorateChapters(id,student,chapters);
                int done=0,total=0;for(var chapter:chapters){done+=Sql.intValue(chapter,"done");total+=Sql.intValue(chapter,"total");}
                r.setAttribute("items",items);r.setAttribute("chapters",chapters);r.setAttribute("progress",CourseService.progress(done,total));
                r.setAttribute("timeline",StudyTools.timeline(id));
                if(enrollment!=null&&"ACTIVE".equals(enrollment.get("status")))r.setAttribute("resume",service.resume(id,Sql.intValue(enrollment,"id")));
                Web.view(r,p,"courses/detail");
            }
            case "/preview" -> {
                var item=dao.item(Web.positive(r,"id"));
                if(item==null){p.sendError(404);return;}
                var course=dao.get(Sql.intValue(item,"course_id"));
                if(course==null||!"PUBLISHED".equals(course.get("status"))){p.sendError(404);return;}
                if(!CourseService.flag(item.get("is_preview"))&&(user(r)==null||!dao.manages(user(r),Sql.intValue(item,"course_id")))){p.sendError(404);return;}
                if(user(r)!=null&&"STUDENT".equals(user(r).getRole())){Web.redirect(r,p,"/student/lesson?id="+item.get("id"));return;}
                Web.prepareLecture(item);
                r.setAttribute("item",item);r.setAttribute("course",course);r.setAttribute("preview",true);Web.view(r,p,"courses/lesson");
            }
            default -> p.sendError(404);
        }
    }
}
