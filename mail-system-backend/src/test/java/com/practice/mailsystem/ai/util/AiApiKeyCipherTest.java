package com.practice.mailsystem.ai.util;

import com.practice.mailsystem.ai.config.AiProperties;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiApiKeyCipherTest {

    private static final String TEST_KEY = "01234567890123456789012345678901";

    @Test
    void encryptUsesGcmAndRoundTrips() {
        AiApiKeyCipher cipher = new AiApiKeyCipher(testProperties());
        String encrypted = cipher.encrypt("sk-live-abc123");
        assertTrue(encrypted.startsWith("GCM:"));
        assertEquals("sk-live-abc123", cipher.decrypt(encrypted));
    }

    @Test
    void decryptSupportsLegacyEcbPayload() throws Exception {
        AiApiKeyCipher cipher = new AiApiKeyCipher(testProperties());
        String legacy = encryptLegacyEcb("legacy-key");
        assertEquals("legacy-key", cipher.decrypt(legacy));
    }

    private AiProperties testProperties() {
        return new AiProperties("https://example.com", 1, 1, 1000, TEST_KEY);
    }

    private String encryptLegacyEcb(String plainText) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(TEST_KEY.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }
}
