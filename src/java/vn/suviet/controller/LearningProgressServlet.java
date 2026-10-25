package vn.suviet.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet(urlPatterns={"/teacher/progress","/teacher/students","/admin/progress"})
public final class LearningProgressServlet extends BaseServlet {
    private static final DateTimeFormatter DISPLAY_TIME=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static void formatDates(List<Map<String,Object>> rows,String... fields){
        for(var row:rows)for(String field:fields){
            Timestamp value=(Timestamp)row.get(field);
            row.put(field+"_text",value==null?"":DISPLAY_TIME.format(value.toLocalDateTime()));
        }
    }
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        if(!"GET".equals(r.getMethod())){p.sendError(405);return;}
        LearningProgressDao dao=new LearningProgressDao();
        r.setAttribute("managementBase",user(r).getRole().equals("ADMIN")?"/admin":"/teacher");
        int enrollment=Web.number(r,"enrollment",0);
        if(enrollment<0)throw new IllegalArgumentException("Ghi danh không hợp lệ.");
        if(enrollment>0){
            var learner=dao.enrollment(user(r),enrollment);
            if(learner==null)throw new AccessDeniedException();
            formatDates(List.of(learner),"enrolled_local","completed_local","last_activity_local");
            var lessons=dao.lessons(user(r),enrollment);formatDates(lessons,"viewed_local","completed_local");
            int courseId=Sql.intValue(learner,"course_id"),studentId=Sql.intValue(learner,"student_id");
            var curriculum=new CurriculumDao();curriculum.decorate(courseId,studentId,lessons);
            var chapters=CurriculumService.chapters(lessons);curriculum.decorateChapters(courseId,studentId,chapters);
            r.setAttribute("chapters",chapters);
            r.setAttribute("learner",learner);r.setAttribute("lessons",lessons);
            int pages=Math.max(1,(dao.activityCount(user(r),enrollment)+LearningProgressDao.PAGE_SIZE-1)/LearningProgressDao.PAGE_SIZE);
            int page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            var activity=dao.activity(user(r),enrollment,page);formatDates(activity,"occurred_local");
            r.setAttribute("activity",activity);r.setAttribute("page",page);r.setAttribute("pages",pages);
            Web.view(r,p,"teacher/progress-detail");return;
        }
        int course=Web.number(r,"course",0);
        if(course>0)new CourseService().requireManager(user(r),course);
        String status=Web.text(r,"status",15,false),learning=Web.text(r,"learning",15,false),sort=Web.text(r,"sort",15,false);
        var filter=new LearningProgressDao.Filter(course,Web.text(r,"q",100,false),status.isBlank()?"ACTIVE":status,learning.isBlank()?"ALL":learning,sort.isBlank()?"name":sort);
        var summary=dao.summary(user(r),filter);int pages=Math.max(1,(Sql.intValue(summary,"enrollment_count")+LearningProgressDao.PAGE_SIZE-1)/LearningProgressDao.PAGE_SIZE);
        int page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
        var rows=dao.list(user(r),filter,page);formatDates(rows,"last_activity_local");
        r.setAttribute("summary",summary);r.setAttribute("rows",rows);r.setAttribute("courses",new CourseDao().managed(user(r)));
        r.setAttribute("selectedCourse",course);r.setAttribute("selectedStatus",filter.enrollment());r.setAttribute("selectedLearning",filter.progress());r.setAttribute("selectedSort",filter.sort());
        r.setAttribute("page",page);r.setAttribute("pages",pages);
        Web.view(r,p,"teacher/progress");
    }
}
