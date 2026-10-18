package me.dhiren9939.api.apikey.internal;

/**
 * Carries a newly generated access identifier, one-time secret, and secret hash.
 */
public record ApiKeyTriplet(String accessId, String accessSecret, String secretHash){
}
