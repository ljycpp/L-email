package com.practice.mailsystem.ai.util;

public final class AiKeyMaskUtil {

    private AiKeyMaskUtil() {
    }

    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "";
        }
        if (apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 3) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
