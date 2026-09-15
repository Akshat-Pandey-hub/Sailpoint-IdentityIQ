package com.keyforge.iiq.client;

/**
 * Raised when a call to the IdentityIQ REST/SCIM API fails.
 *
 * <p>Covers both transport failures (I/O, interruption) and non-2xx HTTP
 * responses. When the failure is a non-2xx response, {@link #getStatusCode()}
 * carries the HTTP status; otherwise it is {@code -1}.
 *
 * <p>Messages are built so they never contain credentials.
 */
public class IiqApiException extends RuntimeException {

    private static final int NO_STATUS = -1;

    private final int statusCode;

    public IiqApiException(String message) {
        this(message, NO_STATUS, null);
    }

    public IiqApiException(String message, Throwable cause) {
        this(message, NO_STATUS, cause);
    }

    public IiqApiException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public IiqApiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code of the failing response, or {@code -1} if not applicable. */
    public int getStatusCode() {
        return statusCode;
    }

    public boolean hasStatusCode() {
        return statusCode != NO_STATUS;
    }
}
