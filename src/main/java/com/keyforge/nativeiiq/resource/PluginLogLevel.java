package com.keyforge.nativeiiq.resource;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Configurator;
import sailpoint.plugin.PluginBaseHelper;

/**
 * Applies the plugin's own "logLevel" setting (manifest.xml; editable under IIQ Plugins -> KeyForge settings) to the
 * {@code com.keyforge.nativeiiq} logger: normal (default) = INFO, debug = DEBUG, off = OFF. Same meaning as the
 * KF Agent's KF_LOG_LEVEL. If the IIQ admin has configured that logger explicitly in log4j2.properties, the admin's
 * level wins and the plugin changes nothing. Checked on every request, so a changed setting, or an IIQ
 * log4j2.properties reload, takes effect on the next call. Never fails a request.
 */
final class PluginLogLevel {

    static final String PLUGIN_NAME = "KeyForgeNativeIIQ";
    static final String SETTING = "logLevel";
    static final String LOGGER = "com.keyforge.nativeiiq";
    private static Configuration pluginConfiguration;
    private static LoggerConfig pluginLoggerConfig;

    private PluginLogLevel() {
    }

    static synchronized void apply() {
        try {
            Object context = LogManager.getContext(false);
            if (!(context instanceof LoggerContext)) return;
            LoggerContext loggerContext = (LoggerContext) context;
            Configuration configuration = loggerContext.getConfiguration();
            LoggerConfig explicit = configuration.getLoggers().get(LOGGER);
            boolean ours = configuration == pluginConfiguration && explicit != null
                    && explicit == pluginLoggerConfig;
            if (!shouldApplyPluginLevel(explicit != null, ours)) return;

            Level wanted = levelFor(PluginBaseHelper.getSettingString(PLUGIN_NAME, SETTING));
            if (LogManager.getLogger(LOGGER).getLevel() != wanted) {
                Configurator.setLevel(LOGGER, wanted);
            }
            Configuration active = loggerContext.getConfiguration();
            LoggerConfig applied = active.getLoggers().get(LOGGER);
            pluginConfiguration = active;
            pluginLoggerConfig = applied;
        } catch (Throwable ignored) {
            // logging configuration must never break a data request; IIQ's own log settings then apply
        }
    }

    /** Admin's exact-name LoggerConfig wins; otherwise the plugin owns its generated config. */
    static boolean shouldApplyPluginLevel(boolean hasExplicitConfig, boolean isPluginConfig) {
        return !hasExplicitConfig || isPluginConfig;
    }

    /** "debug" -> DEBUG, "off" -> OFF, anything else (unset, "normal", unknown) -> INFO. */
    static Level levelFor(String setting) {
        String s = setting == null ? "" : setting.trim().toLowerCase(java.util.Locale.ROOT);
        if ("debug".equals(s)) {
            return Level.DEBUG;
        }
        if ("off".equals(s)) {
            return Level.OFF;
        }
        return Level.INFO;
    }
}
