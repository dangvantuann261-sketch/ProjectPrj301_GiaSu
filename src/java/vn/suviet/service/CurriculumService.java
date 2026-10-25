package vn.suviet.service;

import java.util.*;
import vn.suviet.dao.Sql;

/** Groups lessons in curriculum order; chapter identity is the exact section title. */
public final class CurriculumService {
    private CurriculumService() {}
    public static List<Map<String,Object>> chapters(List<Map<String,Object>> items) {
        Map<String,Map<String,Object>> grouped=new LinkedHashMap<>();
        for(var item:items){
            String title=Objects.toString(item.get("section_title"),"");
            var chapter=grouped.computeIfAbsent(title,key->{
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("title",key.isBlank()?"Bài học":key);row.put("key",key);
                row.put("anchor",Sql.intValue(item,"id"));row.put("items",new ArrayList<Map<String,Object>>());
                return row;
            });
            @SuppressWarnings("unchecked") var lessons=(List<Map<String,Object>>)chapter.get("items");
            lessons.add(item);
        }
        for(var chapter:grouped.values()){
            @SuppressWarnings("unchecked") var lessons=(List<Map<String,Object>>)chapter.get("items");
            int required=0,done=0,practiced=0,questions=0;
            for(var lesson:lessons){
                if(CourseService.flag(lesson.get("required"))){required++;if(CourseService.flag(lesson.get("completed")))done++;}
                if(lesson.get("practice_finished") instanceof Number n&&n.intValue()>0)practiced++;
                if(lesson.get("question_count") instanceof Number n)questions+=n.intValue();
            }
            chapter.put("total",required);chapter.put("done",done);chapter.put("practiced",practiced);
            chapter.put("question_count",questions);chapter.put("progress",CourseService.progress(done,required));
        }
        return new ArrayList<>(grouped.values());
    }
}
