package com.muvhulawa.payments.idempotency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Record of a message we have already processed, keyed on the ISO MsgId. Payment transports
 * (IBM MQ, SWIFT, real-time clearing) guarantee at-least-once delivery, which means duplicates
 * are not an edge case — they are expected. Without this table, a redelivered pacs.008 would
 * post to the ledger twice: a double payment. The unique constraint on messageId is what makes
 * the guard correct under concurrency, not just the in-code check.
 */
@Entity
@Table(name = "processed_message",
        uniqueConstraints = @UniqueConstraint(name = "uk_processed_msgid", columnNames = "messageId"))
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String messageId;

    @Column(nullable = false)
    private String status;        // ACSC / RJCT of the original processing

    @Column
    private String reasonCode;    // ISO reason code for a RJCT; null for ACSC

    @Lob
    @Column(nullable = false)
    private String pacs002Xml;    // the response we returned the first time

    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() { }

    public ProcessedMessage(String messageId, String status, String reasonCode, String pacs002Xml) {
        this.messageId = messageId;
        this.status = status;
        this.reasonCode = reasonCode;
        this.pacs002Xml = pacs002Xml;
        this.processedAt = Instant.now();
    }

    public String getMessageId() { return messageId; }
    public String getStatus() { return status; }
    public String getReasonCode() { return reasonCode; }
    public String getPacs002Xml() { return pacs002Xml; }
    public Instant getProcessedAt() { return processedAt; }
}
