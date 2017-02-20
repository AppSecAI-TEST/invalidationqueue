package com.mcherm.invalidationqueue;

/**
 * An exception thrown when a refresh function returns a value but then there is an error when
 * attempting to cache that value.
 */
public class RefreshCouldNotBeCachedException extends RefreshException {

    /** Constructor. */
    public RefreshCouldNotBeCachedException(String fieldName, Object value) {
        super("Unable to cache the value refresh returned for field '" + fieldName +
                  "'. The value was: " + value.toString(),
              null);
    }

}
