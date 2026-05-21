package com.practice.mailsystem.spam.plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record SpamModel(
        double alpha,
        Map<String, Long> classCounts,
        Map<String, Long> tokenTotals,
        Map<String, Map<String, Integer>> tokenCounts,
        Set<String> vocabulary
) {
    public SpamModel {
        classCounts = classCounts == null ? Map.of() : Map.copyOf(classCounts);
        tokenTotals = tokenTotals == null ? Map.of() : Map.copyOf(tokenTotals);
        tokenCounts = tokenCounts == null ? Map.of() : deepImmutable(tokenCounts);
        vocabulary = vocabulary == null ? Set.of() : Set.copyOf(vocabulary);
    }

    public long totalDocuments() {
        return classCounts.values().stream().mapToLong(Long::longValue).sum();
    }

    public Map<String, Integer> tokenCounter(String label) {
        return tokenCounts.getOrDefault(label, Map.of());
    }

    private static Map<String, Map<String, Integer>> deepImmutable(Map<String, Map<String, Integer>> source) {
        return source.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> Collections.unmodifiableMap(entry.getValue())
        ));
    }
}
