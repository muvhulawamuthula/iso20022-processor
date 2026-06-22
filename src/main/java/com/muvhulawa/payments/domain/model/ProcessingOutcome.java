package com.muvhulawa.payments.domain.model;

import java.util.List;

/**
 * Result of processing one pacs.008 batch: the group status, the per-transaction verdicts,
 * the generated pacs.002 XML, and whether this was an idempotent replay of an already-seen
 * message.
 *
 * <p>The group status is derived from the transaction verdicts: all accepted -&gt; ACSC,
 * all rejected -&gt; RJCT, otherwise -&gt; PART. A schema-invalid message that never parsed
 * is reported as a whole-group RJCT with no per-transaction detail.
 */
public record ProcessingOutcome(
        TransactionStatus groupStatus,
        List<TransactionOutcome> transactions,
        String pacs002Xml,
        boolean duplicateReplay
) {
    /** Derives the group status from per-transaction verdicts and builds the outcome. */
    public static ProcessingOutcome of(List<TransactionOutcome> transactions, String pacs002Xml) {
        return new ProcessingOutcome(groupStatusOf(transactions), transactions, pacs002Xml, false);
    }

    /** A whole-batch rejection that never reached transaction-level processing (e.g. schema-invalid). */
    public static ProcessingOutcome groupRejected(String pacs002Xml) {
        return new ProcessingOutcome(TransactionStatus.RJCT, List.of(), pacs002Xml, false);
    }

    public ProcessingOutcome asReplay() {
        return new ProcessingOutcome(groupStatus, transactions, pacs002Xml, true);
    }

    /** all accepted -&gt; ACSC, all rejected -&gt; RJCT, otherwise -&gt; PART. */
    public static TransactionStatus groupStatusOf(List<TransactionOutcome> transactions) {
        boolean anyAccepted = transactions.stream().anyMatch(TransactionOutcome::isAccepted);
        boolean anyRejected = transactions.stream().anyMatch(t -> !t.isAccepted());
        if (anyAccepted && anyRejected) {
            return TransactionStatus.PART;
        }
        return anyAccepted ? TransactionStatus.ACSC : TransactionStatus.RJCT;
    }
}
