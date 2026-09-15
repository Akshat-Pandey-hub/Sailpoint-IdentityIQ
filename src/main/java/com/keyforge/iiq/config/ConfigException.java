package com.keyforge.iiq.config;

/**
 * Thrown when required configuration is missing or invalid.
 *
 * <p>Unchecked so that misconfiguration fails fast at startup with a clear
 * message rather than being caught and handled deep in the call stack.
 */
public class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
