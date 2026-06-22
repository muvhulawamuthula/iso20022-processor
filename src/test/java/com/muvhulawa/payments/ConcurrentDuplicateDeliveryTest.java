package com.muvhulawa.payments;

import com.muvhulawa.payments.application.PaymentProcessingService;
import com.muvhulawa.payments.domain.ledger.LedgerEntry;
import com.muvhulawa.payments.domain.ledger.LedgerRepository;
import com.muvhulawa.payments.domain.model.ProcessingOutcome;
import com.muvhulawa.payments.domain.model.TransactionStatus;
import com.muvhulawa.payments.idempotency.ProcessedMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the duplicate guard under genuine concurrency: two threads submit the SAME pacs.008
 * (same MsgId) at the same instant, both get past the in-memory idempotency read, and both try
 * to commit. The database's unique constraint must let exactly one win. The invariant that
 * matters when real money moves: the ledger is posted exactly ONCE, never twice.
 *
 * This is the scenario the README leads with. Before the fix it produced a 500 on the loser
 * (the constraint violation surfaced at commit, outside the recovery catch); now the loser
 * transparently replays the winner's stored response.
 */
@SpringBootTest
class ConcurrentDuplicateDeliveryTest {

    @Autowired private PaymentProcessingService processingService;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;

    private String validPacs008;

    @BeforeEach
    void loadSample() throws Exception {
        try (var in = new ClassPathResource("samples/valid-pacs008.xml").getInputStream()) {
            validPacs008 = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Clean slate so the assertions count only what this test produced.
        processedMessageRepository.deleteAll();
        ledgerRepository.deleteAll();
    }

    @Test
    void concurrentDuplicatesSettleExactlyOnce() throws Exception {
        CyclicBarrier startLine = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<ProcessingOutcome> submit = () -> {
            startLine.await();                 // release both threads as close to simultaneously as possible
            return processingService.process(validPacs008);
        };

        try {
            Future<ProcessingOutcome> a = pool.submit(submit);
            Future<ProcessingOutcome> b = pool.submit(submit);

            ProcessingOutcome o1 = a.get();    // no ExecutionException: the race loser recovers, it does not 500
            ProcessingOutcome o2 = b.get();

            // Both callers got a well-formed ACSC acknowledgement...
            assertEquals(TransactionStatus.ACSC, o1.groupStatus());
            assertEquals(TransactionStatus.ACSC, o2.groupStatus());
            // ...carrying the identical stored pacs.002.
            assertEquals(o1.pacs002Xml(), o2.pacs002Xml());
            // Exactly one of them was served as an idempotent replay.
            assertTrue(o1.duplicateReplay() ^ o2.duplicateReplay(),
                    "exactly one delivery should be a replay, got "
                            + o1.duplicateReplay() + " / " + o2.duplicateReplay());

            // The money moved once: a single balanced debit/credit pair, not two.
            List<LedgerEntry> postings = ledgerRepository.findByTransactionId("TX-0001");
            assertEquals(2, postings.size(), "duplicate delivery must not double-post the ledger");

            // And a single idempotency record exists for the MsgId.
            assertEquals(1, processedMessageRepository.count());
        } finally {
            pool.shutdownNow();
        }
    }
}
