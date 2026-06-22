package com.muvhulawa.payments.messaging;

/** Raised when an inbound message fails XSD validation (maps to ISO reason FF01). */
public class SchemaInvalidException extends RuntimeException {
    public SchemaInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
