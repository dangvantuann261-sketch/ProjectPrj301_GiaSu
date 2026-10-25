package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.nio.file.Files;
import vn.suviet.dao.*;
import vn.suviet.model.Question;
import vn.suviet.service.UploadService;
import vn.suviet.util.*;
@WebServlet(urlPatterns={
    "/review","/upload","/results"
}
)
public final class ReviewServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception{
        boolean teacher=user(r).getRole().equals("ADMIN");
        if(user(r).getRole().equals("TEACHER"))throw new vn.suviet.service.AccessDeniedException();
        PracticeDao dao=new PracticeDao();
        if(r.getServletPath().equals("/results")){
            int id=Web.positive(r,"id");
            var attempt=Sql.one("SELECT a.*,t.title AS topic_title FROM attempts a JOIN topics t ON t.id=a.topic_id WHERE a.id=? AND (?=1 OR a.user_id=?)",id,teacher?1:0,user(r).getId());
            if(attempt==null){
                p.sendError(404);
                return;
            }
            r.setAttribute("attempt",attempt);
            r.setAttribute("answers",dao.answers(id));
            r.setAttribute("teacherView",teacher);
            Web.view(r,p,"student/summary");
            return;
        }
        var answer=dao.answer(Web.positive(r,"id"),user(r).getId(),teacher);
        if(answer==null){
            p.sendError(404);
            return;
        }
        if(r.getServletPath().equals("/upload")){
            String name=(String)answer.get("upload_name");
            int revision=Web.number(r,"revision",0);
            if(revision>0){
                var archived=Sql.one("SELECT upload_name,upload_mime FROM essay_answer_revisions WHERE answer_id=? AND revision=?",Sql.intValue(answer,"id"),revision);
                if(archived==null){p.sendError(404);return;}
                name=(String)archived.get("upload_name");answer.put("upload_mime",archived.get("upload_mime"));
            }
            if(name==null||name.isBlank()){
                p.sendError(404);
                return;
            }
            var file=new UploadService().resolve(name);
            if(!Files.isRegularFile(file)){
                p.sendError(404);
                return;
            }
            p.setContentType((String)answer.get("upload_mime"));
            p.setHeader("Content-Disposition","inline; filename=\"bai-lam"+(name.endsWith(".png")?".png":".jpg")+"\"");
            p.setContentLengthLong(Files.size(file));
            Files.copy(file,p.getOutputStream());
            return;
        }
        r.setAttribute("answer",answer);
        r.setAttribute("question",Question.restore((String)answer.get("question_snapshot")));
        r.setAttribute("teacherView",teacher);
        if("ESSAY".equals(answer.get("kind"))){r.setAttribute("essayState",new EssayDao().state(Sql.intValue(answer,"id")));r.setAttribute("revisions",new EssayDao().revisions(Sql.intValue(answer,"id")));}
        Web.view(r,p,"student/review");
    }
}
