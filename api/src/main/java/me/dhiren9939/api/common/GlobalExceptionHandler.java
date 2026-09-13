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

/** Ensures every response leaving this API is the same ApiResponse/ApiError JSON shape - including errors Spring throws before a controller method runs. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Object>> handleNotFound(NoSuchElementException ex) {
        return ApiResponse.fail(
                        ApiError.of(404, "NOT_FOUND", ex.getMessage()))
                .toResponseEntity();
    }

    // @Valid/@Validated failures on a request body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList()))
                );

        return ApiResponse.fail(
                        ApiError.ofFields(400, "VALIDATION_FAILED", "Request validation failed.", fields))
                .toResponseEntity();
    }

    // @Valid failure on a @PathVariable or @RequestParam
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
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

        return ApiResponse.fail(
                        ApiError.ofFields(400, "VALIDATION_FAILED", "Request validation failed.", fields))
                .toResponseEntity();
    }

    // Malformed JSON body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ApiResponse.fail(
                        ApiError.of(400, "MALFORMED_REQUEST", "Request body is missing or malformed."))
                .toResponseEntity();
    }

    // @PathVariable/@RequestParam type mismatch
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        return ApiResponse.fail(
                        ApiError.of(400, "TYPE_MISMATCH", message))
                .toResponseEntity();
    }

    // Wrong HTTP method for the route
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ApiResponse.fail(
                        ApiError.of(405, "METHOD_NOT_ALLOWED",
                                ex.getMethod() + " is not supported for this endpoint."))
                .toResponseEntity();
    }

    // Unsupported request Content-Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ApiResponse.fail(
                        ApiError.of(415, "UNSUPPORTED_MEDIA_TYPE", ex.getContentType() + " is not supported."))
                .toResponseEntity();
    }

    // Route not found
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return ApiResponse.fail(
                        ApiError.of(404, "NOT_FOUND", "Route not found."))
                .toResponseEntity();
    }

    // Missing required @RequestParam
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ApiResponse.fail(
                        ApiError.of(400, "MISSING_PARAMETER",
                                "Required parameter '" + ex.getParameterName() + "' is missing."))
                .toResponseEntity();
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Object>> handleRuntime(RuntimeException ex) {
        log.error("Runtime exception", ex);
        return ApiResponse.fail(
                        ApiError.of(500, "INTERNAL_SERVER_ERROR", "Something went wrong."))
                .toResponseEntity();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ApiResponse.fail(
                        ApiError.of(500, "INTERNAL_SERVER_ERROR", "Something went wrong."))
                .toResponseEntity();
    }
}
