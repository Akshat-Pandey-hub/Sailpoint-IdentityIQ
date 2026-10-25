package com.keyforge.iiq.debug;

/**
 * Prepares application logging settings before the first SLF4J logger is created. The separately deployed
 * {@code log4j2.properties} reads its
 * level from the system property {@code kf.log.level} and its folder from {@code kf.log.dir}; this maps the
 * application settings onto them:
 * <ul>
 *   <li>{@code KF_LOG_LEVEL} (environment, {@code -D}, or the {@code IIQ_CONFIG_FILE} file):
 *       {@code normal} → INFO (also the default when unset), {@code debug} → DEBUG,
 *       {@code off} → WARN (no routine lines; errors and rejected requests are still logged with their reason)</li>
 *   <li>{@code KF_LOG_DIR} (environment or {@code -D}): the log folder (default {@code logs})</li>
 * </ul>
 * A value already given on the command line ({@code -Dkf.log.level=...}, {@code -Dkf.log.dir=...}) is kept.
 * Must run before any {@code LoggerFactory.getLogger} call; {@code Main} calls it from its first static block.
 */
public final class LoggingSetup {

    public static final String PROP_LEVEL = "kf.log.level";
    public static final String PROP_DIR = "kf.log.dir";
    public static final String KEY_LOG_DIR = "KF_LOG_DIR";

    private LoggingSetup() {
    }

    public static void init() {
        if (isBlank(System.getProperty(PROP_LEVEL))) {
            System.setProperty(PROP_LEVEL, log4jLevel(DebugLog.level()));
        }
        if (isBlank(System.getProperty(PROP_DIR))) {
            String dir = System.getenv(KEY_LOG_DIR);
            if (isBlank(dir)) {
                dir = System.getProperty(KEY_LOG_DIR);
            }
            if (!isBlank(dir)) {
                System.setProperty(PROP_DIR, dir.trim());
            }
        }
    }

    /** The Log4j2 level name for an application log level. */
    static String log4jLevel(DebugLog.Level level) {
        switch (level) {
            case OFF:
                return "WARN"; // never silent: failures and their reasons are always written
            case NORMAL:
                return "INFO";
            default:
                return "DEBUG";
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
