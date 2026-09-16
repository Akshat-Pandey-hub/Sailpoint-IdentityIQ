package com.keyforge.iiq.rest;

/**
 * A client-facing API error carrying an HTTP status, a short {@code error} label, and a human
 * {@code message}. Server internals (stack traces, filesystem paths) are never placed here.
 */
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;

    public ApiException(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public String error() {
        return error;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "Bad Request", message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "Not Found", message);
    }
}
