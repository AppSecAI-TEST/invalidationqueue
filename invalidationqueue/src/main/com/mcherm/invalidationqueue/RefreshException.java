package com.mcherm.invalidationqueue;

/**
 * An exception type that will be thrown when a refresh function has been defined for a field
 * and no value is available. It has several sub-types for particular problems that can occur.
 */
public abstract class RefreshException extends RuntimeException {

    /** Constructor. */
    public RefreshException(String message, Throwable cause) {
        super(message, cause);
    }

}
