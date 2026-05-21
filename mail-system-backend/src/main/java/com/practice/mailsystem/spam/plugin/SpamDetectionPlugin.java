package com.practice.mailsystem.spam.plugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice.mailsystem.common.exception.BusinessException;
import com.practice.mailsystem.spam.client.SpamPredictResult;
import com.practice.mailsystem.spam.config.SpamDetectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class SpamDetectionPlugin {

    private static final Logger log = LoggerFactory.getLogger(SpamDetectionPlugin.class);
    private static final String LABEL_SPAM = "spam";
    private static final String LABEL_HAM = "ham";

    private final SpamDetectorProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private volatile SpamModel model;

    public SpamDetectionPlugin(SpamDetectorProperties properties,
                               ResourceLoader resourceLoader,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    public Optional<SpamPredictResult> predict(String subject, String body) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return Optional.of(doPredict(subject, body));
        } catch (Exception ex) {
            log.warn("垃圾邮件模型插件不可用: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public void testConnection() {
        if (!properties.isEnabled()) {
            throw new BusinessException(400, "垃圾邮件检测插件已关闭");
        }
        ensureModelLoaded();
    }

    private SpamPredictResult doPredict(String subject, String body) {
        SpamModel currentModel = ensureModelLoaded();
        List<String> tokens = SpamFeatureExtractor.extractTokens(subject, body);

        Map<String, Double> scores = new HashMap<>();
        for (String label : currentModel.classCounts().keySet()) {
            scores.put(label, logProbability(currentModel, label, tokens));
        }

        double maxScore = scores.values().stream().max(Double::compareTo).orElse(0D);
        Map<String, Double> expScores = new HashMap<>();
        double normalizer = 0D;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            double expValue = Math.exp(entry.getValue() - maxScore);
            expScores.put(entry.getKey(), expValue);
            normalizer += expValue;
        }

        Map<String, Double> probabilities = new HashMap<>();
        for (Map.Entry<String, Double> entry : expScores.entrySet()) {
            probabilities.put(entry.getKey(), normalizer == 0 ? 0D : entry.getValue() / normalizer);
        }

        String predictedLabel = probabilities.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(LABEL_HAM);
        double spamScore = probabilities.getOrDefault(LABEL_SPAM, 0D);
        double hamScore = probabilities.getOrDefault(LABEL_HAM, 0D);
        double confidence = Math.max(spamScore, hamScore);
        List<String> reasonTokens = topReasonTokens(currentModel, tokens, properties.getTopReasonCount());
        return new SpamPredictResult(predictedLabel, spamScore, hamScore, confidence, reasonTokens);
    }

    private double logProbability(SpamModel model, String label, List<String> tokens) {
        double totalDocs = (double) Math.max(model.totalDocuments(), 1L);
        double vocabSize = (double) Math.max(model.vocabulary().size(), 1);
        long classDocCount = model.classCounts().getOrDefault(label, 0L);
        double denominator = model.tokenTotals().getOrDefault(label, 0L) + model.alpha() * vocabSize;
        double logProb = Math.log(classDocCount / totalDocs);
        Map<String, Integer> classTokenCounter = model.tokenCounter(label);

        for (String token : tokens) {
            int tokenCount = classTokenCounter.getOrDefault(token, 0);
            logProb += Math.log((tokenCount + model.alpha()) / denominator);
        }
        return logProb;
    }

    private List<String> topReasonTokens(SpamModel model, List<String> tokens, int limit) {
        if (tokens.isEmpty()) {
            return List.of();
        }
        Set<String> uniqueTokens = new LinkedHashSet<>(tokens);
        double vocabSize = (double) Math.max(model.vocabulary().size(), 1);
        double spamDenominator = model.tokenTotals().getOrDefault(LABEL_SPAM, 0L) + model.alpha() * vocabSize;
        double hamDenominator = model.tokenTotals().getOrDefault(LABEL_HAM, 0L) + model.alpha() * vocabSize;
        Map<String, Integer> spamCounter = model.tokenCounter(LABEL_SPAM);
        Map<String, Integer> hamCounter = model.tokenCounter(LABEL_HAM);

        return uniqueTokens.stream()
                .map(token -> Map.entry(token, Math.log(
                        ((spamCounter.getOrDefault(token, 0) + model.alpha()) / spamDenominator) /
                                ((hamCounter.getOrDefault(token, 0) + model.alpha()) / hamDenominator)
                )))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(Math.max(limit, 1))
                .map(Map.Entry::getKey)
                .map(SpamFeatureExtractor::describeToken)
                .filter(text -> text != null && !text.isBlank())
                .toList();
    }

    private SpamModel ensureModelLoaded() {
        SpamModel current = model;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (model != null) {
                return model;
            }
            model = loadModel();
            return model;
        }
    }

    private SpamModel loadModel() {
        String modelPath = properties.getModelPath();
        try {
            Resource resource = resourceLoader.getResource(modelPath);
            if (!resource.exists()) {
                throw new IllegalStateException("垃圾邮件模型文件不存在: " + modelPath);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                Map<String, Object> raw = objectMapper.readValue(inputStream, new TypeReference<>() {
                });
                SpamModel loadedModel = new SpamModel(
                        readDouble(raw.get("alpha"), 1D),
                        readLongMap(raw.get("class_counts")),
                        readLongMap(raw.get("token_totals")),
                        readNestedIntMap(raw.get("token_counts")),
                        readStringSet(raw.get("vocabulary"))
                );
                if (loadedModel.classCounts().isEmpty() || loadedModel.vocabulary().isEmpty()) {
                    throw new IllegalStateException("垃圾邮件模型内容不完整: " + modelPath);
                }
                log.info("垃圾邮件模型已加载: {}，词汇量={}", modelPath, loadedModel.vocabulary().size());
                return loadedModel;
            }
        } catch (IOException ex) {
            throw new IllegalStateException("加载垃圾邮件模型失败: " + modelPath, ex);
        }
    }

    private double readDouble(Object value, double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private Map<String, Long> readLongMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object rawValue = entry.getValue();
            long number = rawValue instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(rawValue));
            result.put(key, number);
        }
        return result;
    }

    private Map<String, Map<String, Integer>> readNestedIntMap(Object value) {
        if (!(value instanceof Map<?, ?> outer)) {
            return Map.of();
        }
        Map<String, Map<String, Integer>> result = new HashMap<>();
        for (Map.Entry<?, ?> outerEntry : outer.entrySet()) {
            String label = String.valueOf(outerEntry.getKey());
            Object innerValue = outerEntry.getValue();
            if (!(innerValue instanceof Map<?, ?> innerMap)) {
                continue;
            }
            Map<String, Integer> inner = new HashMap<>();
            for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                String token = String.valueOf(innerEntry.getKey());
                Object rawNumber = innerEntry.getValue();
                int number = rawNumber instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(rawNumber));
                inner.put(token, number);
            }
            result.put(label, inner);
        }
        return result;
    }

    private Set<String> readStringSet(Object value) {
        if (!(value instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : list) {
            result.add(String.valueOf(item));
        }
        return result;
    }
}
