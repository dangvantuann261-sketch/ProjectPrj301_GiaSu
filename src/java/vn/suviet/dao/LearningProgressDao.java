package vn.suviet.dao;

import java.sql.SQLException;
import java.util.*;
import vn.suviet.model.User;
import vn.suviet.service.AccessDeniedException;

/** Every report query scopes data to current teaching assignments (or an Admin). */
public final class LearningProgressDao {
    public static final int PAGE_SIZE=20;
    public record Filter(int course,String search,String enrollment,String progress,String sort) {
        public Filter {
            if(course<0||search==null||search.length()>100||!Set.of("ALL","ACTIVE","PENDING","REJECTED","REVOKED").contains(enrollment)
                ||!Set.of("ALL","NOT_STARTED","IN_PROGRESS","COMPLETED").contains(progress)||!Set.of("name","recent","progress").contains(sort))
                throw new IllegalArgumentException("Bộ lọc tiến độ không hợp lệ.");
        }
    }
    private static final String SCOPE="(?=1 OR EXISTS(SELECT 1 FROM course_teachers ct WHERE ct.course_id=e.course_id AND ct.teacher_id=?))";
    private static final String BASE="""
        WITH Learners AS (
          SELECT e.id AS enrollment_id,e.student_id,e.course_id,e.status AS enrollment_status,
            u.full_name,u.email,c.title AS course_title,c.status AS course_status,
            CONVERT(datetime2,e.created_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS enrolled_local,
            CONVERT(datetime2,e.completed_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS completed_local,
            totals.required_count,totals.required_done,totals.viewed_count,totals.item_count,
            CASE WHEN events.last_event>totals.last_viewed OR totals.last_viewed IS NULL THEN events.last_event ELSE totals.last_viewed END AS last_activity
          FROM enrollments e JOIN users u ON u.id=e.student_id JOIN courses c ON c.id=e.course_id
          JOIN subjects s ON s.id=c.subject_id
          OUTER APPLY (SELECT COUNT(*) AS item_count,
            COALESCE(SUM(CASE WHEN i.required=1 THEN 1 ELSE 0 END),0) AS required_count,
            COALESCE(SUM(CASE WHEN i.required=1 AND r.completed=1 THEN 1 ELSE 0 END),0) AS required_done,
            COUNT(r.course_item_id) AS viewed_count,MAX(r.last_viewed_at) AS last_viewed
            FROM course_items i LEFT JOIN learning_records r ON r.course_item_id=i.id AND r.enrollment_id=e.id
            WHERE i.course_id=e.course_id) totals
          OUTER APPLY (SELECT MAX(activity_at) AS last_event FROM (
            SELECT a.occurred_at AS activity_at FROM learning_activity a WHERE a.enrollment_id=e.id
            UNION ALL SELECT COALESCE(a.finished_at,a.created_at) FROM attempts a WHERE a.course_id=e.course_id AND a.user_id=e.student_id
          ) history) events
          WHERE u.deleted=0 AND u.role='STUDENT' AND s.deleted=0 AND
        """+SCOPE+"""
        ), Progress AS (
          SELECT *,CASE WHEN required_count=0 THEN 0 ELSE required_done*100/required_count END AS percent_complete,
            CASE WHEN required_count>0 AND required_done=required_count THEN 'COMPLETED'
                 WHEN viewed_count>0 OR last_activity IS NOT NULL THEN 'IN_PROGRESS' ELSE 'NOT_STARTED' END AS learning_status,
            CONVERT(datetime2,last_activity AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS last_activity_local
          FROM Learners
        )
        """;
    private static final String FILTER="""
        , Filtered AS (SELECT * FROM Progress WHERE (?=0 OR course_id=?) AND (full_name LIKE ? OR email LIKE ?)
          AND (?='ALL' OR enrollment_status=?) AND (?='ALL' OR learning_status=?))
        """;
    private static int admin(User user){
        if(user==null||!Set.of("ADMIN","TEACHER").contains(user.getRole()))throw new AccessDeniedException();
        return "ADMIN".equals(user.getRole())?1:0;
    }
    private static String pattern(String value){return "%"+value.replace("[","[[]").replace("%","[%]").replace("_","[_]")+"%";}
    private Object[] args(User user,Filter f){return new Object[]{admin(user),user.getId(),f.course(),f.course(),pattern(f.search()),pattern(f.search()),f.enrollment(),f.enrollment(),f.progress(),f.progress()};}
    public Map<String,Object> summary(User user,Filter f)throws SQLException {
        return Sql.one(BASE+FILTER+"SELECT COUNT(*) AS enrollment_count,COALESCE(SUM(CASE WHEN learning_status='NOT_STARTED' THEN 1 ELSE 0 END),0) AS not_started,COALESCE(SUM(CASE WHEN learning_status='IN_PROGRESS' THEN 1 ELSE 0 END),0) AS in_progress,COALESCE(SUM(CASE WHEN learning_status='COMPLETED' THEN 1 ELSE 0 END),0) AS completed FROM Filtered",args(user,f));
    }
    public List<Map<String,Object>> list(User user,Filter f,int page)throws SQLException {
        String sort=switch(f.sort()){case "recent"->"last_activity DESC,full_name";case "progress"->"percent_complete DESC,full_name";default->"full_name,course_title";};
        var params=new ArrayList<>(Arrays.asList(args(user,f)));params.add((page-1)*PAGE_SIZE);params.add(PAGE_SIZE);
        return Sql.query(BASE+FILTER+"SELECT * FROM Filtered ORDER BY "+sort+",enrollment_id OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",params.toArray());
    }
    public Map<String,Object> enrollment(User user,int enrollment)throws SQLException {
        return Sql.one(BASE+"SELECT * FROM Progress WHERE enrollment_id=?",admin(user),user.getId(),enrollment);
    }
    public List<Map<String,Object>> lessons(User user,int enrollment)throws SQLException {
        return Sql.query("""
            SELECT i.id,i.position,i.section_title,i.required,l.title,COALESCE(r.completed,0) AS completed,
              CASE WHEN r.course_item_id IS NULL THEN 0 ELSE 1 END AS viewed,
              CONVERT(datetime2,r.last_viewed_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS viewed_local,
              CONVERT(datetime2,a.occurred_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS completed_local
            FROM enrollments e JOIN course_items i ON i.course_id=e.course_id JOIN lectures l ON l.id=i.lecture_id
            LEFT JOIN learning_records r ON r.enrollment_id=e.id AND r.course_item_id=i.id
            LEFT JOIN learning_activity a ON a.enrollment_id=e.id AND a.course_item_id=i.id AND a.action='COMPLETE'
            WHERE e.id=? AND
            """+SCOPE+" ORDER BY i.position,i.id",enrollment,admin(user),user.getId());
    }
    public int activityCount(User user,int enrollment)throws SQLException {
        return Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM learning_activity a JOIN enrollments e ON e.id=a.enrollment_id WHERE e.id=? AND "+SCOPE,enrollment,admin(user),user.getId()),"n");
    }
    public List<Map<String,Object>> activity(User user,int enrollment,int page)throws SQLException {
        return Sql.query("SELECT a.id,a.action,l.title,i.section_title,CONVERT(datetime2,a.occurred_at AT TIME ZONE 'UTC' AT TIME ZONE 'SE Asia Standard Time') AS occurred_local FROM learning_activity a JOIN enrollments e ON e.id=a.enrollment_id JOIN course_items i ON i.id=a.course_item_id JOIN lectures l ON l.id=i.lecture_id WHERE e.id=? AND "+SCOPE+" ORDER BY a.id DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",enrollment,admin(user),user.getId(),(page-1)*PAGE_SIZE,PAGE_SIZE);
    }
}
