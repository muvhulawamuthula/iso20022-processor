package com.muvhulawa.payments.domain.ledger;

import com.muvhulawa.payments.domain.model.CreditTransfer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Settles a payment by writing balanced double-entry postings. The settlement is a single
 * transaction: either both legs commit or neither does. Before committing we verify the
 * zero-sum invariant defensively — money systems fail closed, not open.
 */
@Service
public class LedgerService {

    private final LedgerRepository repository;

    public LedgerService(LedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void settle(CreditTransfer transfer) {
        LedgerEntry debit = new LedgerEntry(
                transfer.transactionId(), transfer.debtorAccount(),
                LedgerEntry.Direction.DEBIT, transfer.amount(), transfer.currency());
        LedgerEntry credit = new LedgerEntry(
                transfer.transactionId(), transfer.creditorAccount(),
                LedgerEntry.Direction.CREDIT, transfer.amount(), transfer.currency());

        BigDecimal residual = debit.signedAmount().add(credit.signedAmount());
        if (residual.compareTo(BigDecimal.ZERO) != 0) {
            throw new UnbalancedLedgerException(transfer.transactionId(), residual);
        }
        repository.saveAll(List.of(debit, credit));
    }
}
