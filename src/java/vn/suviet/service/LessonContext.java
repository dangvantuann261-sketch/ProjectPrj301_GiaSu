package vn.suviet.service;
import java.sql.SQLException;
import java.util.Map;
import vn.suviet.dao.*;
import vn.suviet.model.User;
public final class LessonContext {
    private LessonContext(){}
    public static Map<String,Object> accessible(User user,int id)throws SQLException {
        var dao=new CourseDao();var item=dao.item(id);
        if(item==null)throw new AccessDeniedException();
        int course=Sql.intValue(item,"course_id");var info=dao.get(course);var enrollment=dao.enrollment(course,user.getId());
        if(info==null||(!"PUBLISHED".equals(info.get("status"))&&!("ARCHIVED".equals(info.get("status"))&&enrollment!=null&&"ACTIVE".equals(enrollment.get("status")))))throw new AccessDeniedException();
        return item;
    }
}
