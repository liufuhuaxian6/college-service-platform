package com.ruc.college.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256 加密工具，用于敏感字段加密存储（身份证号、生源地等）
 */
public class EncryptUtil {

    private static final String ALGORITHM = "AES";
    // 生产环境应从配置中心或环境变量读取
    private static final String KEY = "CollegeServicePlatform2026Secret!"; // 32 bytes = AES-256

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 脱敏显示：如身份证 110***********1234
     */
    public static String desensitize(String text, int prefixLen, int suffixLen) {
        if (text == null || text.length() <= prefixLen + suffixLen) return text;
        String prefix = text.substring(0, prefixLen);
        String suffix = text.substring(text.length() - suffixLen);
        return prefix + "*".repeat(text.length() - prefixLen - suffixLen) + suffix;
    }
}
