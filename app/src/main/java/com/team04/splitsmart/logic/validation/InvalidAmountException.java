package com.team04.splitsmart.logic.validation;

/**
 * Thrown by {@link Money} when user-entered text is not a valid dollar amount or percentage.
 * The message is written for the user and can be shown directly next to the input.
 */
public class InvalidAmountException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidAmountException(String message) {
        super(message);
    }
}
