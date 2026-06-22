package com.muvhulawa.payments.api;

import com.muvhulawa.payments.domain.ledger.UnbalancedLedgerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Only genuine internal faults (e.g. a ledger invariant breach) become HTTP 5xx. Business
 * rejections never reach here — they are returned as well-formed pacs.002 RJCT documents.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UnbalancedLedgerException.class)
    public ResponseEntity<String> handleUnbalanced(UnbalancedLedgerException ex) {
        // Fail closed: the transaction has rolled back; surface a 500 for ops alerting.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Settlement aborted: " + ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadMessage(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Malformed payment message: " + ex.getMessage());
    }
}
