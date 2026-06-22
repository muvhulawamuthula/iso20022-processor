package com.muvhulawa.payments.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    Optional<ProcessedMessage> findByMessageId(String messageId);
}
