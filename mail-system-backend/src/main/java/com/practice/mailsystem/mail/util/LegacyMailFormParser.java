package com.practice.mailsystem.mail.util;

import com.practice.mailsystem.mail.dto.LegacyMailFormRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LegacyMailFormParser {

    private static final Pattern INDEXED_FIELD_PATTERN = Pattern.compile("^(target|copy|attachmentIds)\\[(\\d+)]$");

    public LegacyMailFormRequest parse(MultiValueMap<String, String> formData, List<Long> uploadedAttachmentIds) {
        String title = firstValue(formData, "title");
        String content = firstValue(formData, "content");
        Long draftId = parseLong(firstValue(formData, "draftId"));

        List<String> targets = extractIndexedValues(formData, "target");
        List<String> copies = extractIndexedValues(formData, "copy");
        List<Long> attachmentIds = new ArrayList<>(extractIndexedLongValues(formData, "attachmentIds"));
        if (uploadedAttachmentIds != null && !uploadedAttachmentIds.isEmpty()) {
            attachmentIds.addAll(uploadedAttachmentIds);
        }

        return new LegacyMailFormRequest(draftId, title, content, targets, copies, attachmentIds);
    }

    private String firstValue(MultiValueMap<String, String> formData, String key) {
        List<String> values = formData.get(key);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private List<String> extractIndexedValues(MultiValueMap<String, String> formData, String fieldName) {
        return formData.entrySet().stream()
                .map(entry -> toIndexedValue(entry, fieldName))
                .filter(item -> item != null && item.value() != null && !item.value().isBlank())
                .sorted(Comparator.comparingInt(IndexedValue::index))
                .map(IndexedValue::value)
                .distinct()
                .toList();
    }

    private List<Long> extractIndexedLongValues(MultiValueMap<String, String> formData, String fieldName) {
        return formData.entrySet().stream()
                .map(entry -> toIndexedValue(entry, fieldName))
                .filter(item -> item != null && item.value() != null && !item.value().isBlank())
                .sorted(Comparator.comparingInt(IndexedValue::index))
                .map(item -> parseLong(item.value()))
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private IndexedValue toIndexedValue(Map.Entry<String, List<String>> entry, String fieldName) {
        Matcher matcher = INDEXED_FIELD_PATTERN.matcher(entry.getKey());
        if (!matcher.matches() || !fieldName.equals(matcher.group(1))) {
            return null;
        }
        List<String> values = entry.getValue();
        if (values == null || values.isEmpty()) {
            return null;
        }
        return new IndexedValue(Integer.parseInt(matcher.group(2)), values.get(0));
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record IndexedValue(int index, String value) {
    }
}
