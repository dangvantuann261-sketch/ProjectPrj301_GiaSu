package vn.suviet.model;
import java.util.*;
public enum Entity {
    SUBJECTS("subjects","Môn học","name",null,null,List.of("name","grade","description")),
    LESSONS("lessons","Bài học","title","subjects","subject_id",List.of("subject_id","title","description")),
    TOPICS("topics","Chủ đề","title","lessons","lesson_id",List.of("lesson_id","title","description")),
    LECTURES("lectures","Bài giảng","title","topics","topic_id",List.of("topic_id","title","description","body","video_url")),
    QUESTIONS("questions","Câu hỏi","prompt","topics","topic_id",List.of("topic_id","kind","prompt","option_a","option_b","option_c","option_d","correct_option","explanation","difficulty"));
    public final String table,label,title,parent,parentColumn;
    public final List<String> fields;
    Entity(String t,String l,String title,String p,String pc,List<String> f){
        table=t;
        label=l;
        this.title=title;
        parent=p;
        parentColumn=pc;
        fields=f;
    }
    public String getTable(){
        return table;
    }
    public String getLabel(){
        return label;
    }
    public String getTitle(){
        return title;
    }
    public String getParent(){
        return parent;
    }
    public String getParentColumn(){
        return parentColumn;
    }
    public List<String> getFields(){
        return fields;
    }
    public static Entity parse(String s){
        for(Entity e:values())if(e.table.equals(s))return e;
        throw new IllegalArgumentException("Danh mục không hợp lệ.");
    }
}
