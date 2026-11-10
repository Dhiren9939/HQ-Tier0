package me.dhiren9939.backend.access.application.port.out;

import me.dhiren9939.backend.access.domain.IssuedCredential;

/** Output port that generates a fresh credential in the key format currently in use. */
public interface IssueCredentialPort {
    IssuedCredential issue();
}
