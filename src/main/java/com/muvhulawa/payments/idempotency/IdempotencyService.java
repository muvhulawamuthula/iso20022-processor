package com.muvhulawa.payments.idempotency;

import com.muvhulawa.payments.domain.model.ReasonCode;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class IdempotencyService {

    private final ProcessedMessageRepository repository;

    public IdempotencyService(ProcessedMessageRepository repository) {
        this.repository = repository;
    }

    public Optional<ProcessedMessage> findExisting(String messageId) {
        return repository.findByMessageId(messageId);
    }

    /**
     * Records that we have answered this MsgId. We {@code saveAndFlush} deliberately: the INSERT
     * (and therefore any unique-constraint violation from a racing duplicate delivery) must be
     * pushed to the database *now*, inside the caller's transaction, so it can be caught and
     * rolled back — rather than surfacing at commit time, after the catch block is long gone.
     */
    public void record(String messageId, String status, ReasonCode reasonCode, String pacs002Xml) {
        repository.saveAndFlush(new ProcessedMessage(
                messageId, status, reasonCode == null ? null : reasonCode.name(), pacs002Xml));
    }
}
