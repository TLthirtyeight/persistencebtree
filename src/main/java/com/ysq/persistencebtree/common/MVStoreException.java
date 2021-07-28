package com.ysq.persistencebtree.common;

/**
 * Various kinds of MVStore problems, along with associated error code.
 */
public class MVStoreException extends RuntimeException {

    private static final long serialVersionUID = 2847042930249663807L;

    private final int errorCode;

    public MVStoreException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}