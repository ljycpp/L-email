package com.practice.mailsystem.common.exception;

import com.practice.mailsystem.common.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(resolveStatus(ex.getCode()))
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseBody
    public ApiResponse<Void> handleDuplicateKey(DuplicateKeyException ex) {
        return ApiResponse.fail(409, "数据已存在，请勿重复提交");
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class, ConstraintViolationException.class})
    @ResponseBody
    public ApiResponse<Void> handleValidation(Exception ex) {
        return ApiResponse.fail(400, resolveValidationMessage(ex));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ApiResponse<Void> handleUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ApiResponse.fail(400, "Attachment is too large. The maximum single file size is 20MB.");
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ApiResponse<Void> handleOther(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.fail(500, "Server error.");
    }

    private HttpStatus resolveStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 500 -> HttpStatus.INTERNAL_SERVER_ERROR;
            case 502 -> HttpStatus.BAD_GATEWAY;
            case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            case 504 -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private String resolveValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException validException) {
            return firstBindingError(validException.getBindingResult());
        }
        if (ex instanceof BindException bindException) {
            return firstBindingError(bindException.getBindingResult());
        }
        if (ex instanceof ConstraintViolationException violationException
                && !violationException.getConstraintViolations().isEmpty()) {
            ConstraintViolation<?> violation = violationException.getConstraintViolations().iterator().next();
            return formatFieldMessage(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }
        return "请求参数不正确";
    }

    private String firstBindingError(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError != null) {
            return formatFieldMessage(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ObjectError globalError = bindingResult.getGlobalError();
        if (globalError != null && globalError.getDefaultMessage() != null) {
            return globalError.getDefaultMessage();
        }
        return "请求参数不正确";
    }

    private String formatFieldMessage(String fieldPath, String message) {
        String fieldName = readableFieldName(fieldPath);
        String detail = normalizeValidationDetail(message);
        if (fieldName.isBlank()) {
            return detail;
        }
        if (detail.startsWith(fieldName)) {
            return detail;
        }
        return fieldName + detail;
    }

    private String normalizeValidationDetail(String message) {
        if (message == null || message.isBlank()) {
            return "格式不正确";
        }
        String lower = message.toLowerCase();
        if (lower.contains("must not be blank") || lower.contains("must not be null")
                || lower.contains("不能为空")) {
            return "不能为空";
        }
        if (lower.contains("well-formed email") || lower.contains("email") || lower.contains("格式")) {
            return "格式不正确";
        }
        if (lower.contains("size must be") || lower.contains("length") || lower.contains("长度")) {
            return "长度不正确";
        }
        return message;
    }

    private String readableFieldName(String fieldPath) {
        if (fieldPath == null || fieldPath.isBlank()) {
            return "";
        }
        String normalized = fieldPath.replaceAll("\\[\\d+\\]", "").toLowerCase();
        if (normalized.contains("target") && normalized.contains("mail")) {
            return "收件人邮箱";
        }
        if (normalized.contains("copy") && normalized.contains("mail")) {
            return "抄送邮箱";
        }
        String lastSegment = normalized.substring(normalized.lastIndexOf('.') + 1);
        return switch (lastSegment) {
            case "mail", "email" -> "邮箱";
            case "name", "nickname", "username" -> "名称";
            case "title", "subject" -> "主题";
            case "content" -> "正文";
            case "password" -> "密码";
            case "ids", "mailids" -> "邮件";
            case "color" -> "颜色";
            default -> "";
        };
    }
}
