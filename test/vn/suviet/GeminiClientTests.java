package vn.suviet;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import vn.suviet.service.*;

/** Local HTTP contract tests: no real API key, provider request or database. */
public final class GeminiClientTests {
    private static int checks;
    private static final String KEY="local-test-key-not-a-real-secret";
    private record Response(int status,String body){}
    private static void check(boolean result,String message){
        checks++;
        if(!result)throw new AssertionError(message);
    }
    private static String generate(GeminiClient client)throws Exception{
        return client.generate("Test tutor",List.of(new AIClient.Message("user","Hello")),null,null,null);
    }
    private static void rejected(GeminiClient client,AtomicReference<Response> response,int status,String body,String expected)throws Exception{
        response.set(new Response(status,body));
        try{
            generate(client);
            throw new AssertionError("Expected AIException for HTTP "+status);
        }catch(AIClient.AIException e){
            check(e.getMessage().contains(expected),"Helpful error for HTTP "+status);
            check(!e.getMessage().contains(KEY)&&!e.getMessage().contains("RAW_PROVIDER_SECRET"),"Provider errors do not leak secrets");
        }
    }
    public static void main(String[] args)throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<Response> response=new AtomicReference<>(new Response(200,"""
            {"candidates":[{"finishReason":"STOP","content":{"parts":[{"thought":true,"text":"private reasoning"},{"text":"Xin "},{"text":"chào"}]}}]}
            """));
        AtomicReference<JsonObject> payload=new AtomicReference<>();
        AtomicReference<String> path=new AtomicReference<>(),method=new AtomicReference<>(),key=new AtomicReference<>(),type=new AtomicReference<>();
        server.createContext("/",exchange->{
            try{
                path.set(exchange.getRequestURI().toString());
                method.set(exchange.getRequestMethod());
                key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
                type.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                payload.set(JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject());
                Response current=response.get();
                byte[] body=current.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json; charset=UTF-8");
                exchange.sendResponseHeaders(current.status(),body.length);
                exchange.getResponseBody().write(body);
            }finally{exchange.close();}
        });
        Path config=Files.createTempFile("suviet-gemini-test-",".properties");
        String previous=System.getProperty("tutor.config");
        try{
            Properties properties=new Properties();
            properties.setProperty("ai.apiKey",KEY);
            properties.setProperty("ai.model","gemini-test-model");
            properties.setProperty("ai.baseUrl","http://127.0.0.1:"+server.getAddress().getPort()+"/v1beta/models/");
            properties.setProperty("ai.timeoutSeconds","5");
            try(var writer=Files.newBufferedWriter(config,StandardCharsets.UTF_8)){properties.store(writer,"Local test only");}
            System.setProperty("tutor.config",config.toString());
            server.start();
            GeminiClient client=new GeminiClient();
            check(generate(client).equals("Xin chào"),"UTF-8 text parts joined; thought parts excluded");
            check(path.get().equals("/v1beta/models/gemini-test-model:generateContent"),"Correct model URL without API key query");
            check(method.get().equals("POST")&&type.get().equals("application/json"),"JSON POST request");
            check(KEY.equals(key.get()),"Key sent in x-goog-api-key header");
            check(!payload.get().toString().contains(KEY),"Key excluded from payload");
            check(payload.get().getAsJsonObject("systemInstruction").getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString().equals("Test tutor"),"System instruction preserved");
            byte[] image={1,2,3,4};
            String schema="{\"type\":\"object\",\"properties\":{\"hint\":{\"type\":\"string\"}}}";
            client.generate("Tutor",List.of(new AIClient.Message("user","First"),new AIClient.Message("model","Hint"),new AIClient.Message("user","Read image")),image,"image/png",schema);
            JsonArray contents=payload.get().getAsJsonArray("contents");
            check(contents.size()==3&&contents.get(1).getAsJsonObject().get("role").getAsString().equals("model"),"Conversation history preserved");
            check(contents.get(0).getAsJsonObject().getAsJsonArray("parts").size()==1,"Image not repeated on earlier turns");
            JsonObject inline=contents.get(2).getAsJsonObject().getAsJsonArray("parts").get(1).getAsJsonObject().getAsJsonObject("inlineData");
            check(inline.get("mimeType").getAsString().equals("image/png")&&inline.get("data").getAsString().equals(Base64.getEncoder().encodeToString(image)),"Image MIME and base64 preserved");
            JsonObject generation=payload.get().getAsJsonObject("generationConfig");
            check(generation.get("responseMimeType").getAsString().equals("application/json")&&generation.get("responseJsonSchema").equals(JsonParser.parseString(schema)),"Structured output schema included");

            String invalid="{\"error\":{\"message\":\"RAW_PROVIDER_SECRET "+KEY+"\",\"details\":[{\"reason\":\"API_KEY_INVALID\"}]}}";
            rejected(client,response,400,invalid,"API key Gemini không hợp lệ");
            rejected(client,response,403,invalid,"API key Gemini không hợp lệ");
            rejected(client,response,400,"{\"error\":{\"details\":[null,{}]}}","HTTP 400");
            rejected(client,response,401,"RAW_PROVIDER_SECRET","quyền truy cập");
            rejected(client,response,403,"{\"error\":{\"message\":\"RAW_PROVIDER_SECRET\"}}","quyền truy cập");
            rejected(client,response,404,"{}","Không tìm thấy model");
            rejected(client,response,429,"{}","hạn mức");
            rejected(client,response,500,"{}","tạm thời");
            rejected(client,response,503,"<html>RAW_PROVIDER_SECRET</html>","tạm thời");
            rejected(client,response,418,"RAW_PROVIDER_SECRET","HTTP 418");
            rejected(client,response,200,"{\"candidates\":[]}","chưa thể trả lời");
            rejected(client,response,200,"{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}","chưa hoàn chỉnh");
            rejected(client,response,200,"{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[]}}]}","nội dung trống");
            rejected(client,response,200,"<html>RAW_PROVIDER_SECRET</html>","không đọc được");
            rejected(client,response,200,"x".repeat(500001),"vượt giới hạn");
            System.out.println("PASS: "+checks+" Gemini HTTP contract checks (local stub; no Google calls)");
        }finally{
            server.stop(0);
            Files.deleteIfExists(config);
            if(previous==null)System.clearProperty("tutor.config");else System.setProperty("tutor.config",previous);
        }
    }
}