package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet(urlPatterns={"/student/dashboard","/student/courses","/student/progress","/student/mistakes","/student/notifications"})
public final class InsightsServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        int uid=user(r).getId();boolean post=r.getMethod().equals("POST");String path=r.getServletPath();
        if(!post&&!r.getMethod().equals("GET")){p.sendError(405);return;}
        var insights=new LearningInsightsDao();
        if(path.equals("/student/notifications")){
            if(post)insights.readNotification(uid,Web.positive(r,"id"));
            Web.json(p,200,Map.of("notifications",insights.notifications(uid)));return;
        }
        var dao=new CourseDao();var courses=dao.catalog("",0,0,0,1);
        int course=Web.number(r,"course",0);
        if(course==0){
            var seeded=Sql.one("SELECT TRY_CONVERT(INT,JSON_VALUE(setting_value,'$.course_id')) AS id FROM app_settings WHERE setting_key='seed.history6.kntt.v1'");
            if(seeded!=null&&seeded.get("id")!=null)course=Sql.intValue(seeded,"id");
            else if(!courses.isEmpty())course=Sql.intValue(courses.get(0),"id");
        }
        var info=course==0?null:dao.get(course);
        var enrollment=course==0?null:dao.enrollment(course,uid);
        if(info!=null&&!"PUBLISHED".equals(info.get("status"))&&!("ARCHIVED".equals(info.get("status"))&&enrollment!=null&&"ACTIVE".equals(enrollment.get("status")))){p.sendError(404);return;}
        if(post){
            if(!path.equals("/student/mistakes")||info==null){p.sendError(405);return;}
            Web.redirect(r,p,"/student/practice?id="+insights.retry(user(r),course));return;
        }
        var items=info==null?new ArrayList<Map<String,Object>>():dao.items(course,enrollment==null?0:Sql.intValue(enrollment,"id"));
        new CurriculumDao().decorate(course,uid,items);
        var chapters=CurriculumService.chapters(items);int done=0,total=0;
        for(var chapter:chapters){done+=Sql.intValue(chapter,"done");total+=Sql.intValue(chapter,"total");}
        var scores=insights.chapterScores(uid,course);
        for(var chapter:chapters)for(var score:scores)if(chapter.get("key").equals(score.get("section_title")))chapter.put("average",score.get("score"));
        int resume=info==null?0:new CourseService().resume(course,enrollment==null?0:Sql.intValue(enrollment,"id"));
        for(var item:items)if(Sql.intValue(item,"id")==resume)r.setAttribute("resumeItem",item);
        r.setAttribute("course",info);r.setAttribute("chapters",chapters);r.setAttribute("items",items);
        r.setAttribute("progress",CourseService.progress(done,total));r.setAttribute("done",done);r.setAttribute("total",total);
        r.setAttribute("mistakes",insights.mistakes(uid,course));r.setAttribute("weakTopics",insights.weakTopics(uid,course));
        var trend=insights.trend(uid,course);Collections.reverse(trend);r.setAttribute("trend",trend);
        r.setAttribute("notifications",insights.notifications(uid));
        r.setAttribute("unfinished",Sql.query("SELECT TOP 3 a.id,COALESCE(a.chapter_title,l.title,t.title) AS title FROM attempts a JOIN topics t ON t.id=a.topic_id LEFT JOIN course_items i ON i.id=a.course_item_id LEFT JOIN lectures l ON l.id=i.lecture_id WHERE a.user_id=? AND a.current_pos<a.total ORDER BY a.id DESC",uid));
        Web.view(r,p,path.endsWith("dashboard")?"student/dashboard":path.endsWith("mistakes")?"student/mistakes":"student/progress");
    }
}
