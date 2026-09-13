package me.dhiren9939.api.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writes an {@link ApiResponse} straight to the servlet response for code that runs outside
 * Spring MVC's message-conversion pipeline - security filters and entry points/handlers,
 * which reject a request before any @Controller is reached and would otherwise fall back to
 * an empty body or a container error page.
 */
public final class ApiResponseWriter {

    private ApiResponseWriter() {
    }

    public static void writeError(HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code, String message)
            throws IOException {
        ApiResponse<?> body = ApiResponse.fail(ApiError.of(status.value(), code, message));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        byte[] bytes = objectMapper.writeValueAsBytes(body);
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();
    }
}
