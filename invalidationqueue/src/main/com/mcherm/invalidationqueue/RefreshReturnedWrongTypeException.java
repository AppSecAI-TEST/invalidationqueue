package com.mcherm.invalidationqueue;

/**
 * An exception thrown when an refresh function runs but returns the wrong type.
 */
public class RefreshReturnedWrongTypeException extends RefreshException {

    /** Constructor. */
    public RefreshReturnedWrongTypeException(String refreshFunctionName, Class expectedType, Class actualType) {
        super("Refresh function '" + refreshFunctionName + "' was expected to return " + expectedType.toString() +
        " but it actually returned " + actualType.toString() + ".", null);
    }

}
