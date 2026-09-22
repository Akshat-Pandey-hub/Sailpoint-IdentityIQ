package com.keyforge.iiq.raw;

/**
 * Thrown when a RAW payload cannot be persisted. Propagating this (rather than swallowing it) makes a
 * RAW-enabled extraction fail cleanly instead of silently claiming the payload was captured when it
 * was not — per the approved failure-isolation design. It never causes a change when RAW is disabled
 * (capture is not invoked at all in that case).
 */
public class RawArchiveException extends RuntimeException {

    public RawArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
