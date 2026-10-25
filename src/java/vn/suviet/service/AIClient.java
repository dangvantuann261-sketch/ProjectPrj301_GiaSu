package vn.suviet.service;
import java.util.*;
public interface AIClient {
    record Message(String role,String text) {
    }
    String generate(String system,List<Message> history,byte[] image,String mime,String schema)throws AIException;
    final class AIException extends Exception {
        public AIException(String message){
            super(message);
        }
        public AIException(String message,Throwable cause){
            super(message,cause);
        }
    }
}
