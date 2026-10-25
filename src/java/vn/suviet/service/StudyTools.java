package vn.suviet.service;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import vn.suviet.dao.SettingsDao;
public final class StudyTools {
    private static final JsonObject DATA=load();
    private static JsonObject load(){
        try(var in=StudyTools.class.getResourceAsStream("/history-study-tools.json")){
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException ex){throw new IllegalStateException(ex);}
    }
    private static JsonObject manifest()throws SQLException {
        return JsonParser.parseString(new SettingsDao().get("seed.history6.kntt.v1","{}")).getAsJsonObject();
    }
    public static Map<String,Object> card(int item)throws SQLException {
        var m=manifest();if(!m.has("items"))return null;
        for(var entry:m.getAsJsonArray("items")){
            var row=entry.getAsJsonObject();if(row.get("item_id").getAsInt()!=item)continue;
            for(var lesson:DATA.getAsJsonArray("lessons")){
                var l=lesson.getAsJsonObject();if(l.get("number").getAsInt()==row.get("number").getAsInt())return Map.of("front",l.get("front").getAsString(),"back",l.get("back").getAsString());
            }
        }return null;
    }
    public static List<Map<String,Object>> timeline(int course)throws SQLException {
        var m=manifest();var result=new ArrayList<Map<String,Object>>();if(!m.has("course_id")||m.get("course_id").getAsInt()!=course)return result;
        for(var event:DATA.getAsJsonArray("events")){
            var e=event.getAsJsonObject();int item=0;
            for(var entry:m.getAsJsonArray("items")){var row=entry.getAsJsonObject();if(row.get("number").getAsInt()==e.get("lesson").getAsInt())item=row.get("item_id").getAsInt();}
            result.add(Map.of("time",e.get("time").getAsString(),"title",e.get("title").getAsString(),"item",item));
        }return result;
    }
}
