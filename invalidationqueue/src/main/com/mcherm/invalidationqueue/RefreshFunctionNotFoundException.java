package com.mcherm.invalidationqueue;

/**
 * An exception thrown when a refresh function was declared in the componentCacheEntries.json
 * config file, but the function object could not be found.
 */
public class RefreshFunctionNotFoundException extends RefreshException {

    /** Constructor. */
    public RefreshFunctionNotFoundException(String refreshFunctionName, Throwable cause) {
        super("Refresh function '" + refreshFunctionName + "' could not be looked up.", cause);
    }

}
