package me.dhiren9939.backend.web.errors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The last line of defence for every endpoint: validation failures, malformed requests, wrong methods, wrong media
 * types, unknown routes and anything unexpected all become the same problem body as the domain errors, with a
 * stable {@code code}. Domain handlers (which run first) own their own exceptions.
 *
 * <p>Validation, body and parameter errors never echo the offending value, and unexpected errors never reveal
 * their message or stack trace, so nothing internal is leaked.</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class WebExceptionHandler extends ResponseEntityExceptionHandler {

    /** {@code @Valid} body failed: lists every field that is wrong, so a client can fix them in one go. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(WebExceptionHandler::describe)
                .sorted(Comparator.comparing((Map<String, String> e) -> e.get("field")).thenComparing(e -> e.get("message")))
                .toList();
        ProblemDetail problem = ApiProblems.of(status, "VALIDATION_FAILED", "The request has invalid fields.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Invalid JSON, a missing body, a wrong type or an unknown enum value. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, ApiProblems.of(status, "MALFORMED_REQUEST",
                "The request body is missing, is not valid JSON, or contains an unknown or mistyped value."),
                headers, status, request);
    }

    /** A path variable or parameter that cannot be converted, such as an id that is not a UUID. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        return handleExceptionInternal(ex, ApiProblems.of(status, "INVALID_PARAMETER",
                "The value of '" + (name == null ? "the request" : name) + "' is not valid."),
                headers, status, request);
    }

    /** Every other framework exception keeps Spring's status and headers (such as {@code Allow}) and gains a code. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> handled = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        HttpHeaders responseHeaders = handled == null ? headers : handled.getHeaders();
        ProblemDetail problem = handled != null && handled.getBody() instanceof ProblemDetail existing
                ? existing
                : ProblemDetail.forStatusAndDetail(statusCode, "The request could not be processed.");
        if (problem.getProperties() == null || !problem.getProperties().containsKey("code"))
            problem.setProperty("code", codeFor(ex));
        return ResponseEntity.status(statusCode).headers(responseHeaders).body(problem);
    }

    /** Anything nobody planned for: logged in full here, never described to the caller. */
    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled exception while processing a request", ex);
        return ApiProblems.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again later.");
    }

    private static Map<String, String> describe(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : "";
        String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        return Map.of("field", field, "message", message);
    }

    private static String codeFor(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException)
            return "VALIDATION_FAILED";
        if (ex instanceof HttpMessageNotReadableException || ex instanceof ServletRequestBindingException)
            return "MALFORMED_REQUEST";
        if (ex instanceof TypeMismatchException)
            return "INVALID_PARAMETER";
        if (ex instanceof HttpRequestMethodNotSupportedException)
            return "METHOD_NOT_ALLOWED";
        if (ex instanceof HttpMediaTypeNotSupportedException)
            return "UNSUPPORTED_MEDIA_TYPE";
        if (ex instanceof HttpMediaTypeNotAcceptableException)
            return "NOT_ACCEPTABLE";
        if (ex instanceof NoResourceFoundException || ex instanceof NoHandlerFoundException)
            return "ROUTE_NOT_FOUND";
        return "REQUEST_FAILED";
    }
}
