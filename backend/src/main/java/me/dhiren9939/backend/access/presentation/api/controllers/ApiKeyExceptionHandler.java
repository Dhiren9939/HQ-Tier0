package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.shared.access.ApiKeyVerificationFailure;
import me.dhiren9939.backend.shared.access.InvalidPermissionsException;
import me.dhiren9939.backend.shared.access.VerificationFailureCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Access failures to HTTP problem responses instead of letting them surface as 500s. */
@RestControllerAdvice(basePackageClasses = ApiKeyExceptionHandler.class)
class ApiKeyExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ProblemDetail notFound(ApiKeyNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ApiKeyUpdateFailure.class)
    ProblemDetail conflict(ApiKeyUpdateFailure exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ApiKeyVerificationFailure.class)
    ProblemDetail verificationFailed(ApiKeyVerificationFailure exception) {
        HttpStatus status = exception.code() == VerificationFailureCode.MALFORMED_CREDENTIALS
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.UNAUTHORIZED;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", exception.code().name());
        return problem;
    }

    @ExceptionHandler(InvalidPermissionsException.class)
    ProblemDetail invalidPermissions(InvalidPermissionsException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
