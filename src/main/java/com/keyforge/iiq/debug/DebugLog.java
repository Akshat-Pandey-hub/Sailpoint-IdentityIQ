package com.keyforge.iiq.debug;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Audit log for Part 1: one short line per step, so a run can be followed end to end — what started, which service
 * ran and what it does, the result, and every error with a plain-language reason.
 *
 * <p>Level, from {@code KF_LOG_LEVEL} (environment, {@code -DKF_LOG_LEVEL=...}, or the {@code IIQ_CONFIG_FILE}
 * properties file); default {@code normal}:
 * <ul>
 *   <li>{@code normal}: what ran and its result — start, service, result, login OK/failed, plus every error,
 *       warning and rejected request (401, 4xx) with its reason. No usernames, passwords or settings.</li>
 *   <li>{@code debug}: everything in normal plus every setting loaded, each request with the credentials sent and
 *       expected, and every call to IdentityIQ. <b>Prints usernames and the KF Agent password in plain text.</b></li>
 *   <li>{@code off}: no routine lines; errors, warnings and rejected requests are still written with their
 *       reason.</li>
 * </ul>
 * The IdentityIQ password ({@code IIQ_PASSWORD}) is never printed at any level. Legacy {@code KF_DEBUG=false}
 * (when {@code KF_LOG_LEVEL} is not set) means {@code off}.
 *
 * <p>Message format: {@code [<request/command id>] <STEP> | key=value | key=value}, written through SLF4J (logger
 * {@code com.keyforge.audit}); time, level, thread and destination (console + rolling file) come from
 * {@code log4j2.properties}.
 */
public final class DebugLog {

    public static final String KEY_LEVEL = "KF_LOG_LEVEL";
    public static final String KEY_DEBUG = "KF_DEBUG";

    /** How much is logged. */
    public enum Level { OFF, NORMAL, DEBUG }

    static final String HIDDEN = "****** (hidden)";

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Long> STARTED = new ThreadLocal<>();

    /** Settings read through AppConfig (environment, -D, or the IIQ_CONFIG_FILE properties file). */
    private static final List<String> FILE_BACKED = List.of(
            "IIQ_BASE_URL", "IIQ_USERNAME", "IIQ_PASSWORD", "KFAGENT_USERNAME", "KFAGENT_PASSWORD", KEY_LEVEL);
    /** Every setting printed at startup in debug, in this order. */
    private static final List<String> SETTINGS = List.of(
            "IIQ_CONFIG_FILE", "IIQ_BASE_URL", "IIQ_USERNAME", "IIQ_PASSWORD",
            "KFAGENT_USERNAME", "KFAGENT_PASSWORD", "KFAGENT_HOST", "KFAGENT_PORT", "REST_HOST", "REST_PORT",
            "PARQUET_OUT_DIR", "RAW_OUT_DIR", "IIQ_DEBUG_HTTP", KEY_LEVEL);
    /** Optional settings printed at startup only when they are set. */
    private static final List<String> SHOWN_ONLY_WHEN_SET = List.of("PARQUET_OUT_DIR", "RAW_OUT_DIR", "IIQ_DEBUG_HTTP");
    /** Values never printed, at any level. */
    private static final List<String> ALWAYS_HIDDEN = List.of("IIQ_PASSWORD", "PG_PASSWORD");
    /** Values printed only in debug: usernames, the KF Agent credentials, and what a caller sent. */
    private static final List<String> DEBUG_ONLY_KEYS = List.of(
            "IIQ_USERNAME", "KFAGENT_USERNAME", "KFAGENT_PASSWORD",
            "sentUser", "sentPassword", "expectedUser", "expectedPassword");
    /** Whole lines printed only in debug. */
    private static final List<String> DEBUG_ONLY_STEPS = List.of("SETTING", "REQUEST", "IIQ-CALL", "AUTH");

    private static volatile Level cachedLevel;
    private static volatile Properties cachedFile;
    private static volatile boolean fileLoaded;

    private DebugLog() {
    }

