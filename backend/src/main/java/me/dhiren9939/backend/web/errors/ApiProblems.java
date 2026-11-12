package me.dhiren9939.backend.web.errors;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds the error body every endpoint returns: an RFC 9457 problem with a human-readable {@code detail} that is
 * safe to show to end users, and a machine-readable {@code code} that clients branch on.
 */
public final class ApiProblems {

    private ApiProblems() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
