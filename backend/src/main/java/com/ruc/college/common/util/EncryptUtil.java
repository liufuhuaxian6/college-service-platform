package com.ruc.college.common.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class EncryptUtil {

    private static final String LEGACY_ALGORITHM = "AES";
    private static final String GCM_ALGORITHM = "AES/GCM/NoPadding";
    private static final String ACTIVE_KEY_ENV = "FIELD_CRYPTO_KEY_BASE64";
    private static final String V2_PREFIX = "v2:";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final byte[] LEGACY_KEY =
            "CollegeServicePlatform2026Secret!".getBytes(StandardCharsets.UTF_8);

    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) return plainText;
        byte[] activeKey = activeKeyOrNull();
        if (activeKey == null) {
            return encryptLegacy(plainText);
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(activeKey, LEGACY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return V2_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        if (cipherText.startsWith(V2_PREFIX)) {
            byte[] activeKey = activeKeyOrNull();
            if (activeKey == null) {
                throw new RuntimeException("FIELD_CRYPTO_KEY_BASE64 is required for v2 encrypted data");
            }
            return decryptGcm(cipherText.substring(V2_PREFIX.length()), activeKey);
        }
        return decryptLegacy(cipherText);
    }

    public static String desensitize(String text, int prefixLen, int suffixLen) {
        if (text == null || text.length() <= prefixLen + suffixLen) return text;
        String prefix = text.substring(0, prefixLen);
        String suffix = text.substring(text.length() - suffixLen);
        return prefix + "*".repeat(text.length() - prefixLen - suffixLen) + suffix;
    }

    private static String encryptLegacy(String plainText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(normalizeLegacyKey(), LEGACY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(LEGACY_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private static String decryptLegacy(String cipherText) {
        try {
            SecretKeySpec keySpec = new SecretKeySpec(normalizeLegacyKey(), LEGACY_ALGORITHM);
            Cipher cipher = Cipher.getInstance(LEGACY_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private static String decryptGcm(String encodedPayload, byte[] activeKey) {
        try {
            byte[] payload = Base64.getDecoder().decode(encodedPayload);
            if (payload.length <= GCM_IV_BYTES) {
                throw new IllegalArgumentException("Invalid encrypted payload");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(GCM_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(activeKey, LEGACY_ALGORITHM),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private static byte[] activeKeyOrNull() {
        String configured = System.getenv(ACTIVE_KEY_ENV);
        if (configured == null || configured.isBlank()) {
            configured = System.getProperty(ACTIVE_KEY_ENV);
        }
        if (configured == null || configured.isBlank()) {
            return null;
        }
        byte[] key = Base64.getDecoder().decode(configured.trim());
        if (key.length != 16 && key.length != 24 && key.length != 32) {
            throw new IllegalStateException("FIELD_CRYPTO_KEY_BASE64 must decode to 16, 24, or 32 bytes");
        }
        return key;
    }

    private static byte[] normalizeLegacyKey() {
        return Arrays.copyOf(LEGACY_KEY, 32);
    }
}
