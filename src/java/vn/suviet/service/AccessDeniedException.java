package vn.suviet.service;

public final class AccessDeniedException extends RuntimeException {
    public AccessDeniedException() { super("Bạn không có quyền truy cập tài nguyên này."); }
    public AccessDeniedException(String message) { super(message); }
}