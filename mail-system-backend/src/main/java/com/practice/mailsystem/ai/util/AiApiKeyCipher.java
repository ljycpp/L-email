package com.practice.mailsystem.ai.util;

import com.practice.mailsystem.ai.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class AiApiKeyCipher {

    private static final Logger log = LoggerFactory.getLogger(AiApiKeyCipher.class);

    private static final String ALGORITHM = "AES";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String LEGACY_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String GCM_PREFIX = "GCM:";
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec secretKeySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public AiApiKeyCipher(AiProperties aiProperties) {
        byte[] keyBytes = aiProperties.secretKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("app.ai.secret-key must be 16, 24, or 32 bytes long");
        }
        this.secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + cipherText.length)
                    .put(iv)
                    .put(cipherText)
                    .array();
            return GCM_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt API key", ex);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        if (encryptedText.startsWith(GCM_PREFIX)) {
            return decryptGcm(encryptedText.substring(GCM_PREFIX.length()));
        }
        return decryptLegacyEcb(encryptedText);
    }

    private String decryptGcm(String base64Payload) {
        try {
            byte[] payload = Base64.getDecoder().decode(base64Payload);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt API key", ex);
        }
    }

    /**
     * 兼容历史 ECB 密文，用户下次保存配置时会自动迁移为 GCM。
     * ECB 模式存在安全隐患（相同明文产生相同密文），此路径仅用于向后兼容，
     * 建议用户尽快在 AI 设置页重新保存配置以完成迁移。
     */
    private String decryptLegacyEcb(String encryptedText) {
        log.warn("Decrypting AI API key with legacy ECB mode. Please re-save your AI config to migrate to GCM encryption.");
        try {
            Cipher cipher = Cipher.getInstance(LEGACY_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to decrypt API key", ex);
        }
    }
}
