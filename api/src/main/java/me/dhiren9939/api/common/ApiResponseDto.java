package me.dhiren9939.api.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper used for all endpoints.")
public record ApiResponseDto<T>(

        @Schema(description = "Indicates if the request was successful.", example = "true")
        boolean success,

        @Schema(description = "An optional success message.", example = "Operation completed successfully.")
        String message,

        @Schema(description = "The actual payload of the response. The structure depends on the specific endpoint.")
        T data,

        @Schema(description = "Contains error details if 'success' is false.")
        ApiErrorDto<?> error
) {
    public static <T> ApiResponseDto<T> of(T data) {
        return new ApiResponseDto<>(true, null, data, null);
    }

    public static <T> ApiResponseDto<T> of(String message, T data) {
        return new ApiResponseDto<>(true, message, data, null);
    }

    public static <T> ApiResponseDto<T> fail(ApiErrorDto<?> error) {
        return new ApiResponseDto<>(false, null, null, error);
    }

    public ResponseEntity<ApiResponseDto<T>> toResponseEntity() {
        if (error != null)
            return ResponseEntity.status(error.status()).body(this);
        return ResponseEntity.status(200).body(this);
    }

    public ResponseEntity<ApiResponseDto<T>> toResponseEntity(int statusCode) {
        return ResponseEntity.status(statusCode).body(this);
    }
}
