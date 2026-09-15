package me.dhiren9939.api.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Ensures every response leaving this API is the same ApiResponseDto/ApiErrorDto JSON shape - including errors Spring throws before a controller method runs. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleNotFound(NoSuchElementException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(404, "NOT_FOUND", ex.getMessage()))
                .toResponseEntity();
    }

    // @Valid/@Validated failures on a request body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList()))
                );

        return ApiResponseDto.fail(
                        ApiErrorDto.ofFields(400, "VALIDATION_FAILED", "Request validation failed.", fields))
                .toResponseEntity();
    }

    // @Valid failure on a @PathVariable or @RequestParam
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> fields = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.groupingBy(
                        v -> {
                            String pathName = v.getPropertyPath().toString();
                            if (pathName.lastIndexOf('.') == -1)
                                return pathName;
                            return pathName.substring(pathName.lastIndexOf('.') + 1);
                        },
                        Collectors.mapping(
                                ConstraintViolation::getMessage,
                                Collectors.toList()
                        )
                ));

        return ApiResponseDto.fail(
                        ApiErrorDto.ofFields(400, "VALIDATION_FAILED", "Request validation failed.", fields))
                .toResponseEntity();
    }

    // Malformed JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(400, "MALFORMED_REQUEST", "Request body is missing or malformed."))
                .toResponseEntity();
    }

    // @PathVariable/@RequestParam type mismatch
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        return ApiResponseDto.fail(
                        ApiErrorDto.of(400, "TYPE_MISMATCH", message))
                .toResponseEntity();
    }

    // Wrong HTTP method for the route
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(405, "METHOD_NOT_ALLOWED",
                                ex.getMethod() + " is not supported for this endpoint."))
                .toResponseEntity();
    }

    // Unsupported request Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(415, "UNSUPPORTED_MEDIA_TYPE", ex.getContentType() + " is not supported."))
                .toResponseEntity();
    }

    // Route not found
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleNoResource(NoResourceFoundException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(404, "NOT_FOUND", "Route not found."))
                .toResponseEntity();
    }

    // Missing required @RequestParam
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponseDto.fail(
                        ApiErrorDto.of(400, "MISSING_PARAMETER",
                                "Required parameter '" + ex.getParameterName() + "' is missing."))
                .toResponseEntity();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseDto<Object>> handleRuntime(RuntimeException ex) {
        log.error("Runtime exception", ex);
        return ApiResponseDto.fail(
                        ApiErrorDto.of(500, "INTERNAL_SERVER_ERROR", "Something went wrong."))
                .toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponseDto.fail(
                        ApiErrorDto.of(500, "INTERNAL_SERVER_ERROR", "Something went wrong."))
                .toResponseEntity();
    }
}
