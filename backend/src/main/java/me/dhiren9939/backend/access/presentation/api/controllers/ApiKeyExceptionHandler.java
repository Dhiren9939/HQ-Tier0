package me.dhiren9939.backend.access.presentation.api.controllers;

import me.dhiren9939.backend.access.application.usecase.service.verify.ApiKeyVerificationFailure;
import me.dhiren9939.backend.common.access.VerificationFailureCode;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyNotFoundException;
import me.dhiren9939.backend.access.domain.exceptions.ApiKeyUpdateFailure;
import me.dhiren9939.backend.access.domain.exceptions.InvalidPermissionsException;
import me.dhiren9939.backend.web.errors.ApiProblems;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps Access failures to HTTP problem responses instead of letting them surface as 500s. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = ApiKeyExceptionHandler.class)
class ApiKeyExceptionHandler {

    @ExceptionHandler(ApiKeyNotFoundException.class)
    ProblemDetail notFound(ApiKeyNotFoundException exception) {
        return ApiProblems.of(HttpStatus.NOT_FOUND, "API_KEY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(ApiKeyUpdateFailure.class)
    ProblemDetail conflict(ApiKeyUpdateFailure exception) {
        return ApiProblems.of(HttpStatus.CONFLICT, exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(ApiKeyVerificationFailure.class)
    ProblemDetail verificationFailed(ApiKeyVerificationFailure exception) {
        HttpStatus status = exception.code() == VerificationFailureCode.MALFORMED_CREDENTIALS
                ? HttpStatus.BAD_REQUEST
                : HttpStatus.UNAUTHORIZED;
        return ApiProblems.of(status, exception.code().name(), exception.getMessage());
    }

    @ExceptionHandler(InvalidPermissionsException.class)
    ProblemDetail invalidPermissions(InvalidPermissionsException exception) {
        return ApiProblems.of(HttpStatus.BAD_REQUEST, "INVALID_SCOPE_LEVEL", exception.getMessage());
    }
}
