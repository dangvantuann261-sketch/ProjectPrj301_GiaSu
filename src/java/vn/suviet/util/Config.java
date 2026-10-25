package vn.suviet.util;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
public final class Config {
    private static final Properties VALUES = load();
    private Config() {
    }
    private static Properties load() {
        Properties p = new Properties();
        String path = System.getProperty("tutor.config", System.getenv("TUTOR_CONFIG"));
        if (path != null && !path.isBlank()) {
            try (Reader r = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
                p.load(r);
            }
            catch (IOException e) {
                throw new IllegalStateException("Không đọc được config.properties bên ngoài WAR", e);
            }
        }
        return p;
    }
    public static String get(String key, String fallback) {
        return VALUES.getProperty(key, fallback).trim();
    }
    public static String require(String key) {
        String s = get(key, "");
        if (s.isBlank() || s.equals("CHANGE_ME")) throw new IllegalStateException("Chưa cấu hình " + key);
        return s;
    }
    public static String defaultPrompt() {
        try (InputStream in = Config.class.getResourceAsStream("/system-prompt.txt")) {
            if (in == null) throw new IOException("Missing system-prompt.txt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
