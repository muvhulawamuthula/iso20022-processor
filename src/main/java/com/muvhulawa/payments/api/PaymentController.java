package com.muvhulawa.payments.api;

import com.muvhulawa.payments.application.PaymentProcessingService;
import com.muvhulawa.payments.domain.model.ProcessingOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Accepts a raw pacs.008 XML payload and always responds with a pacs.002 status report.
 *
 * Status mapping (the group status of the batch):
 *   ACSC (all settled)        -> 200 OK
 *   PART (some settled)       -> 200 OK  (per-transaction TxSts blocks carry the detail)
 *   RJCT (business/schema)    -> 200 OK  (the message was understood; the instruction was declined)
 *   Duplicate replay          -> 200 OK + X-Idempotent-Replay: true
 *
 * Note we do NOT use HTTP error codes for rejected payments. A rejected payment is a
 * successful protocol exchange that produced a "no" — distinct from a transport failure.
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentProcessingService processingService;

    public PaymentController(PaymentProcessingService processingService) {
        this.processingService = processingService;
    }

    @PostMapping(value = "/pacs008",
            consumes = MediaType.APPLICATION_XML_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> submit(@RequestBody String pacs008Xml) {
        ProcessingOutcome outcome = processingService.process(pacs008Xml);
        return ResponseEntity.status(HttpStatus.OK)
                .header("X-Payment-Status", outcome.groupStatus().name())
                .header("X-Idempotent-Replay", String.valueOf(outcome.duplicateReplay()))
                .contentType(MediaType.APPLICATION_XML)
                .body(outcome.pacs002Xml());
    }
}
