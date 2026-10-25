package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.Question;
import vn.suviet.service.*;
import vn.suviet.util.*;
@WebServlet("/api/chat")
public final class ChatServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        boolean post=r.getMethod().equals("POST");
        if(!post&&!r.getMethod().equals("GET")){p.sendError(405);return;}
        int uid=user(r).getId(),answerId=Web.number(r,"answerId",0),itemId=Web.number(r,"itemId",0);
        if(answerId<0||itemId<0||answerId>0&&itemId>0)throw new IllegalArgumentException("Ngữ cảnh không hợp lệ.");
        String context="Học sinh đang học Lịch sử lớp 6. Bắt đầu từ điều em đã biết.",key="general";
        if(answerId>0){
            var answer=new PracticeDao().answer(answerId,uid,false);
            if(answer==null){Web.json(p,404,Map.of("error","Không tìm thấy bài làm."));return;}
            Question q=Question.restore((String)answer.get("question_snapshot"));
            if("ESSAY".equals(q.getKind())){Web.json(p,409,Map.of("error","Bài tự luận dùng phần Luyện từng bước bên cạnh ảnh bài làm."));return;}
            context="Đề và hướng dẫn chấm: "+q.snapshot()+"; học sinh đã chọn: "+answer.get("chosen")+"; nhận xét ban đầu: "+answer.get("feedback");
            key="answer:"+answerId;
        }else if(itemId>0){
            var item=LessonContext.accessible(user(r),itemId);
            String body=Objects.toString(item.get("body"),"");
            context="Bài đang học: "+item.get("title")+". Chương: "+item.get("section_title")+". Nội dung bài: "+body.substring(0,Math.min(body.length(),24000));
            key="lesson:"+itemId;
        }
        ChatDao dao=new ChatDao();
        if(!post){Web.json(p,200,Map.of("messages",dao.history(uid,key)));return;}
        String message=Web.text(r,"message",3000,true);
        var lease=dao.claim(uid,key,"1".equals(r.getParameter("reset")));
        if(lease==null){Web.json(p,429,Map.of("error","Gia sư đang xử lý, em hãy chờ một chút."));return;}
        try{
            var history=new ArrayList<>(lease.messages());history.add(new AIClient.Message("user",message));
            String reply=new TutorService().chat(context,history);history.add(new AIClient.Message("model",reply));
            if(!dao.finish(uid,key,lease,history)){Web.json(p,409,Map.of("error","Yêu cầu đã hết hạn. Em hãy tải lại hội thoại."));return;}
            Web.json(p,200,Map.of("reply",reply));
        }catch(AIClient.AIException ex){Web.json(p,502,Map.of("error",ex.getMessage()));}
        finally{dao.release(uid,key,lease);}
    }
}