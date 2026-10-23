package com.keyforge.iiq.debug;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KF_LOG_LEVEL → Log4j2 level, and the bundled log4j2.properties (console + bounded rolling file). */
class LoggingSetupTest {

    @Test
    void mapsApplicationLevelsToLog4jLevels() {
        assertEquals("DEBUG", LoggingSetup.log4jLevel(DebugLog.Level.DEBUG));
        assertEquals("INFO", LoggingSetup.log4jLevel(DebugLog.Level.NORMAL));
        assertEquals("OFF", LoggingSetup.log4jLevel(DebugLog.Level.OFF));
    }

    @Test
    void keepsALevelAndFolderAlreadyGivenOnTheCommandLine() {
        String oldLevel = System.getProperty(LoggingSetup.PROP_LEVEL);
        String oldDir = System.getProperty(LoggingSetup.PROP_DIR);
        try {
            System.setProperty(LoggingSetup.PROP_LEVEL, "WARN");
            System.setProperty(LoggingSetup.PROP_DIR, "custom-logs");
            LoggingSetup.init();
            assertEquals("WARN", System.getProperty(LoggingSetup.PROP_LEVEL));
            assertEquals("custom-logs", System.getProperty(LoggingSetup.PROP_DIR));
        } finally {
            restore(LoggingSetup.PROP_LEVEL, oldLevel);
            restore(LoggingSetup.PROP_DIR, oldDir);
        }
    }

    @Test
    void bundledConfigurationHasConsoleAndBoundedRollingFile() throws Exception {
        File logDir = new File("target/log4j2-config-test");
        String oldLevel = System.getProperty(LoggingSetup.PROP_LEVEL);
        String oldDir = System.getProperty(LoggingSetup.PROP_DIR);
        System.setProperty(LoggingSetup.PROP_LEVEL, "INFO");
        System.setProperty(LoggingSetup.PROP_DIR, logDir.getPath());
        // A private context: parse the bundled file without replacing the shared logging context other tests use.
        LoggerContext ctx = new LoggerContext("bundled-config-test");
        File file1 = new File("src/main/resources/log4j2.properties");
        Configuration config = new PropertiesConfigurationFactory().getConfiguration(ctx,
                new ConfigurationSource(new FileInputStream(file1), file1));
        config.initialize();
        try {
            assertEquals(Level.INFO, config.getRootLogger().getLevel(), "level comes from kf.log.level");

            Map<String, Appender> appenders = config.getAppenders();
            assertInstanceOf(ConsoleAppender.class, appenders.get("CONSOLE"));
            RollingFileAppender file = (RollingFileAppender) appenders.get("FILE");
            assertEquals(new File(logDir, "kfagent.log").getPath(), new File(file.getFileName()).getPath(),
                    "log file goes to kf.log.dir");
            assertTrue(file.getFilePattern().endsWith("kfagent-%d{yyyy-MM-dd}-%i.log.gz"), file.getFilePattern());

            String layout = String.valueOf(file.getLayout().getContentFormat());
            assertTrue(layout.contains("%d{yyyy-MM-dd HH:mm:ss.SSS}") && layout.contains("%-5level")
                    && layout.contains("%t") && layout.contains("%c{1.}") && layout.contains("%m"), layout);
        } finally {
            config.stop();
            restore(LoggingSetup.PROP_LEVEL, oldLevel);
            restore(LoggingSetup.PROP_DIR, oldDir);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
