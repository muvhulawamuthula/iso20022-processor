package com.muvhulawa.payments.application;

import com.muvhulawa.payments.domain.ledger.LedgerService;
import com.muvhulawa.payments.domain.model.CreditTransfer;
import com.muvhulawa.payments.domain.model.PaymentBatch;
import com.muvhulawa.payments.domain.model.TransactionStatus;
import com.muvhulawa.payments.idempotency.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The atomic commit boundary for a whole batch. "Settle every acceptable transfer" and
 * "remember we answered this MsgId" must commit together or not at all, so both live inside ONE
 * transaction here. The batch — not the individual transaction — is the unit of atomicity and of
 * idempotency: a redelivered MsgId must never re-post <em>any</em> of its transfers.
 *
 * <p>This is a separate bean from {@link PaymentProcessingService} on purpose. The duplicate
 * guard works by letting the database's unique constraint reject a racing second delivery: the
 * flush throws {@link org.springframework.dao.DataIntegrityViolationException}, which marks this
 * transaction rollback-only and unwinds <em>all</em> the ledger postings and the record. The
 * exception then propagates to the orchestrator. Because Spring's transaction interceptor rolls
 * this inner transaction back <em>before</em> rethrowing, the orchestrator can safely open a
 * fresh transaction to read the winner's stored response. Catching the violation inside the same
 * transaction could not do that: a rollback-only transaction cannot be read from and re-committed.
 */
@Service
public class PaymentSettlementService {

    private final LedgerService ledgerService;
    private final IdempotencyService idempotencyService;

    public PaymentSettlementService(LedgerService ledgerService,
                                    IdempotencyService idempotencyService) {
        this.ledgerService = ledgerService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Post a balanced double-entry settlement for every accepted transfer and record the batch's
     * group acknowledgement — all in one transaction. If any single settlement breaks the
     * zero-sum invariant, or a racing duplicate claims this MsgId, the entire batch rolls back.
     */
    @Transactional
    public void settleBatch(PaymentBatch batch, List<CreditTransfer> accepted,
                            TransactionStatus groupStatus, String pacs002Xml) {
        for (CreditTransfer transfer : accepted) {
            ledgerService.settle(transfer);
        }
        idempotencyService.record(batch.messageId(), groupStatus.name(), null, pacs002Xml);
    }
}
