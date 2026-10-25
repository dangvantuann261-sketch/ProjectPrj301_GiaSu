package vn.suviet.service;
import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import vn.suviet.util.Config;
public final class GeminiClient implements AIClient {
    private final boolean managed,essay;
    public GeminiClient(){this(false,false);}
    private GeminiClient(boolean managed,boolean essay){this.managed=managed;this.essay=essay;}
    public static GeminiClient managed(boolean essay){return new GeminiClient(true,essay);}
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @Override public String generate(String system,List<Message> history,byte[] image,String mime,String schema)throws AIException{
        AiSettingsService.Connection connection;
        try{connection=managed?new AiSettingsService().connection(essay):AiSettingsService.fileConnection();}
        catch(Exception e){throw new AIException("Không đọc được cấu hình AI. Quản trị viên cần kiểm tra cấu hình và khóa mã hóa.");}
        String key=connection.apiKey();
        if(key.isBlank()||key.equals("CHANGE_ME"))throw new AIException("Chưa cấu hình API key Gemini. Hãy liên hệ quản trị viên hoặc thử lại sau.");
        String model=connection.model();
        if(!model.matches("[a-zA-Z0-9._-]+"))throw new AIException("Tên model AI không hợp lệ.");
        JsonObject payload=new JsonObject();
        JsonObject instruction=new JsonObject();
        JsonArray parts=new JsonArray();
        JsonObject st=new JsonObject();
        st.addProperty("text",system);
        parts.add(st);
        instruction.add("parts",parts);
        payload.add("systemInstruction",instruction);
        JsonArray contents=new JsonArray();
        for(int i=0;i<history.size();i++){
            Message message=history.get(i);
            JsonObject content=new JsonObject();
            content.addProperty("role",message.role());
            JsonArray segments=new JsonArray();
            JsonObject text=new JsonObject();
            text.addProperty("text",message.text());
            segments.add(text);
            if(image!=null&&i==history.size()-1){
                JsonObject inline=new JsonObject();
                inline.addProperty("mimeType",mime);
                inline.addProperty("data",Base64.getEncoder().encodeToString(image));
                JsonObject data=new JsonObject();
                data.add("inlineData",inline);
                segments.add(data);
            }
            content.add("parts",segments);
            contents.add(content);
        }
        payload.add("contents",contents);
        JsonObject generation=new JsonObject();
        generation.addProperty("temperature",0.35);
        generation.addProperty("maxOutputTokens",8192);
        if(schema!=null){
            generation.addProperty("responseMimeType","application/json");
            generation.add("responseJsonSchema",JsonParser.parseString(schema));
        }
        payload.add("generationConfig",generation);
        try{
            String base=connection.baseUrl();
            HttpRequest req=HttpRequest.newBuilder(URI.create(base+model+":generateContent")).timeout(Duration.ofSeconds(Math.max(5,Math.min(90,connection.timeout())))).header("Content-Type","application/json").header("x-goog-api-key",key).POST(HttpRequest.BodyPublishers.ofString(payload.toString())).build();
            HttpResponse<String> res=HTTP.send(req,HttpResponse.BodyHandlers.ofString());
            if(res.body().length()>500000)throw new AIException("Phản hồi AI vượt giới hạn.");
            if(res.statusCode()!=200&&hasErrorReason(res.body(),"API_KEY_INVALID"))throw new AIException("API key Gemini không hợp lệ hoặc đã bị thu hồi. Quản trị viên cần cập nhật key tại Cấu hình AI.");
            if(res.statusCode()==429)throw new AIException("AI đang quá tải hoặc đã hết hạn mức. Hãy thử lại sau ít phút.");
            if(res.statusCode()==401||res.statusCode()==403)throw new AIException("Gemini từ chối quyền truy cập. Quản trị viên cần kiểm tra trạng thái API key, giới hạn key và quyền dùng model trong Google AI Studio.");
            if(res.statusCode()==404)throw new AIException("Không tìm thấy model Gemini hoặc key chưa có quyền dùng model này. Quản trị viên cần kiểm tra ai.model và danh sách model của tài khoản.");
            if(res.statusCode()==400)throw new AIException("Gemini không chấp nhận yêu cầu. Quản trị viên cần kiểm tra API key, model và định dạng dữ liệu gửi lên (HTTP 400).");
            if(res.statusCode()>=500)throw new AIException("Dịch vụ Gemini đang tạm thời gặp sự cố. Hãy thử lại sau ít phút.");
            if(res.statusCode()!=200)throw new AIException("Dịch vụ AI trả lỗi HTTP "+res.statusCode()+". Hãy kiểm tra model và thử lại.");
            JsonObject root=JsonParser.parseString(res.body()).getAsJsonObject();
            JsonArray candidates=root.getAsJsonArray("candidates");
            if(candidates==null||candidates.isEmpty())throw new AIException("AI chưa thể trả lời nội dung này. Hãy diễn đạt lại hoặc hỏi giáo viên.");
            JsonObject candidate=candidates.get(0).getAsJsonObject();
            if(candidate.has("finishReason")&&!candidate.get("finishReason").getAsString().equals("STOP"))throw new AIException("Phản hồi AI chưa hoàn chỉnh. Hãy thử lại với yêu cầu ngắn hơn.");
            StringBuilder out=new StringBuilder();
            for(JsonElement part:candidate.getAsJsonObject("content").getAsJsonArray("parts")){
                JsonObject obj=part.getAsJsonObject();
                if(obj.has("text")&&(!obj.has("thought")||!obj.get("thought").getAsBoolean()))out.append(obj.get("text").getAsString());
            }
            if(out.isEmpty())throw new AIException("AI trả về nội dung trống. Hãy thử lại.");
            return out.toString();
        }
        catch(HttpTimeoutException e){
            throw new AIException("AI phản hồi quá lâu. Em hãy bấm gửi lại.",e);
        }
        catch(InterruptedException e){
            Thread.currentThread().interrupt();
            throw new AIException("Yêu cầu AI đã bị ngắt.",e);
        }
        catch(AIException e){
            throw e;
        }
        catch(Exception e){
            throw new AIException("Không kết nối hoặc không đọc được phản hồi AI. Hãy thử lại.",e);
        }
    }
    // Inspect only machine-readable reasons; never expose Google's raw body or credentials.
    private static boolean hasErrorReason(String body,String reason){
        try{
            JsonObject error=JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("error");
            if(error==null)return false;
            JsonArray details=error.getAsJsonArray("details");
            if(details==null)return false;
            for(JsonElement detail:details){
                if(detail.isJsonObject()){
                    JsonElement value=detail.getAsJsonObject().get("reason");
                    if(value!=null&&value.isJsonPrimitive()&&reason.equals(value.getAsString()))return true;
                }
            }
        }catch(RuntimeException ignored){
            // Non-JSON error pages still receive a safe HTTP-status message.
        }
        return false;
    }
}
