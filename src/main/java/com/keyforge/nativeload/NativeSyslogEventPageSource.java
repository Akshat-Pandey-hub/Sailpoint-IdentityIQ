package com.keyforge.nativeload;

/** Transport seam for the native SyslogEvent payload. Production impl: {@link NativeSyslogEventClient}. */
public interface NativeSyslogEventPageSource {

    String fetchPage(int start, int limit);
}
