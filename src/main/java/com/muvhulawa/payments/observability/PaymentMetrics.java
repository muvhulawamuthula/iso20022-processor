package com.muvhulawa.payments.observability;

import com.muvhulawa.payments.domain.model.TransactionOutcome;
import com.muvhulawa.payments.domain.model.TransactionStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Payments-domain metrics, exposed via Actuator / Prometheus. The interesting operational signals
 * for a clearing component are not request counts but <em>business</em> outcomes: the accept/reject
 * mix, which ISO reason codes are firing, how often duplicates are replayed, and how long the
 * atomic settlement takes. All are dimensioned so a dashboard can break them down without code
 * changes.
 */
@Component
public class PaymentMetrics {

    private static final String BATCHES = "iso20022.batches.processed";
    private static final String TRANSACTIONS = "iso20022.transactions.processed";
    private static final String REPLAYS = "iso20022.batches.replayed";

    private final MeterRegistry registry;
    private final Timer settlementTimer;

    public PaymentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.settlementTimer = Timer.builder("iso20022.settlement.duration")
                .description("Time to atomically settle a batch and record its idempotency key")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    /** A batch reached a terminal group status (ACSC / PART / RJCT). */
    public void batchProcessed(TransactionStatus groupStatus) {
        registry.counter(BATCHES, "groupStatus", groupStatus.name()).increment();
    }

    /** A duplicate delivery was served from the idempotency store instead of reprocessed. */
    public void batchReplayed() {
        registry.counter(REPLAYS).increment();
    }

    /** One transaction within a batch was accepted or rejected; reason is NONE when accepted. */
    public void transactionProcessed(TransactionOutcome outcome) {
        registry.counter(TRANSACTIONS,
                "status", outcome.status().name(),
                "reason", outcome.reasonCode() == null ? "NONE" : outcome.reasonCode().name())
                .increment();
    }

    /** Times the atomic settlement commit (including any rollback on a lost duplicate race). */
    public void recordSettlement(Runnable settlement) {
        settlementTimer.record(settlement);
    }
}