    /** The configured level (read once). */
    public static Level level() {
        Level l = cachedLevel;
        if (l == null) {
            l = parseLevel(raw(KEY_LEVEL, configFile()), firstNonBlank(System.getenv(KEY_DEBUG),
                    System.getProperty(KEY_DEBUG)));
            cachedLevel = l;
        }
        return l;
    }

    /**
     * {@code KF_LOG_LEVEL}: {@code debug} → DEBUG; {@code normal}/{@code info} → NORMAL; {@code off}/{@code quiet}/
     * {@code error}/{@code warn} → OFF (errors and warnings only). Unset or unrecognised → NORMAL: DEBUG is never
     * the default. When KF_LOG_LEVEL is unset, the legacy {@code KF_DEBUG=true|false} is honoured.
     */
    static Level parseLevel(String levelSetting, String legacyDebug) {
        if (levelSetting != null && !levelSetting.isBlank()) {
            switch (levelSetting.trim().toLowerCase()) {
                case "debug":
                case "trace":
                    return Level.DEBUG;
                case "off":
                case "none":
                case "quiet":
                case "error":
                case "warn":
                    return Level.OFF;
                default:
                    return Level.NORMAL;
            }
        }
        if (legacyDebug != null) {
            if ("false".equalsIgnoreCase(legacyDebug.trim())) {
                return Level.OFF;
            }
            if ("true".equalsIgnoreCase(legacyDebug.trim())) {
                return Level.DEBUG;
            }
        }
        return Level.NORMAL;
    }

    /**
     * True when routine lines (start, service, result) are written, i.e. not at {@code off}. Guard for building
     * routine lines. Errors and warnings ({@link #failure}, {@link #warning}, {@link #warn}) are written at every
     * level and do not check this.
     */
    public static boolean enabled() {
        return level() != Level.OFF;
    }

    /** True only at debug level. */
    public static boolean debugEnabled() {
        return level() == Level.DEBUG;
    }

    // --- request / command context -----------------------------------------

    /** Starts a request or command scope; its short id prefixes every line on this thread until {@link #end}. */
    public static String begin(String prefix) {
        String id = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        CONTEXT.set(id);
        STARTED.set(System.nanoTime());
        OUTCOME.remove();
        return id;
    }

    /**
     * True once this request's outcome has been logged (a RESULT line, a warning or a failure), so the closing
     * RESPONSE line can drop to debug instead of repeating it.
     */
    public static boolean outcomeLogged() {
        return Boolean.TRUE.equals(OUTCOME.get());
    }

    private static final ThreadLocal<Boolean> OUTCOME = new ThreadLocal<>();

    /** Milliseconds since {@link #begin} on this thread, or -1. */
    public static long elapsedMs() {
        Long t = STARTED.get();
        return t == null ? -1 : (System.nanoTime() - t) / 1_000_000;
    }

    public static void end() {
        CONTEXT.remove();
        STARTED.remove();
        OUTCOME.remove();
    }

    // --- writing -----------------------------------------------------------

    /**
     * One line: {@code step} then alternating key, value pairs. Filtered and masked for the configured level, then
     * written through SLF4J (logger {@value #LOGGER_NAME}): ERROR steps at error, debug-only steps at debug, the
     * rest at info. Time, level and thread come from the log4j2 pattern.
     */
    public static void log(String step, Object... keyValues) {
        String line = render(level(), step, false, keyValues);
        if (line == null) {
            return;
        }
        if ("RESULT".equals(step)) {
            OUTCOME.set(Boolean.TRUE);
        }
        if (isDebugOnly(step, keyValues)) {
            Audit.LOG.debug(line);
        } else {
            Audit.LOG.info(line);
        }
    }

    /**
     * The closing line of a request: INFO when nothing has reported its outcome yet (e.g. /health, Parquet
     * queries), otherwise a DEBUG detail so the same outcome is not logged twice.
     */
    public static void response(Object... keyValues) {
        boolean already = outcomeLogged();
        String line = render(level(), "RESPONSE", false, keyValues);
        if (line == null) {
            return;
        }
        if (already) {
            Audit.LOG.debug(line);
        } else if (level() != Level.OFF) {
            Audit.LOG.info(line);
        }
    }

