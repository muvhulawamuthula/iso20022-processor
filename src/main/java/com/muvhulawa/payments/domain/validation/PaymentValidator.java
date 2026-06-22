package com.muvhulawa.payments.domain.validation;

import com.muvhulawa.payments.domain.model.CreditTransfer;
import com.muvhulawa.payments.domain.model.ReasonCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

/**
 * Business validation that runs AFTER the message is already schema-valid. Schema validation
 * proves the message is well-formed; business validation proves it is *acceptable*. These are
 * deliberately separate layers — conflating them is a classic payments mistake, because a
 * schema-valid message can still be a financially invalid instruction.
 */
@Component
public class PaymentValidator {

    private static final Set<String> SUPPORTED_CURRENCIES = Set.of("ZAR", "USD", "EUR", "GBP");

    public Optional<BusinessRuleViolation> validate(CreditTransfer p) {
        if (p.amount() == null || p.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.of(new BusinessRuleViolation(
                    ReasonCode.AM02, "Settlement amount must be strictly positive"));
        }
        if (!SUPPORTED_CURRENCIES.contains(p.currency())) {
            return Optional.of(new BusinessRuleViolation(
                    ReasonCode.AM03, "Unsupported currency: " + p.currency()));
        }
        if (p.debtorAccount() != null && p.debtorAccount().equals(p.creditorAccount())) {
            return Optional.of(new BusinessRuleViolation(
                    ReasonCode.NARR, "Debtor and creditor accounts are identical"));
        }
        return Optional.empty();
    }
}
