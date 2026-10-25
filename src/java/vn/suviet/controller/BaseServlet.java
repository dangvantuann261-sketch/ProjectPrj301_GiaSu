package vn.suviet.controller;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.suviet.model.User;
public abstract class BaseServlet extends HttpServlet {
    protected User user(HttpServletRequest r){
        return (User)r.getSession().getAttribute("user");
    }
    @Override protected void doGet(HttpServletRequest r,HttpServletResponse p)throws ServletException,IOException{
        dispatch(r,p);
    }
    @Override protected void doPost(HttpServletRequest r,HttpServletResponse p)throws ServletException,IOException{
        dispatch(r,p);
    }
    private void dispatch(HttpServletRequest r,HttpServletResponse p)throws ServletException,IOException{
        try{
            handle(r,p);
        }
        catch(RuntimeException e){
            throw e;
        }
        catch(Exception e){
            throw new ServletException(e);
        }
    }
    protected abstract void handle(HttpServletRequest r,HttpServletResponse p)throws Exception;
}
