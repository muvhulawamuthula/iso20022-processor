package com.muvhulawa.payments.domain.model;

/**
 * The verdict for a single {@link CreditTransfer} within a batch: accepted (ACSC) or rejected
 * (RJCT) with an ISO reason code. These per-transaction verdicts drive both the {@code TxSts}
 * blocks in the pacs.002 and the derivation of the group status (ACSC / PART / RJCT).
 */
public record TransactionOutcome(
        String transactionId,
        String endToEndId,
        TransactionStatus status,   // only ACSC or RJCT at the transaction level
        ReasonCode reasonCode,      // null when ACSC
        String narrative            // null unless rejected
) {
    public static TransactionOutcome accepted(CreditTransfer t) {
        return new TransactionOutcome(t.transactionId(), t.endToEndId(),
                TransactionStatus.ACSC, null, null);
    }

    public static TransactionOutcome rejected(CreditTransfer t, ReasonCode code, String narrative) {
        return new TransactionOutcome(t.transactionId(), t.endToEndId(),
                TransactionStatus.RJCT, code, narrative);
    }

    public boolean isAccepted() {
        return status == TransactionStatus.ACSC;
    }
}
