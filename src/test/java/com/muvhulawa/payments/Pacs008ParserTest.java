package com.muvhulawa.payments;

import com.muvhulawa.payments.domain.model.CreditTransfer;
import com.muvhulawa.payments.domain.model.PaymentBatch;
import com.muvhulawa.payments.messaging.Pacs008Parser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parser is the one place XML/JAXB types are allowed to live; everything downstream works with
 * the clean domain model. These tests pin that mapping for both a single transfer and a batch.
 */
class Pacs008ParserTest {

    private final Pacs008Parser parser = new Pacs008Parser();

    private String sample(String name) throws Exception {
        return new String(Files.readAllBytes(Path.of("src/main/resources/samples/" + name)),
                StandardCharsets.UTF_8);
    }

    @Test
    void mapsSingleTransferToDomain() throws Exception {
        PaymentBatch batch = parser.parse(sample("valid-pacs008.xml"));

        assertEquals("MSG-20260622-0001", batch.messageId());
        assertEquals(1, batch.numberOfTransactions());

        CreditTransfer t = batch.transactions().get(0);
        assertEquals("TX-0001", t.transactionId());
        assertEquals("E2E-0001", t.endToEndId());
        assertEquals("ZAR", t.currency());
        assertEquals(0, new BigDecimal("15000.00").compareTo(t.amount()));
        assertEquals("62001234567", t.debtorAccount());
        assertEquals("10009876543", t.creditorAccount());
    }

    @Test
    void mapsEveryTransferInABatchPreservingOrder() throws Exception {
        PaymentBatch batch = parser.parse(sample("partial-batch-pacs008.xml"));

        assertEquals("MSG-20260622-BATCH-01", batch.messageId());
        assertEquals(3, batch.numberOfTransactions());
        assertEquals("TXB-0001", batch.transactions().get(0).transactionId());
        assertEquals("TXB-0002", batch.transactions().get(1).transactionId());
        assertEquals("TXB-0003", batch.transactions().get(2).transactionId());
        // The negative-amount transaction is parsed faithfully; rejecting it is validation's job.
        assertEquals(0, new BigDecimal("-50.00")
                .compareTo(batch.transactions().get(1).amount()));
    }
}
