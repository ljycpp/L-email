package com.practice.mailsystem.spam.plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SpamFeatureExtractor {

    private static final Pattern WORD_RE = Pattern.compile("[a-z0-9][a-z0-9@._%+-]{1,39}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHRASE_RE = Pattern.compile("[\\u4e00-\\u9fff]{2,20}");
    private static final Pattern URL_RE = Pattern.compile(
            "(https?://\\S+|www\\.\\S+|\\b[a-z0-9.-]+\\.(?:com|cn|net|org|top|vip|xyz|cc|info|shop|link)\\b)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PHONE_RE = Pattern.compile("(?:\\+?86[- ]?)?1[3-9]\\d{9}");
    private static final Pattern CURRENCY_RE = Pattern.compile("(?:¥|￥|\\$|\\d+\\s*(?:元|块|万元|usd|rmb))", Pattern.CASE_INSENSITIVE);
    private static final Pattern CODE_RE = Pattern.compile("(验证码|校验码|动态码|\\bcode\\b)", Pattern.CASE_INSENSITIVE);

    private static final String[] SPAM_HINTS = {
            "中奖", "红包", "返利", "兼职", "刷单", "领取", "提现", "投资", "外汇", "盈利",
            "稳赚", "低息", "贷款", "办证", "提额", "回收", "定金", "补贴", "清零", "点击",
            "链接", "抵金", "返现", "代购", "招募", "理财", "跟单", "日赚", "优惠", "促销",
            "秒杀", "退税", "理赔", "空投", "offer", "winner", "claim", "bonus", "urgent", "free"
    };

    private static final String[] SAFE_HINTS = {
            "如非本人请忽略", "联系管理员", "共享目录", "会议纪要", "报销", "审批", "评审", "培训", "周会"
    };

    private static final String[] URGENT_HINTS = {
            "立即", "马上", "立刻", "速抢", "限时"
    };

    private SpamFeatureExtractor() {
    }

    static List<String> extractTokens(String subject, String body) {
        String subjectText = normalizeText(subject);
        String bodyText = normalizeText(body);
        String combined = (subjectText + " " + bodyText).trim();

        List<String> tokens = new ArrayList<>();
        List<String> subjectTokens = new ArrayList<>();
        subjectTokens.addAll(wordTokens(subjectText, "subject"));
        subjectTokens.addAll(phraseTokens(subjectText, "subject"));
        subjectTokens.addAll(charNgrams(subjectText, "subject"));

        List<String> bodyTokens = new ArrayList<>();
        bodyTokens.addAll(wordTokens(bodyText, "body"));
        bodyTokens.addAll(phraseTokens(bodyText, "body"));
        bodyTokens.addAll(charNgrams(bodyText, "body"));

        tokens.addAll(subjectTokens);
        tokens.addAll(subjectTokens);
        tokens.addAll(bodyTokens);
        tokens.addAll(metaTokens(combined));

        if (tokens.isEmpty()) {
            tokens.add("meta:empty");
        }
        return tokens;
    }

    static String describeToken(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }
        if (token.startsWith("meta:")) {
            return switch (token) {
                case "meta:has_url" -> "包含链接";
                case "meta:has_phone" -> "包含手机号";
                case "meta:has_money" -> "包含金额信息";
                case "meta:has_code" -> "包含验证码/代码";
                case "meta:many_digits" -> "数字较多";
                case "meta:contains_link_text" -> "包含网址文本";
                case "meta:urgent_call" -> "含紧迫诱导用语";
                case "meta:empty" -> "正文为空";
                default -> token.substring("meta:".length());
            };
        }
        if (token.startsWith("hint:")) {
            return "命中可疑关键词：" + token.substring("hint:".length());
        }
        if (token.startsWith("safe:")) {
            return "命中正常办公词：" + token.substring("safe:".length());
        }
        int index = token.lastIndexOf(':');
        if (index >= 0 && index < token.length() - 1) {
            return "命中特征词：" + token.substring(index + 1);
        }
        return token;
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean isCjk(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fff';
    }

    private static String cleanForNgrams(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || isCjk(ch)) {
                builder.append(ch);
            }
        }
        String compact = builder.toString();
        return compact.length() > 400 ? compact.substring(0, 400) : compact;
    }

    private static List<String> wordTokens(String text, String prefix) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_RE.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1) {
                tokens.add(prefix + ":w:" + token);
            }
        }
        return tokens;
    }

    private static List<String> phraseTokens(String text, String prefix) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = PHRASE_RE.matcher(text);
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() > 1) {
                tokens.add(prefix + ":p:" + token);
            }
        }
        return tokens;
    }

    private static List<String> charNgrams(String text, String prefix) {
        List<String> tokens = new ArrayList<>();
        String compact = cleanForNgrams(text);
        for (int size : new int[]{2, 3}) {
            if (compact.length() < size) {
                continue;
            }
            for (int i = 0; i <= compact.length() - size; i++) {
                String gram = compact.substring(i, i + size);
                if (gram.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                tokens.add(prefix + ":g:" + gram);
            }
        }
        return tokens;
    }

    private static List<String> metaTokens(String fullText) {
        List<String> tokens = new ArrayList<>();
        int urlHits = countMatches(URL_RE, fullText);
        int phoneHits = countMatches(PHONE_RE, fullText);
        int moneyHits = countMatches(CURRENCY_RE, fullText);
        int codeHits = countMatches(CODE_RE, fullText);
        int digitCount = (int) fullText.chars().filter(Character::isDigit).count();

        repeat(tokens, "meta:has_url", Math.min(urlHits, 3));
        repeat(tokens, "meta:has_phone", Math.min(phoneHits, 2));
        repeat(tokens, "meta:has_money", Math.min(moneyHits, 3));
        repeat(tokens, "meta:has_code", Math.min(codeHits, 2));

        if (digitCount >= 4) {
            tokens.add("meta:many_digits");
        }
        if (fullText.contains("http") || fullText.contains("www.")) {
            tokens.add("meta:contains_link_text");
        }
        if (containsAny(fullText, URGENT_HINTS)) {
            tokens.add("meta:urgent_call");
        }

        addHintTokens(tokens, fullText, SPAM_HINTS, "hint:");
        addHintTokens(tokens, fullText, SAFE_HINTS, "safe:");
        return tokens;
    }

    private static int countMatches(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static boolean containsAny(String text, String[] words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private static void addHintTokens(List<String> tokens, String text, String[] hints, String prefix) {
        for (String hint : hints) {
            int count = countOccurrences(text, hint);
            repeat(tokens, prefix + hint, Math.min(count, 2));
        }
    }

    private static int countOccurrences(String text, String hint) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(hint, index)) >= 0) {
            count++;
            index += hint.length();
        }
        return count;
    }

    private static void repeat(List<String> target, String token, int times) {
        for (int i = 0; i < times; i++) {
            target.add(token);
        }
    }
}
