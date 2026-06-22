package com.muvhulawa.payments.application;

import com.muvhulawa.payments.domain.model.*;
import com.muvhulawa.payments.domain.validation.BusinessRuleViolation;
import com.muvhulawa.payments.domain.validation.PaymentValidator;
import com.muvhulawa.payments.idempotency.IdempotencyService;
import com.muvhulawa.payments.idempotency.ProcessedMessage;
import com.muvhulawa.payments.messaging.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Orchestrates the full inbound pipeline for a pacs.008 credit-transfer batch:
 *
 *   1. XSD validation          (syntactic gate — rejects the whole batch on FF01)
 *   2. Parse -> domain PaymentBatch
 *   3. Idempotency check       (duplicate delivery is expected, not exceptional)
 *   4. Business validation     (per transaction — each is acceptable or not on its own)
 *   5. Ledger settlement       (balanced double-entry posting for every accepted transfer)
 *   6. pacs.002 generation     (ALWAYS returned, with a per-transaction status and a
 *                               derived group status: ACSC / PART / RJCT)
 *
 * This orchestrator is deliberately NOT transactional. The only step that must be atomic —
 * "settle every accepted transfer" + "remember we did it" — is delegated to
 * {@link PaymentSettlementService}, which owns the single transaction. Keeping schema validation
 * and the (DB-free) XSLT generation outside that transaction means we don't hold a database
 * transaction open across XML work.
 *
 * The duplicate guard is the database, not the in-memory read. If two duplicate deliveries race
 * past the {@code findExisting} check, both attempt to commit; one loses on the unique constraint
 * and its whole batch rolls back, and we return the winner's stored response rather than paying
 * twice.
 */
@Service
public class PaymentProcessingService {

    private final SchemaValidator schemaValidator;
    private final Pacs008Parser parser;
    private final PaymentValidator paymentValidator;
    private final IdempotencyService idempotencyService;
    private final PaymentSettlementService settlementService;
    private final Pacs002Generator pacs002Generator;

    public PaymentProcessingService(SchemaValidator schemaValidator,
                                    Pacs008Parser parser,
                                    PaymentValidator paymentValidator,
                                    IdempotencyService idempotencyService,
                                    PaymentSettlementService settlementService,
                                    Pacs002Generator pacs002Generator) {
        this.schemaValidator = schemaValidator;
        this.parser = parser;
        this.paymentValidator = paymentValidator;
        this.idempotencyService = idempotencyService;
        this.settlementService = settlementService;
        this.pacs002Generator = pacs002Generator;
    }

    public ProcessingOutcome process(String pacs008Xml) {
        // 1. Schema gate. Unparseable messages get a minimal whole-batch RJCT (FF01) and stop here.
        try {
            schemaValidator.validate(pacs008Xml);
        } catch (SchemaInvalidException e) {
            return ProcessingOutcome.groupRejected(
                    pacs002Generator.generateSchemaReject(ReasonCode.FF01));
        }

        // 2. Parse to the clean domain model.
        PaymentBatch batch = parser.parse(pacs008Xml);

        // 3. Idempotency: have we already answered this MsgId?
        Optional<ProcessedMessage> existing = idempotencyService.findExisting(batch.messageId());
        if (existing.isPresent()) {
            return replayOf(existing.get());
        }

        // 4. Business validation, per transaction. Each transfer is accepted or rejected on its own.
        List<TransactionOutcome> outcomes = new ArrayList<>(batch.numberOfTransactions());
        List<CreditTransfer> toSettle = new ArrayList<>();
        for (CreditTransfer transfer : batch.transactions()) {
            Optional<BusinessRuleViolation> violation = paymentValidator.validate(transfer);
            if (violation.isPresent()) {
                BusinessRuleViolation v = violation.get();
                outcomes.add(TransactionOutcome.rejected(transfer, v.reasonCode(), v.narrative()));
            } else {
                outcomes.add(TransactionOutcome.accepted(transfer));
                toSettle.add(transfer);
            }
        }

        // 5 + 6. Derive the group status, render the pacs.002, then settle atomically.
        TransactionStatus groupStatus = ProcessingOutcome.groupStatusOf(outcomes);
        String pacs002Xml = pacs002Generator.generate(pacs008Xml, groupStatus, outcomes);
        return commitOrReplay(batch, toSettle, groupStatus, outcomes, pacs002Xml);
    }

    /**
     * Runs the atomic commit. If a concurrent duplicate already claimed this MsgId, the commit's
     * flush rolls back (no double-pay) and we return the winner's already-stored response.
     */
    private ProcessingOutcome commitOrReplay(PaymentBatch batch, List<CreditTransfer> toSettle,
                                             TransactionStatus groupStatus,
                                             List<TransactionOutcome> outcomes, String pacs002Xml) {
        try {
            settlementService.settleBatch(batch, toSettle, groupStatus, pacs002Xml);
            return new ProcessingOutcome(groupStatus, outcomes, pacs002Xml, false);
        } catch (DataIntegrityViolationException raceLost) {
            return idempotencyService.findExisting(batch.messageId())
                    .map(this::replayOf)
                    .orElseThrow(() -> raceLost);
        }
    }

    /**
     * Rebuilds the outcome from the stored record. The per-transaction detail lives in the stored
     * pacs.002 itself, so a replay carries the original document and group status verbatim.
     */
    private ProcessingOutcome replayOf(ProcessedMessage pm) {
        return new ProcessingOutcome(
                TransactionStatus.valueOf(pm.getStatus()), List.of(), pm.getPacs002Xml(), true);
    }
}
