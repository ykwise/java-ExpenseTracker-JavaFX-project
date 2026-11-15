package com.expensetracker;

/** Thrown when the database cannot be read or written. Carries a user-friendly message. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
