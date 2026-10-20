package com.nttdata.msaccount.exception;

public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public DependencyUnavailableException(String message) {
        super(message);
    }
}
