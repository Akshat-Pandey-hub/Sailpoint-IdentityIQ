package com.keyforge.nativeiiq.resource;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;
import sailpoint.plugin.PluginBaseHelper;

/**
 * Applies the plugin's own "logLevel" setting (manifest.xml; editable under IIQ Plugins -> KeyForge settings) to the
 * {@code com.keyforge.nativeiiq} logger: normal (default) = INFO, debug = DEBUG, off = OFF. Same meaning as the
 * KF Agent's KF_LOG_LEVEL. Checked on every request, so a changed setting, or an IIQ log4j2.properties reload that
 * reset the level, takes effect on the next call. Never fails a request.
 */
final class PluginLogLevel {

    static final String PLUGIN_NAME = "KeyForgeNativeIIQ";
    static final String SETTING = "logLevel";
    static final String LOGGER = "com.keyforge.nativeiiq";

    private PluginLogLevel() {
    }

    static void apply() {
        try {
            Level wanted = levelFor(PluginBaseHelper.getSettingString(PLUGIN_NAME, SETTING));
            if (LogManager.getLogger(LOGGER).getLevel() != wanted) {
                Configurator.setLevel(LOGGER, wanted);
            }
        } catch (Throwable ignored) {
            // logging configuration must never break a data request; IIQ's own log settings then apply
        }
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
