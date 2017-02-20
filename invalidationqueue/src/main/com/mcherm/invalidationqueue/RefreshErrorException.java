package com.mcherm.invalidationqueue;

/**
 * An exception thrown when attempting to refresh a cache value but the function to do
 * the refresh threw an exception.
 */
public class RefreshErrorException extends RefreshException {

    /** Constructor. */
    public RefreshErrorException(Throwable cause) {
        super("Exception when executing {}", cause);
    }

}
