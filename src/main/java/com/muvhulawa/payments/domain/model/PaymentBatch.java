package com.muvhulawa.payments.domain.model;

import java.util.List;

/**
 * A pacs.008 message as the domain sees it: the group identifier ({@code MsgId}) plus the
 * one-or-more credit transfers it carries. Idempotency is keyed on {@link #messageId()} — the
 * whole batch is the unit of "have we answered this before?" — while each {@link CreditTransfer}
 * is validated and settled on its own.
 */
public record PaymentBatch(
        String messageId,
        List<CreditTransfer> transactions
) {
    public int numberOfTransactions() {
        return transactions.size();
    }
}
