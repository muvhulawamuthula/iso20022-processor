package com.muvhulawa.payments;

import com.muvhulawa.payments.domain.ledger.LedgerRepository;
import com.muvhulawa.payments.idempotency.ProcessedMessageRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end HTTP coverage of the whole pipeline through the controller: accept, business reject,
 * schema reject, idempotent replay, and partial-batch settlement. Every path must answer 200 with
 * a pacs.002 — a rejected payment is a successful exchange that produced a "no", never an HTTP error.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private LedgerRepository ledgerRepository;
    @Autowired private ProcessedMessageRepository processedMessageRepository;
    @Autowired private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanSlate() {
        processedMessageRepository.deleteAll();
        ledgerRepository.deleteAll();
    }

    private String sample(String name) throws Exception {
        try (var in = new ClassPathResource("samples/" + name).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private MvcResult submit(String xml) throws Exception {
        return mvc.perform(post("/api/v1/payments/pacs008")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(xml))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andReturn();
    }

    @Test
    void validSingleTransferSettlesAndAcknowledgesAcsc() throws Exception {
        MvcResult res = submit(sample("valid-pacs008.xml"));

        assertEquals("ACSC", res.getResponse().getHeader("X-Payment-Status"));
        assertEquals("false", res.getResponse().getHeader("X-Idempotent-Replay"));
        assertEquals("ACSC", txStatuses(res).get("TX-0001"));
        // One balanced debit/credit pair posted.
        assertEquals(2, ledgerRepository.findByTransactionId("TX-0001").size());
    }

    @Test
    void negativeAmountIsRejectedWithAm02ButStill200() throws Exception {
        MvcResult res = submit(sample("invalid-amount-pacs008.xml"));

        assertEquals("RJCT", res.getResponse().getHeader("X-Payment-Status"));
        String body = res.getResponse().getContentAsString();
        assertTrue(body.contains("AM02"), "rejection must carry the ISO reason code AM02");
        // Nothing settled.
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void sameDebtorAndCreditorIsRejectedWithNarr() throws Exception {
        MvcResult res = submit(sample("same-party-pacs008.xml"));
        assertEquals("RJCT", res.getResponse().getHeader("X-Payment-Status"));
        assertTrue(res.getResponse().getContentAsString().contains("NARR"));
    }

    @Test
    void malformedMessageIsRejectedWithFf01AndNo5xx() throws Exception {
        // Schema-invalid: missing the mandatory GrpHdr. Still a 200 carrying a pacs.002 RJCT.
        String malformed = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf/>
                </Document>
                """;
        MvcResult res = submit(malformed);
        assertEquals("RJCT", res.getResponse().getHeader("X-Payment-Status"));
        assertTrue(res.getResponse().getContentAsString().contains("FF01"));
        assertEquals(0, ledgerRepository.count());
    }

    @Test
    void duplicateDeliveryReplaysWithoutDoublePosting() throws Exception {
        String xml = sample("valid-pacs008.xml");

        MvcResult first = submit(xml);
        MvcResult second = submit(xml);

        assertEquals("false", first.getResponse().getHeader("X-Idempotent-Replay"));
        assertEquals("true", second.getResponse().getHeader("X-Idempotent-Replay"));
        // Byte-identical response on replay.
        assertEquals(first.getResponse().getContentAsString(),
                second.getResponse().getContentAsString());
        // The money still moved exactly once.
        assertEquals(2, ledgerRepository.findByTransactionId("TX-0001").size());
        assertEquals(1, processedMessageRepository.count());
    }

    @Test
    void partialBatchSettlesAcceptedTransfersAndReportsPerTransactionStatus() throws Exception {
        double partBefore = counter("iso20022.batches.processed", "groupStatus", "PART");
        double acscBefore = counter("iso20022.transactions.processed", "status", "ACSC", "reason", "NONE");
        double am02Before = counter("iso20022.transactions.processed", "status", "RJCT", "reason", "AM02");

        MvcResult res = submit(sample("partial-batch-pacs008.xml"));

        // Group status reflects a mixed batch.
        assertEquals("PART", res.getResponse().getHeader("X-Payment-Status"));

        Map<String, String> statuses = txStatuses(res);
        assertEquals("ACSC", statuses.get("TXB-0001"));
        assertEquals("RJCT", statuses.get("TXB-0002"));
        assertEquals("ACSC", statuses.get("TXB-0003"));

        // The rejected transaction carries its reason; the response names AM02 exactly once.
        assertTrue(res.getResponse().getContentAsString().contains("AM02"));

        // Only the two accepted transfers were posted: 2 pairs = 4 entries, and the rejected one nil.
        assertEquals(2, ledgerRepository.findByTransactionId("TXB-0001").size());
        assertEquals(0, ledgerRepository.findByTransactionId("TXB-0002").size());
        assertEquals(2, ledgerRepository.findByTransactionId("TXB-0003").size());

        // Business metrics were recorded with the right dimensions (deltas — the registry is
        // shared across tests in this context).
        assertEquals(1.0, counter("iso20022.batches.processed", "groupStatus", "PART") - partBefore);
        assertEquals(2.0, counter("iso20022.transactions.processed", "status", "ACSC", "reason", "NONE") - acscBefore);
        assertEquals(1.0, counter("iso20022.transactions.processed", "status", "RJCT", "reason", "AM02") - am02Before);
        assertTrue(meterRegistry.find("iso20022.settlement.duration").timer().count() >= 1);
    }

    /** Reads a counter's current value, or 0 if it has not been registered yet. */
    private double counter(String name, String... tags) {
        var c = meterRegistry.find(name).tags(tags).counter();
        return c == null ? 0.0 : c.count();
    }

    /** Parses the pacs.002 and maps OrgnlTxId -> TxSts, namespace-agnostically. */
    private Map<String, String> txStatuses(MvcResult res) throws Exception {
        byte[] body = res.getResponse().getContentAsByteArray();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(body));
        NodeList blocks = doc.getElementsByTagNameNS("*", "TxInfAndSts");
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < blocks.getLength(); i++) {
            Element block = (Element) blocks.item(i);
            String txId = block.getElementsByTagNameNS("*", "OrgnlTxId").item(0).getTextContent();
            String sts = block.getElementsByTagNameNS("*", "TxSts").item(0).getTextContent();
            out.put(txId, sts);
        }
        return out;
    }
}
