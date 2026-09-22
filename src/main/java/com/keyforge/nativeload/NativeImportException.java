package com.keyforge.nativeload;

/** Unchecked failure while pulling or parsing the native Identity payload. */
public class NativeImportException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NativeImportException(String message) {
        super(message);
    }

    public NativeImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
