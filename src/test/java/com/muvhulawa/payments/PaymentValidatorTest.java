package com.muvhulawa.payments;

import com.muvhulawa.payments.domain.ledger.LedgerEntry;
import com.muvhulawa.payments.domain.model.CreditTransfer;
import com.muvhulawa.payments.domain.model.ReasonCode;
import com.muvhulawa.payments.domain.validation.BusinessRuleViolation;
import com.muvhulawa.payments.domain.validation.PaymentValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PaymentValidatorTest {

    private final PaymentValidator validator = new PaymentValidator();

    private CreditTransfer payment(BigDecimal amount, String ccy, String dbtrAcct, String cdtrAcct) {
        return new CreditTransfer("INSTR", "E2E", "TX", ccy, amount,
                "Debtor", dbtrAcct, "Creditor", cdtrAcct);
    }

    @Test
    void acceptsValidPayment() {
        assertTrue(validator.validate(
                payment(new BigDecimal("100.00"), "ZAR", "ACC1", "ACC2")).isEmpty());
    }

    @Test
    void rejectsNegativeAmountWithAm02() {
        Optional<BusinessRuleViolation> v =
                validator.validate(payment(new BigDecimal("-1.00"), "ZAR", "ACC1", "ACC2"));
        assertTrue(v.isPresent());
        assertEquals(ReasonCode.AM02, v.get().reasonCode());
    }

    @Test
    void rejectsUnsupportedCurrencyWithAm03() {
        Optional<BusinessRuleViolation> v =
                validator.validate(payment(new BigDecimal("100.00"), "XYZ", "ACC1", "ACC2"));
        assertTrue(v.isPresent());
        assertEquals(ReasonCode.AM03, v.get().reasonCode());
    }

    @Test
    void rejectsSamePartyWithNarr() {
        Optional<BusinessRuleViolation> v =
                validator.validate(payment(new BigDecimal("100.00"), "ZAR", "SAME", "SAME"));
        assertTrue(v.isPresent());
        assertEquals(ReasonCode.NARR, v.get().reasonCode());
    }

    @Test
    void doubleEntryPostingsNetToZero() {
        var debit = new LedgerEntry("TX", "ACC1", LedgerEntry.Direction.DEBIT,
                new BigDecimal("100.00"), "ZAR");
        var credit = new LedgerEntry("TX", "ACC2", LedgerEntry.Direction.CREDIT,
                new BigDecimal("100.00"), "ZAR");
        assertEquals(0, debit.signedAmount().add(credit.signedAmount())
                .compareTo(BigDecimal.ZERO));
    }
}
