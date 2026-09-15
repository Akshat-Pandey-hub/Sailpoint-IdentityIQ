package com.keyforge.iiq.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Holds the connection settings required to talk to IdentityIQ.
 *
 * <p>Values are resolved securely from the runtime environment rather than being
 * baked into source or resources. Resolution order (first non-blank wins):
 * <ol>
 *     <li>Environment variables (primary, recommended)</li>
 *     <li>JVM system properties ({@code -Dkey=value})</li>
 *     <li>An external properties file referenced by {@code IIQ_CONFIG_FILE}</li>
 * </ol>
 *
 * <p>The three settings are:
 * {@code IIQ_BASE_URL}, {@code IIQ_USERNAME}, {@code IIQ_PASSWORD}.
 *
 * <p>The password is never exposed by {@link #toString()} or logged by this class.
 */
public final class AppConfig {

    public static final String KEY_BASE_URL = "IIQ_BASE_URL";
    public static final String KEY_USERNAME = "IIQ_USERNAME";
    public static final String KEY_PASSWORD = "IIQ_PASSWORD";

    /** Optional: path to an external properties file used as a fallback source. */
    public static final String KEY_CONFIG_FILE = "IIQ_CONFIG_FILE";

    private final String baseUrl;
    private final String username;
    private final String password;

    /**
     * Creates a validated configuration. All three arguments are required and
     * must be non-blank; the base URL must be a valid {@code http(s)} URL.
     *
     * @throws ConfigException if any value is missing or malformed
     */
    public AppConfig(String baseUrl, String username, String password) {
        this.baseUrl = requireUrl(KEY_BASE_URL, baseUrl);
        this.username = requireNonBlank(KEY_USERNAME, username);
        this.password = requireNonBlank(KEY_PASSWORD, password);
    }

    /**
     * Loads configuration from the runtime environment. See the class javadoc
     * for the resolution order.
     *
     * @throws ConfigException with a clear message listing every missing setting
     */
    public static AppConfig load() {
        Properties fileProps = loadOptionalFile();

        String baseUrl = resolve(KEY_BASE_URL, fileProps);
        String username = resolve(KEY_USERNAME, fileProps);
        String password = resolve(KEY_PASSWORD, fileProps);

        List<String> missing = new ArrayList<>();
        if (isBlank(baseUrl)) {
            missing.add(KEY_BASE_URL);
        }
        if (isBlank(username)) {
            missing.add(KEY_USERNAME);
        }
        if (isBlank(password)) {
            missing.add(KEY_PASSWORD);
        }
        if (!missing.isEmpty()) {
            throw new ConfigException(
                    "Missing required configuration: " + String.join(", ", missing)
                    + ". Set these as environment variables (e.g. export "
                    + KEY_BASE_URL + "=...), JVM system properties (-D" + KEY_BASE_URL
                    + "=...), or in a file referenced by " + KEY_CONFIG_FILE + ".");
        }

        return new AppConfig(baseUrl, username, password);
    }

    /**
     * Base URL with any trailing slash removed, e.g.
     * {@code https://preview.keyforge.ai/identityiq}.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    // --- resolution helpers -------------------------------------------------

    private static String resolve(String key, Properties fileProps) {
        String fromEnv = System.getenv(key);
        if (!isBlank(fromEnv)) {
            return fromEnv.trim();
        }
        String fromSysProp = System.getProperty(key);
        if (!isBlank(fromSysProp)) {
            return fromSysProp.trim();
        }
        String fromFile = fileProps.getProperty(key);
        if (!isBlank(fromFile)) {
            return fromFile.trim();
        }
        return null;
    }

    private static Properties loadOptionalFile() {
        Properties props = new Properties();
        String configPath = System.getenv(KEY_CONFIG_FILE);
        if (isBlank(configPath)) {
            configPath = System.getProperty(KEY_CONFIG_FILE);
        }
        if (isBlank(configPath)) {
            return props;
        }
        Path path = Path.of(configPath.trim());
        if (!Files.isReadable(path)) {
            throw new ConfigException(
                    KEY_CONFIG_FILE + " points to a file that does not exist or is not readable: " + path);
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            throw new ConfigException("Failed to read configuration file: " + path, e);
        }
        return props;
    }

    // --- validation helpers -------------------------------------------------

    private static String requireNonBlank(String key, String value) {
        if (isBlank(value)) {
            throw new ConfigException("Missing required configuration: " + key);
        }
        return value.trim();
    }

    private static String requireUrl(String key, String value) {
        String v = requireNonBlank(key, value);
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            throw new ConfigException(key + " must be an http(s) URL but was: " + v);
        }
        // Normalise: strip trailing slashes so callers can append paths uniformly.
        while (v.endsWith("/")) {
            v = v.substring(0, v.length() - 1);
        }
        return v;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** Deliberately excludes the password so it can never leak via logging. */
    @Override
    public String toString() {
        return "AppConfig{baseUrl=" + baseUrl + ", username=" + username + ", password=***}";
    }
}
