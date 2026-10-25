package vn.suviet.util;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
public final class Passwords {
    private static final int ITERATIONS = 600000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private Passwords() {
    }
    // PBKDF2 của JDK: salt riêng cho từng mật khẩu, không cần thêm JAR.
    public static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(derive(password, salt, ITERATIONS));
    }
    public static boolean verify(String password, String stored) {
        try {
            String[] p = stored.split("\\$");
            if (p.length != 4 || !p[0].equals("pbkdf2")) return false;
            int iterations = Integer.parseInt(p[1]);
            if (iterations < 100000 || iterations > 2000000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(p[3]), derive(password, Base64.getDecoder().decode(p[2]), iterations));
        }
        catch (RuntimeException e) {
            return false;
        }
    }
    private static byte[] derive(String s, byte[] salt, int iterations) {
        PBEKeySpec key = new PBEKeySpec(s.toCharArray(), salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(key).getEncoded();
        }
        catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        finally {
            key.clearPassword();
        }
    }
    public static void validate(String s) {
        if (s == null || s.length() < 10 || s.length() > 128) throw new IllegalArgumentException("Mật khẩu cần từ 10 đến 128 ký tự.");
    }
}
