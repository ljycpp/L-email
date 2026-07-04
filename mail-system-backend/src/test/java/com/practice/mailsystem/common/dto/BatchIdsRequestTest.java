package com.practice.mailsystem.common.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchIdsRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsEmptyIds() {
        BatchIdsRequest request = new BatchIdsRequest(List.of(), null);
        Set<ConstraintViolation<BatchIdsRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void rejectsTooManyIds() {
        BatchIdsRequest request = new BatchIdsRequest(
                java.util.stream.LongStream.rangeClosed(1, 101).boxed().toList(),
                null);
        Set<ConstraintViolation<BatchIdsRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
    }

    @Test
    void acceptsValidIds() {
        BatchIdsRequest request = new BatchIdsRequest(List.of(1L, 2L, 3L), "INBOX");
        Set<ConstraintViolation<BatchIdsRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty());
    }
}
