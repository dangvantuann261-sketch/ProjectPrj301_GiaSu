package vn.suviet.controller;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.util.Map;
import vn.suviet.service.*;
import vn.suviet.util.Web;

@WebServlet("/api/essay-step")
public final class EssayStepServlet extends BaseServlet {
    @Override protected void handle(HttpServletRequest r,HttpServletResponse p)throws Exception {
        int id=Web.positive(r,"answerId");EssayTutoringService service=new EssayTutoringService();
        try{
            if("POST".equals(r.getMethod()))service.next(id,user(r).getId(),Web.number(r,"version",-1),Web.text(r,"action",10,true),Web.text(r,"value",1000,false));
            Web.json(p,200,service.view(id,user(r).getId()));
        }catch(AIClient.AIException e){Web.json(p,502,Map.of("error",e.getMessage()));}
    }
}
