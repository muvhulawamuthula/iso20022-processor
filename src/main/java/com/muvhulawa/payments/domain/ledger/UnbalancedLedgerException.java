package com.muvhulawa.payments.domain.ledger;

import java.math.BigDecimal;

/** Thrown if a settlement attempt would leave the ledger out of balance. This should be
 *  impossible by construction; if it ever fires, the transaction must roll back rather
 *  than persist a broken set of books. */
public class UnbalancedLedgerException extends RuntimeException {
    public UnbalancedLedgerException(String transactionId, BigDecimal residual) {
        super("Ledger postings for transaction " + transactionId
                + " do not net to zero (residual=" + residual + ")");
    }
}
