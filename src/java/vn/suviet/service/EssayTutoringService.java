package vn.suviet.service;

import com.google.gson.reflect.TypeToken;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.Question;
import vn.suviet.util.Web;

public final class EssayTutoringService {
    public Map<String,Object> view(int id,int uid)throws Exception {
        requireAnswer(id,uid);
        var state=new EssayDao().state(id);
        return Map.of("phase",state.get("phase"),"revision",state.get("revision"),"version",state.get("version"),"step",state.get("step_no"),"formula",state.get("formula"),"question",state.get("question"),"messages",history(state));
    }
    private Map<String,Object> requireAnswer(int id,int uid)throws Exception {
        var answer=new PracticeDao().answer(id,uid,false);
        if(answer==null||!"ESSAY".equals(answer.get("kind")))throw new AccessDeniedException();
        return answer;
    }
    private static List<AIClient.Message> history(Map<String,Object> state){
        return Web.JSON.fromJson((String)state.get("history"),new TypeToken<ArrayList<AIClient.Message>>(){}.getType());
    }
    public void next(int id,int uid,int version,String action,String value)throws Exception {
        var answer=requireAnswer(id,uid);EssayDao dao=new EssayDao();var state=dao.state(id);
        boolean first="NEW".equals(state.get("phase"));
        if(!"AI_GRADED".equals(answer.get("grading_status"))||answer.get("score")==null||((Number)answer.get("score")).doubleValue()>=10)
            throw new IllegalArgumentException("Hãy nhận đánh giá AI của ảnh trước khi luyện các bước sửa bài.");
        if("RESUBMIT".equals(state.get("phase")))throw new IllegalArgumentException("Em đã hoàn tất hướng dẫn. Hãy làm lại và nộp ảnh mới.");
        if(first?!"start".equals(action):!"answer".equals(action))throw new IllegalArgumentException("Thao tác không đúng bước hiện tại.");
        if(!first&&(value==null||value.isBlank()||value.length()>1000))throw new IllegalArgumentException("Hãy tự điền đáp số/ý còn thiếu, tối đa 1.000 ký tự.");
        String lease=UUID.randomUUID().toString();
        if(!dao.claim(id,uid,version,lease))throw new IllegalArgumentException("Bước đã thay đổi hoặc AI đang xử lý ở cửa sổ khác. Hãy tải lại phần hướng dẫn.");
        try{
            List<AIClient.Message> messages=history(state);
            String student=first?"Bắt đầu. Cho em công thức/khung của bước đầu tiên để tự điền.":value;
            List<AIClient.Message> request=new ArrayList<>(messages.subList(Math.max(0,messages.size()-40),messages.size()));request.add(new AIClient.Message("user",student));
            String context="Đề: "+Question.restore((String)answer.get("question_snapshot")).snapshot()+"; Bài đã nộp: "+answer.get("transcript")+"; Bước hiện tại: "+state.get("step_no")+"; Công thức hiện tại: "+state.get("formula")+"; Yêu cầu hiện tại: "+state.get("question");
            var result=new TutorService().essayStep(new SettingsDao().get("essay_prompt",AiSettingsService.ESSAY_RULES),context,request,first);
            int step=Sql.intValue(state,"step_no");String formula=(String)state.get("formula"),question=(String)state.get("question");
            String phase="WAITING",reply;
            if(result.complete()){
                phase="RESUBMIT";formula="";question="";
                reply="Em đã hoàn thành các bước. Bây giờ em phải tự làm lại toàn bộ bài trên giấy, chụp ảnh mới và nộp lại để được đánh giá. Bài chưa hoàn tất vòng sửa cho đến khi nộp ảnh mới.";
            }else{
                if(first||result.accepted()){step++;formula=result.formula();question=result.question();}
                // The server preserves the current blank on an incorrect answer, regardless of AI output.
                reply=(first?"":result.accepted()?"Đúng bước này. ":"Chưa đúng. Em hãy kiểm tra và điền lại. ")+"Bước "+step+"\n"+formula+"\n"+question;
            }
            messages.add(new AIClient.Message("user",student));messages.add(new AIClient.Message("model",reply));
            dao.finish(id,lease,phase,step,formula,question,Web.JSON.toJson(messages));
        }finally{dao.release(id,lease);}
    }
}
