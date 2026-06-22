package com.muvhulawa.payments.domain.ledger;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A single posting in a double-entry ledger. Every settled payment produces exactly two
 * rows that net to zero: a DEBIT against the debtor account and a CREDIT to the creditor.
 * Querying SUM(signed_amount) per transaction must always equal zero — that invariant is
 * the whole point of double-entry: the books cannot silently go out of balance.
 */
@Entity
@Table(name = "ledger_entry",
        indexes = @Index(name = "idx_ledger_txid", columnList = "transactionId"))
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String transactionId;

    @Column(nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant postedAt;

    protected LedgerEntry() { }   // for JPA

    public LedgerEntry(String transactionId, String accountId, Direction direction,
                       BigDecimal amount, String currency) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.currency = currency;
        this.postedAt = Instant.now();
    }

    /** Signed amount: credits positive, debits negative. Used to assert the zero-sum invariant. */
    public BigDecimal signedAmount() {
        return direction == Direction.CREDIT ? amount : amount.negate();
    }

    public Long getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getAccountId() { return accountId; }
    public Direction getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getPostedAt() { return postedAt; }

    public enum Direction { DEBIT, CREDIT }
}
