package me.dhiren9939.backend.access.presentation.api.errors;

import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
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
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The last line of defence for every endpoint: validation failures, malformed requests, wrong methods, wrong media
 * types, unknown routes and anything unexpected all become the same problem body as the domain errors. Every cause
 * has its own {@code code}, so a client can tell exactly what went wrong without reading the message. Domain
 * handlers (which run first) own their own exceptions.
 *
 * <p>Validation, body and parameter errors never echo the offending value, and unexpected errors never reveal
 * their message or stack trace, so nothing internal is leaked.</p>
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class WebExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * {@code @Valid} body failed. Lists every field that is wrong as {@code {field, code, message}}; the code is
     * the key of the constraint's message in {@code ValidationMessages.properties}, such as {@code NAME_TOO_LONG}.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(WebExceptionHandler::describe)
                .sorted(Comparator.comparing((Map<String, String> e) -> e.get("field")).thenComparing(e -> e.get("code")))
                .toList();
        ProblemDetail problem = ApiProblems.of(status, "VALIDATION_FAILED", "The request has invalid fields.");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Invalid JSON, a missing body, a wrong type or an unknown enum value, each with its own code. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        Throwable cause = ex.getCause();
        ProblemDetail problem;
        if (cause == null) {
            problem = ApiProblems.of(status, "REQUEST_BODY_MISSING", "The request body is required.");
        } else if (cause instanceof StreamReadException) {
            problem = ApiProblems.of(status, "REQUEST_BODY_NOT_JSON", "The request body is not valid JSON.");
        } else if (cause instanceof MismatchedInputException mismatch) {
            problem = invalidValue(status, mismatch);
        } else {
            problem = ApiProblems.of(status, "REQUEST_BODY_INVALID", "The request body could not be read.");
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** A path variable or parameter that cannot be converted: {@code INVALID_<PARAMETER>}, such as {@code INVALID_USER_ID}. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName() : ex.getPropertyName();
        ProblemDetail problem = ApiProblems.of(status, name == null ? "INVALID_PARAMETER" : "INVALID_" + upperSnake(name),
                "The value of '" + (name == null ? "the request" : name) + "' is not valid.");
        if (name != null)
            problem.setProperty("parameter", name);
        return handleExceptionInternal(ex, problem, headers, status, request);
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

    /**
     * An unknown or mistyped value. An enum value that is not one of the constants is named after the enum
     * ({@code INVALID_SCOPE}, {@code INVALID_ACCESS_LEVEL}, {@code INVALID_LIFETIME}, {@code INVALID_API_KEY_CHANGE_FAILURE_REASON}),
     * which also covers map keys and values nested in the body; anything else is named after its property
     * ({@code INVALID_NAME}). The {@code field} property says which top-level property of the body it is in.
     */
    private static ProblemDetail invalidValue(HttpStatusCode status, MismatchedInputException mismatch) {
        List<tools.jackson.core.JacksonException.Reference> path = mismatch.getPath();
        if (path.isEmpty() || path.getFirst().getPropertyName() == null)
            return ApiProblems.of(status, "REQUEST_BODY_WRONG_TYPE", "The request body has the wrong shape.");

        String property = path.getFirst().getPropertyName();
        String code = mismatch instanceof InvalidFormatException invalid
                && invalid.getTargetType() != null && invalid.getTargetType().isEnum()
                ? "INVALID_" + upperSnake(invalid.getTargetType().getSimpleName())
                : "INVALID_" + upperSnake(property);
        ProblemDetail problem = ApiProblems.of(status, code, path.size() == 1
                ? "The value of '" + property + "' is not valid."
                : "'" + property + "' contains an unknown or mistyped value.");
        problem.setProperty("field", property);
        return problem;
    }

    private static Map<String, String> describe(ObjectError error) {
        String field = error instanceof FieldError fieldError ? fieldError.getField() : "";
        String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
        return Map.of("field", field, "code", codeOf(error), "message", message);
    }

    /** The key of the constraint's message template: {@code "{NAME_TOO_LONG}"} becomes {@code NAME_TOO_LONG}. */
    private static String codeOf(ObjectError error) {
        if (error.contains(ConstraintViolation.class)) {
            String template = error.unwrap(ConstraintViolation.class).getMessageTemplate();
            if (template.length() > 2 && template.startsWith("{") && template.endsWith("}"))
                return template.substring(1, template.length() - 1);
        }
        return "FIELD_INVALID";
    }

    private static String upperSnake(String camelCase) {
        return camelCase.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
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
