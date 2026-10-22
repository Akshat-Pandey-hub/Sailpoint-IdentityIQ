package com.keyforge.iiq.rest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The username/password a REST caller must present with HTTP Basic auth: the configured client ID / client secret
 * ({@code KFAGENT_USERNAME} / {@code KFAGENT_PASSWORD}), separate from the IdentityIQ login. Held in memory only as
 * the username and a SHA-256 digest of the password; compared in constant time.
 */
public final class RestCredentials {

    private final byte[] userDigest;
    private final byte[] passwordDigest;
    private final String username;

    private RestCredentials(String username, String password) {
        this.username = username;
        this.userDigest = sha256(username);
        this.passwordDigest = sha256(password);
    }

    public static RestCredentials of(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("username and password must be non-empty");
        }
        return new RestCredentials(username.trim(), password);
    }

    public String username() {
        return username;
    }

    /** Constant-time comparison; both parts are always evaluated. */
    public boolean matches(String suppliedUser, String suppliedPassword) {
        if (suppliedUser == null || suppliedPassword == null) {
            return false;
        }
        boolean userOk = MessageDigest.isEqual(userDigest, sha256(suppliedUser));
        boolean passOk = MessageDigest.isEqual(passwordDigest, sha256(suppliedPassword));
        return userOk & passOk;
    }

    /** True when {@code suppliedUser} is the configured username (used only to explain a rejection). */
    public boolean userMatches(String suppliedUser) {
        return suppliedUser != null && MessageDigest.isEqual(userDigest, sha256(suppliedUser));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
