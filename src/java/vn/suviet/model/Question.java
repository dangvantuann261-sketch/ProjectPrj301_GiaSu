package vn.suviet.model;
import java.util.*;
import vn.suviet.util.Web;
public final class Question {
    private int id, topicId;
    private String kind, prompt, optionA, optionB, optionC, optionD, correctOption, explanation, difficulty;
    public int getId(){
        return id;
    }
    public int getTopicId(){
        return topicId;
    }
    public String getKind(){
        return kind;
    }
    public String getPrompt(){
        return prompt;
    }
    public String getOptionA(){
        return optionA;
    }
    public String getOptionB(){
        return optionB;
    }
    public String getOptionC(){
        return optionC;
    }
    public String getOptionD(){
        return optionD;
    }
    public String getCorrectOption(){
        return correctOption;
    }
    public String getExplanation(){
        return explanation;
    }
    public String getDifficulty(){
        return difficulty;
    }
    public void setTopicId(int v){
        topicId=v;
    }
    public void setKind(String v){
        kind=v;
    }
    public static Question from(Map<String,Object> m) {
        Question q=new Question();
        q.id=((Number)m.get("id")).intValue();
        q.topicId=((Number)m.get("topic_id")).intValue();
        q.kind=(String)m.get("kind");
        q.prompt=(String)m.get("prompt");
        q.optionA=(String)m.get("option_a");
        q.optionB=(String)m.get("option_b");
        q.optionC=(String)m.get("option_c");
        q.optionD=(String)m.get("option_d");
        q.correctOption=(String)m.get("correct_option");
        q.explanation=(String)m.get("explanation");
        q.difficulty=(String)m.get("difficulty");
        return q;
    }
    public Map<String,Object> values() {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("topic_id",topicId);
        m.put("kind",kind);
        m.put("prompt",prompt);
        m.put("option_a",Objects.toString(optionA,""));
        m.put("option_b",Objects.toString(optionB,""));
        m.put("option_c",Objects.toString(optionC,""));
        m.put("option_d",Objects.toString(optionD,""));
        m.put("correct_option",Objects.toString(correctOption,""));
        m.put("explanation",explanation);
        m.put("difficulty",difficulty);
        return m;
    }
    public String snapshot(){
        return Web.JSON.toJson(this);
    }
    public static Question restore(String json){
        return Web.JSON.fromJson(json,Question.class);
    }
    public void validate() {
        if(!Set.of("MCQ","ESSAY").contains(Objects.toString(kind,""))) throw new IllegalArgumentException("Loại câu hỏi không hợp lệ.");
        check(prompt,4000);
        check(explanation,8000);
        if(!Set.of("EASY","MEDIUM","HARD").contains(Objects.toString(difficulty,""))) throw new IllegalArgumentException("Độ khó không hợp lệ.");
        if("MCQ".equals(kind)) {
            check(optionA,1000);
            check(optionB,1000);
            check(optionC,1000);
            check(optionD,1000);
            if(!Set.of("A","B","C","D").contains(Objects.toString(correctOption,""))) throw new IllegalArgumentException("Đáp án phải là A, B, C hoặc D.");
        }
    }
    private void check(String s,int max){
        if(s==null || s.isBlank() || s.length()>max) throw new IllegalArgumentException("Nội dung câu hỏi thiếu hoặc quá dài.");
    }
}