    /** A WARN line written at every level (e.g. a rejected request and its reason). */
    public static void warn(String step, Object... keyValues) {
        String line = render(level(), step, true, keyValues);
        if (line != null) {
            OUTCOME.set(Boolean.TRUE);
            Audit.LOG.warn(line);
        }
    }

    /**
     * Logs ONE error line for a failure, at every level: {@code what} failed, the plain-language reason, the root
     * cause, where in our code it happened, any {@code context} key/values, and the settings the reason depends on
     * (IIQ_PASSWORD always hidden). The stack trace is attached for unexpected errors, and for every error at debug.
     *
     * @param log  the caller's logger (so the line carries the caller's class)
     * @param what short description of what failed, e.g. "Configuration error: PG_HOST is missing"
     */
    public static void failure(org.slf4j.Logger log, String what, Throwable t, Object... context) {
        write(log, true, what, t, context);
    }

    /** Like {@link #failure} but at WARN, for a problem the run recovers from. */
    public static void warning(org.slf4j.Logger log, String what, Throwable t, Object... context) {
        write(log, false, what, t, context);
    }

    private static void write(org.slf4j.Logger log, boolean error, String what, Throwable t, Object... context) {
        OUTCOME.set(Boolean.TRUE);
        if (t == null) {
            String line = render(level(), error ? "FAILED" : "WARNING", true, concat(new Object[]{"what", what}, context));
            if (error) {
                log.error(line);
            } else {
                log.warn(line);
            }
            return;
        }
        Throwable root = rootCause(t);
        String reason = reason(t, root);
        StackTraceElement where = firstOwnFrame(t);
        Object[] head = {
                "what", what,
                "reason", reason,
                // an IdentityIQ/plugin error carries IIQ's own reason ("IdentityIQ says: ..."); show it, since
                // the IIQ server log is on the client's server and not readable from here
                "cause", root == t && !(t instanceof com.keyforge.iiq.client.IiqApiException)
                        ? t.getClass().getSimpleName()
                        : root.getClass().getSimpleName() + ": " + firstLine(root.getMessage()),
                "at", where == null ? "-" : where.toString()};
        String line = render(level(), error ? "FAILED" : "WARNING", true,
                concat(concat(head, context), valuesFor(reason)));
        boolean withStack = debugEnabled() || reason.startsWith("UNEXPECTED");
        if (error) {
            if (withStack) {
                log.error(line, t);
            } else {
                log.error(line);
            }
        } else if (withStack) {
            log.warn(line, t);
        } else {
            log.warn(line);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private static Object[] concat(Object[] a, Object[] b) {
        Object[] all = new Object[a.length + b.length];
        System.arraycopy(a, 0, all, 0, a.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    /** SLF4J logger name for these lines (configurable on its own in log4j2.properties). */
    public static final String LOGGER_NAME = "com.keyforge.audit";

    /**
     * Holds the logger so it is created on first use, not when this class loads: {@link LoggingSetup} reads the
     * level through this class BEFORE logging may start (otherwise Log4j2 would initialize without kf.log.level /
     * kf.log.dir).
     */
    private static final class Audit {
        static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LOGGER_NAME);
    }

    private static boolean isDebugOnly(String step, Object... keyValues) {
        return DEBUG_ONLY_STEPS.contains(step)
                || ("IIQ-LOGIN".equals(step) && keyValues.length > 1 && "start".equals(keyValues[1]));
    }

    /**
     * The message for this level ({@code [context] STEP | key=value | ...}), or null when the level does not print
     * it. IIQ_PASSWORD is always masked; in normal, debug-only lines are dropped and usernames/passwords/sent
     * credentials are left out.
     */
    static String render(Level level, String step, boolean alwaysShown, Object... keyValues) {
        if (level == Level.OFF && !alwaysShown) {
            return null;
        }
        boolean debug = level == Level.DEBUG;
        if (!debug && !alwaysShown && isDebugOnly(step, keyValues)) {
            return null;
        }
        StringBuilder sb = new StringBuilder(256);
        String ctx = CONTEXT.get();
        sb.append('[').append(ctx == null ? "-" : ctx).append("] ");
        sb.append(String.format("%-14s", step));
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            String key = String.valueOf(keyValues[i]);
            if (!debug && DEBUG_ONLY_KEYS.contains(key)) {
                continue;
            }
            Object value = ALWAYS_HIDDEN.contains(key) && keyValues[i + 1] != null ? HIDDEN : keyValues[i + 1];
            sb.append(" | ").append(key).append('=').append(show(value));
        }
        if (keyValues.length % 2 == 1) {
            sb.append(" | ").append(show(keyValues[keyValues.length - 1]));
        }
        return sb.toString();
    }

    /** One error line for a failure with no caller message (uncaught errors, background threads). */
    public static void error(Throwable t, Object... context) {
        if (t != null) {
            failure(Audit.LOG, t.getClass().getSimpleName() + ": " + firstLine(t.getMessage()), t, context);
        }
    }

    /** Startup / command line: what is running and every setting it loaded, with where each came from. */
    public static void settings(String command, String purpose) {
        if (!enabled()) {
            return;
        }
        log("START", "command", command, "purpose", purpose, "javaVersion", System.getProperty("java.version"),
                "logLevel", debugEnabled() ? "debug" : "normal");
        if (!debugEnabled()) {
            return;
        }
        Properties file = configFile();
        for (String key : SETTINGS) {
            Object value = raw(key, file);
            if (value == null && SHOWN_ONLY_WHEN_SET.contains(key)) {
                continue; // optional settings the KF Agent does not use: not worth a "<not set>" line
            }
            if (ALWAYS_HIDDEN.contains(key) && value != null) {
                value = HIDDEN;
            }
            log("SETTING", "name", key, "value", value, "source", source(key, file), "note", note(key, file));
        }
    }

    /** The resolved (trimmed) value of a setting, the way Part 1 reads it; null when not set. */
    public static String setting(String key) {
        String raw = raw(key, configFile());
        return raw == null ? null : raw.trim();
    }

    // --- reasons -----------------------------------------------------------

    /** "failed with HTTP 4xx" in an IdentityIQ client error (401/403/404 are matched earlier). */
    private static final java.util.regex.Pattern IIQ_CLIENT_ERROR = java.util.regex.Pattern.compile("http 4\\d\\d");

    static String reason(Throwable t, Throwable root) {
        String msg = (t.getMessage() == null ? "" : t.getMessage()).toLowerCase();
        String rootMsg = (root.getMessage() == null ? "" : root.getMessage()).toLowerCase();
        String type = t.getClass().getSimpleName();
        if (msg.contains("login failed")) {
            return "IIQ_LOGIN_REJECTED: IdentityIQ rejected IIQ_USERNAME / IIQ_PASSWORD";
        }
        if (msg.contains("redirected") && msg.contains("not accepted")) {
            return "IIQ_SESSION_REJECTED: IdentityIQ redirected to its login page; the session expired or the login is"
                    + " no longer valid";
        }
        if (inChain(t, UnknownHostException.class)) {
            return "IIQ_HOST_UNKNOWN: the IIQ_BASE_URL host name cannot be resolved";
        }
        if (inChain(t, ConnectException.class)) {
            return "IIQ_UNREACHABLE: could not connect to IIQ_BASE_URL (connection refused)";
        }
        if (inChain(t, HttpTimeoutException.class) || rootMsg.contains("timed out")) {
            return "IIQ_TIMEOUT: IdentityIQ did not respond in time";
        }
        if (msg.contains("i/o error calling identityiq")) {
            return "IIQ_IO_ERROR: the connection to IIQ_BASE_URL failed";
        }
        if (msg.contains("http 404")) {
            return "IIQ_NOT_FOUND: IdentityIQ endpoint not found (HTTP 404); check the KeyForge plugin and IIQ_BASE_URL";
        }
        if (msg.contains("http 401") || msg.contains("http 403")) {
            return "IIQ_FORBIDDEN: IdentityIQ denied access (HTTP 401/403) for IIQ_USERNAME";
        }
        if (msg.contains("http 5")) {
            return "IIQ_SERVER_ERROR: IdentityIQ returned a server error (HTTP 5xx); see the IdentityIQ log";
        }
        if (msg.contains("identityiq") && IIQ_CLIENT_ERROR.matcher(msg).find()) {
            return "IIQ_REQUEST_REJECTED: IdentityIQ rejected the request (HTTP 4xx)";
        }
        if (t instanceof SQLException || root instanceof SQLException) {
            return "POSTGRES_ERROR: PostgreSQL connection or statement failed; check the PG_* settings";
        }
        if (type.contains("Config")) {
            return "CONFIGURATION_ERROR: a required setting is missing or invalid";
        }
        if (t instanceof OutOfMemoryError) {
            return "OUT_OF_MEMORY: the Java process ran out of memory";
        }
        if (t instanceof IllegalArgumentException) {
            return "BAD_INPUT: a request parameter or filter is not valid";
        }
        if (t instanceof InterruptedException) {
            return "INTERRUPTED: the operation was interrupted";
        }
        return "UNEXPECTED: " + type;
    }

    private static boolean inChain(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    /** The settings that a reason depends on, printed with the error. */
    private static Object[] valuesFor(String reason) {
        if (reason.startsWith("IIQ_") ) {
            return new Object[]{"IIQ_BASE_URL", setting("IIQ_BASE_URL"), "IIQ_USERNAME", setting("IIQ_USERNAME"),
                    "IIQ_PASSWORD", setting("IIQ_PASSWORD")};
        }
        return new Object[0];
    }

    // --- helpers -----------------------------------------------------------

    private static String raw(String key, Properties file) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        v = System.getProperty(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        if (FILE_BACKED.contains(key) && file != null) {
            v = file.getProperty(key);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static String source(String key, Properties file) {
        String v = System.getenv(key);
        if (v != null && !v.isBlank()) {
            return "environment";
        }
        v = System.getProperty(key);
        if (v != null && !v.isBlank()) {
            return "-D system property";
        }
        if (FILE_BACKED.contains(key) && file != null) {
            v = file.getProperty(key);
            if (v != null && !v.isBlank()) {
                return "file " + System.getenv("IIQ_CONFIG_FILE");
            }
        }
        return "not set";
    }

    private static String note(String key, Properties file) {
        String v = raw(key, file);
        if (v == null) {
            if (key.startsWith("KFAGENT_USERNAME") || key.startsWith("KFAGENT_PASSWORD")) {
                return "not set: every REST request will be rejected with 401";
            }
            return "-";
        }
        StringBuilder n = new StringBuilder();
        n.append("length ").append(v.trim().length());
        if (!v.equals(v.trim())) {
            n.append(", has leading/trailing spaces or line ending (trimmed when used)");
        }
        if (v.trim().startsWith("\"") || v.trim().startsWith("'")) {
            n.append(", starts with a quote (the quote is part of the value)");
        }
        return n.toString();
    }

    /** The IIQ_CONFIG_FILE properties (read once), or null. */
    private static Properties configFile() {
        if (fileLoaded) {
            return cachedFile;
        }
        String path = firstNonBlank(System.getenv("IIQ_CONFIG_FILE"), System.getProperty("IIQ_CONFIG_FILE"));
        Properties loaded = null;
        if (path != null) {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(path.trim())) {
                p.load(in);
                loaded = p;
            } catch (Exception unreadable) {
                loaded = null;
            }
        }
        cachedFile = loaded;
        fileLoaded = true;
        return loaded;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }

    private static String show(Object v) {
        if (v == null) {
            return "<not set>";
        }
        String s = String.valueOf(v);
        if (s.isEmpty()) {
            return "\"\"";
        }
        if (!s.equals(s.trim())) {
            return "\"" + s.replace("\r", "\\r").replace("\n", "\\n") + "\"";
        }
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String firstLine(String s) {
        if (s == null) {
            return "-";
        }
        int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl);
        return line.length() > 400 ? line.substring(0, 400) + "..." : line;
    }

    private static StackTraceElement firstOwnFrame(Throwable t) {
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("com.keyforge") && !e.getClassName().equals(DebugLog.class.getName())) {
                return e;
            }
        }
        return null;
    }
}
