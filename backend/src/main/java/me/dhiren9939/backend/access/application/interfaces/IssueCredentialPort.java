package me.dhiren9939.backend.access.application.interfaces;

import me.dhiren9939.backend.access.domain.aggregates.apikey.IssuedCredential;

/** Generates a fresh credential in the key format currently in use. */
public interface IssueCredentialPort {
    IssuedCredential issue();
}
