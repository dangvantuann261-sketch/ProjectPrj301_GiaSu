package vn.suviet.dao;

import java.sql.SQLException;
import java.util.*;

public final class CurriculumDao {
    /** Only scoped attempts count; legacy library work is retained in personal history. */
    public void decorate(int course,int student,List<Map<String,Object>> items)throws SQLException {
        var stats=Sql.query("""
            SELECT a.course_item_id,COUNT(*) AS practice_attempts,
              SUM(CASE WHEN a.current_pos=a.total THEN 1 ELSE 0 END) AS practice_finished,
              MAX(CASE WHEN a.current_pos=a.total AND scores.pending=0 THEN scores.score END) AS best_score
            FROM attempts a OUTER APPLY(SELECT AVG(score) AS score,
              SUM(CASE WHEN score IS NULL THEN 1 ELSE 0 END) AS pending FROM answers WHERE attempt_id=a.id) scores
            WHERE a.course_id=? AND a.user_id=? AND a.course_item_id IS NOT NULL
            GROUP BY a.course_item_id
            """,course,student);
        Map<Integer,Map<String,Object>> byItem=new HashMap<>();
        for(var row:stats)byItem.put(Sql.intValue(row,"course_item_id"),row);
        for(var item:items){
            var row=byItem.get(Sql.intValue(item,"id"));
            item.put("practice_attempts",row==null?0:row.get("practice_attempts"));
            item.put("practice_finished",row==null?0:row.get("practice_finished"));
            item.put("best_score",row==null?null:row.get("best_score"));
        }
    }
    public void decorateChapters(int course,int student,List<Map<String,Object>> chapters)throws SQLException {
        var stats=Sql.query("""
            SELECT a.chapter_title,COUNT(*) AS practice_attempts,
              SUM(CASE WHEN a.current_pos=a.total THEN 1 ELSE 0 END) AS practice_finished,
              MAX(CASE WHEN a.current_pos=a.total AND scores.pending=0 THEN scores.score END) AS best_score
            FROM attempts a OUTER APPLY(SELECT AVG(score) AS score,
              SUM(CASE WHEN score IS NULL THEN 1 ELSE 0 END) AS pending FROM answers WHERE attempt_id=a.id) scores
            WHERE a.course_id=? AND a.user_id=? AND a.chapter_title IS NOT NULL GROUP BY a.chapter_title
            """,course,student);
        for(var chapter:chapters)for(var row:stats)if(Objects.equals(chapter.get("key"),row.get("chapter_title"))){
            chapter.put("practice_attempts",row.get("practice_attempts"));chapter.put("practice_finished",row.get("practice_finished"));chapter.put("best_score",row.get("best_score"));
        }
    }
}
