package vn.suviet.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.suviet.dao.*;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet(urlPatterns={"/student/enroll","/student/lesson","/student/resume"})
public final class LearningServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        CourseDao dao=new CourseDao();CourseService service=new CourseService();boolean post=r.getMethod().equals("POST");
        if(!post&&!r.getMethod().equals("GET")){p.sendError(405);return;}
        switch(r.getServletPath()){
            case "/student/enroll" -> {
                p.sendError(410);return;
            }
            case "/student/resume" -> {
                if(post){p.sendError(405);return;}
                int id=Web.positive(r,"id");var e=service.active(user(r),id);int item=service.resume(id,Sql.intValue(e,"id"));
                if(item==0){Web.redirect(r,p,"/course?id="+id);return;}Web.redirect(r,p,"/student/lesson?id="+item);
            }
            case "/student/lesson" -> {
                int id=Web.positive(r,"id");var item=dao.item(id);if(item==null){p.sendError(404);return;}
                int course=Sql.intValue(item,"course_id");var e=service.active(user(r),course);int eid=Sql.intValue(e,"id");
                if(post){service.learn(user(r),id,Web.text(r,"action",20,true),Web.text(r,"note",8000,false));Web.flash(r,"Đã lưu thay đổi.");Web.redirect(r,p,"/student/lesson?id="+id);return;}
                service.learn(user(r),id,"view",null);
                var items=dao.items(course,eid);int done=0,total=0;
                var curriculum=new CurriculumDao();curriculum.decorate(course,user(r).getId(),items);
                var chapters=CurriculumService.chapters(items);curriculum.decorateChapters(course,user(r).getId(),chapters);
                r.setAttribute("chapters",chapters);
                for(var chapter:chapters)if(chapter.get("key").equals(item.get("section_title")))r.setAttribute("chapter",chapter);
                for(var row:items)if(Sql.intValue(row,"id")==id)r.setAttribute("practiceStats",row);
                for(int n=0;n<items.size();n++){var row=items.get(n);if(Boolean.TRUE.equals(row.get("required"))){total++;if(CourseService.flag(row.get("completed")))done++;}if(Sql.intValue(row,"id")==id){if(n>0)r.setAttribute("previous",items.get(n-1));if(n+1<items.size())r.setAttribute("next",items.get(n+1));}}
                Web.prepareLecture(item);
                r.setAttribute("flashcard",StudyTools.card(id));
                r.setAttribute("progress",CourseService.progress(done,total));r.setAttribute("item",item);r.setAttribute("course",dao.get(course));r.setAttribute("items",items);r.setAttribute("record",dao.record(eid,id));
                r.setAttribute("practiceCount",Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM course_item_questions cq JOIN questions q ON q.id=cq.question_id WHERE cq.course_item_id=? AND q.kind='MCQ' AND q.deleted=0 AND q.hidden=0",id),"n"));
                Web.view(r,p,"courses/lesson");
            }
            default -> p.sendError(404);
        }
    }
}
