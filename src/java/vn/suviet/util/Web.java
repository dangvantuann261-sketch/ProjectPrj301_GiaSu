package vn.suviet.util;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import com.google.gson.Gson;
import java.io.*;
import java.net.URI;
import java.util.*;
public final class Web {
    public static final Gson JSON = new Gson();
    private Web() {
    }
    public static String text(HttpServletRequest r, String name, int max, boolean required) {
        String s = Objects.toString(r.getParameter(name), "").trim();
        if ((required && s.isEmpty()) || s.length() > max) throw new IllegalArgumentException("Trường " + name + " không hợp lệ (tối đa " + max + " ký tự).");
        return s;
    }
    public static int number(HttpServletRequest r, String name, int fallback) {
        String s = r.getParameter(name);
        if (s == null || s.isBlank()) return fallback;
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Giá trị số không hợp lệ: " + name);
        }
    }
    public static int positive(HttpServletRequest r, String name) {
        int v=number(r,name,0);
        if(v<1) throw new IllegalArgumentException("Thiếu lựa chọn: "+name);
        return v;
    }
    public static void view(HttpServletRequest r, HttpServletResponse p, String view) throws ServletException, IOException {
        r.getRequestDispatcher("/WEB-INF/views/" + view + ".jsp").forward(r,p);
    }
    public static void redirect(HttpServletRequest r, HttpServletResponse p, String path) throws IOException {
        p.sendRedirect(r.getContextPath()+path);
    }
    public static void flash(HttpServletRequest r, String message) {
        r.getSession().setAttribute("flash",message);
    }
    public static void json(HttpServletResponse p, int status, Object data) throws IOException {
        p.setStatus(status);
        p.setContentType("application/json;charset=UTF-8");
        p.getWriter().write(JSON.toJson(data));
    }
    public static String safeUrl(String value) {
        if(value==null || value.isBlank()) return "";
        try {
            URI u=URI.create(value);
            if(!Set.of("https","http").contains(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null) throw new IllegalArgumentException();
            return value;
        }
        catch(IllegalArgumentException e) {
            throw new IllegalArgumentException("Liên kết phải là URL http/https hợp lệ.");
        }
    }
    /** Prepare display fields without changing the stored lesson or accepting arbitrary links. */
    public static void prepareLecture(Map<String,Object> lecture) {
        lecture.put("embed",youtubeEmbed((String)lecture.get("video_url")));
        String body=Objects.toString(lecture.get("body"),"");
        String marker="[Nguồn bài học] ";
        int start=body.lastIndexOf(marker);
        if(start>=0){
            String source=body.substring(start+marker.length()).trim();
            try {
                URI uri=URI.create(source);
                if("https".equals(uri.getScheme()) && "loigiaihay.com".equals(uri.getHost()) && uri.getUserInfo()==null){
                    lecture.put("source_url",source);
                    lecture.put("body",body.substring(0,start).stripTrailing());
                }
            } catch(IllegalArgumentException ignored) { /* Keep the original text. */ }
        }
    }
    public static String youtubeEmbed(String link) {
        if(link==null || link.isBlank()) return "";
        URI u=URI.create(link);
        String id="", host=Objects.toString(u.getHost(),"").toLowerCase(Locale.ROOT);
        if(host.equals("youtu.be")) id=u.getPath().substring(1);
        else if(Set.of("youtube.com","www.youtube.com","m.youtube.com").contains(host)) {
            if(u.getPath().startsWith("/embed/") || u.getPath().startsWith("/shorts/")) id=u.getPath().substring(u.getPath().lastIndexOf('/')+1);
            else if(u.getRawQuery()!=null) for(String pair:u.getRawQuery().split("&")) if(pair.startsWith("v=")) id=pair.substring(2);
        }
        return id.matches("[A-Za-z0-9_-]{11}") ? "https://www.youtube-nocookie.com/embed/"+id : "";
    }
}
