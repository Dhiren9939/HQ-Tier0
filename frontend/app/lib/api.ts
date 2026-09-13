// Google is the only sign-in method for now. Spring Security's default OAuth2
// login flow starts at /oauth2/authorization/{registrationId} — no
// AuthController exists yet, so this 404s until the backend registers a
// "google" OAuth2 client. Kept as a constant (not a fetch call) because the
// browser needs a full-page redirect here, not an XHR.
export const GOOGLE_LOGIN_URL = "/oauth2/authorization/google";
