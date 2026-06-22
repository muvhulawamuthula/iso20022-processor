package com.muvhulawa.payments.domain.model;

import java.math.BigDecimal;

/**
 * Domain representation of a single credit-transfer transaction ({@code CdtTrfTxInf}) within a
 * pacs.008 batch, decoupled from the JAXB/XML binding so the rest of the system never touches
 * XML types. A batch ({@link PaymentBatch}) carries one or more of these; each is validated and
 * settled independently and reported with its own status in the pacs.002.
 */
public record CreditTransfer(
        String instructionId,
        String endToEndId,
        String transactionId,
        String currency,
        BigDecimal amount,
        String debtorName,
        String debtorAccount,
        String creditorName,
        String creditorAccount
) {}
