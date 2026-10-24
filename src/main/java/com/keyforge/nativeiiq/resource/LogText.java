package com.keyforge.nativeiiq.resource;

/** Makes caller-supplied text safe to put in a log line: no line breaks or control characters, bounded length. */
final class LogText {

    private static final int MAX_LENGTH = 200;

    private LogText() {
    }

    static String safe(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
        for (int i = 0; i < value.length() && sb.length() < MAX_LENGTH; i++) {
            char c = value.charAt(i);
            sb.append(Character.isISOControl(c) ? '_' : c);
        }
        if (value.length() > MAX_LENGTH) {
            sb.append("...");
        }
        return sb.toString();
    }
}
