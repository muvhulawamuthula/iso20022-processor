package com.muvhulawa.payments.domain.model;

/**
 * ISO 20022 transaction/group status codes (subset) used in pacs.002 status reports.
 * In a credit-transfer flow a counterparty must ALWAYS receive a status — never a bare error.
 *
 * <p>{@link #ACSC} and {@link #RJCT} apply at both the transaction and group level.
 * {@link #PART} is a <em>group-only</em> status: it means the batch was partially accepted —
 * some transactions settled, others were rejected — and the per-transaction {@code TxSts}
 * blocks must be consulted for the detail.
 */
public enum TransactionStatus {
    ACSC,  // AcceptedSettlementCompleted — funds posted to the ledger
    PART,  // PartiallyAccepted — group only; mix of ACSC and RJCT transactions
    RJCT;  // Rejected — see accompanying ReasonCode
}
