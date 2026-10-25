package vn.suviet.controller;
import jakarta.servlet.annotation.*;
import jakarta.servlet.http.*;
import java.util.*;
import vn.suviet.dao.*;
import vn.suviet.model.*;
import vn.suviet.service.*;
import vn.suviet.util.*;
@WebServlet("/student/*")
@MultipartConfig(fileSizeThreshold=1048576,maxFileSize=5242880,maxRequestSize=6291456)
public final class StudentServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception{
        String path=Objects.toString(r.getPathInfo(),"/learn");
        boolean post=r.getMethod().equals("POST");
        int uid=user(r).getId();
        PracticeDao dao=new PracticeDao();
        switch(path){
            case "/learn":
            if(post){p.sendError(405);return;}
            Web.redirect(r,p,"/courses");
            break;
            case "/topic":
            if(post){p.sendError(405);return;}
            var target=Sql.one("SELECT TOP 1 i.id FROM course_items i JOIN lectures l ON l.id=i.lecture_id JOIN courses c ON c.id=i.course_id WHERE l.topic_id=? AND l.deleted=0 AND l.hidden=0 AND c.status='PUBLISHED' ORDER BY c.id,i.position",Web.positive(r,"id"));
            if(target==null){Web.redirect(r,p,"/courses");return;}
            Web.redirect(r,p,"/student/lesson?id="+target.get("id"));
            break;
            case "/start":
            if(!post){
                p.sendError(405);
                return;
            }
            String kind=Web.text(r,"kind",10,true);
            String scope=Web.text(r,"scope",10,false);
            if(!Set.of("", "lesson", "chapter").contains(scope))throw new IllegalArgumentException("Phạm vi luyện tập không hợp lệ.");
            int itemId=Web.positive(r,"item_id");
            int started=dao.startScoped(uid,itemId,"chapter".equals(scope),kind);
            Web.redirect(r,p,"/student/practice?id="+started);
            break;
            case "/practice":
            int attemptId=Web.positive(r,"id");
            var attempt=dao.owned(attemptId,uid);
            if(attempt==null){
                p.sendError(404);
                return;
            }
            int pos=Sql.intValue(attempt,"current_pos"), total=Sql.intValue(attempt,"total");
            r.setAttribute("attempt",attempt);
            if(pos>=total){
                r.setAttribute("answers",dao.answers(attemptId));
                Web.view(r,p,"student/summary");
                return;
            }
            Question q=Question.restore((String)dao.item(attemptId,pos).get("question_snapshot"));
            r.setAttribute("question",q);
            if(post){
                if(Web.number(r,"position",-1)!=pos)throw new IllegalArgumentException("Câu hỏi đã thay đổi. Hãy tải lại trang.");
                int answerId;
                if(q.getKind().equals("MCQ")){
                    String chosen=Web.text(r,"chosen",1,true);
                    if(!Set.of("A","B","C","D").contains(chosen))throw new IllegalArgumentException("Hãy chọn một đáp án.");
                    boolean correct=chosen.equals(q.getCorrectOption());
                    answerId=dao.save(attemptId,uid,pos,chosen,correct?10.0:0.0,correct?q.getExplanation():"Câu trả lời chưa đúng. Gia sư AI sẽ giúp em kiểm tra từng bước.","","","");
                }
                else{
                    UploadService uploads=new UploadService();
                    UploadService.Image image=uploads.validate(r.getPart("image"));
                    answerId=new PracticeService().submitImage(attemptId,uid,pos,image);
                }
                Web.redirect(r,p,"/student/review?id="+answerId);
                return;
            }
            Web.view(r,p,"student/practice");
            break;
            case "/review":
            var answer=dao.answer(Web.positive(r,"id"),uid,false);
            if(answer==null){
                p.sendError(404);
                return;
            }
            r.setAttribute("answer",answer);
            r.setAttribute("question",Question.restore((String)answer.get("question_snapshot")));
            if("ESSAY".equals(answer.get("kind"))){r.setAttribute("essayState",new EssayDao().state(Sql.intValue(answer,"id")));r.setAttribute("revisions",new EssayDao().revisions(Sql.intValue(answer,"id")));}
            Web.view(r,p,"student/review");
            break;
            case "/resubmit":
            if(!post){p.sendError(405);return;}
            int resubmitId=Web.positive(r,"id");
            new PracticeService().resubmitImage(resubmitId,uid,Web.positive(r,"revision"),new UploadService().validate(r.getPart("image")));
            Web.flash(r,"Đã lưu ảnh làm lại. Em hãy nhận đánh giá AI cho lần nộp mới.");
            Web.redirect(r,p,"/student/review?id="+resubmitId);
            break;
            case "/grade":
            if(!post){p.sendError(405);return;}
            int gradingId=Web.positive(r,"id");new PracticeService().grade(gradingId,uid);
            Web.redirect(r,p,"/student/review?id="+gradingId);
            break;
            case "/history":
            int count=Sql.intValue(Sql.one("SELECT COUNT(*) AS n FROM attempts WHERE user_id=?",uid),"n"),pages=Math.max(1,(count+9)/10),page=Math.max(1,Math.min(pages,Web.number(r,"page",1)));
            r.setAttribute("rows",dao.history(uid,page));
            r.setAttribute("page",page);
            r.setAttribute("pages",pages);
            Web.view(r,p,"student/history");
            break;
            case "/tutor":
            if(post){p.sendError(405);return;}
            int contextItem=Web.number(r,"itemId",0);
            if(contextItem>0)r.setAttribute("item",LessonContext.accessible(user(r),contextItem));
            r.setAttribute("initialMessage",Web.text(r,"question",3000,false));
            Web.view(r,p,"student/tutor");
            break;
            default:p.sendError(404);
        }
    }
}
