package com.muvhulawa.payments;

import com.muvhulawa.payments.messaging.SchemaInvalidException;
import com.muvhulawa.payments.messaging.SchemaValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payment ingress is untrusted input, and XML payment rails are a textbook XXE target. This test
 * proves the hardening is real, not decorative: a message that declares a DOCTYPE with an external
 * entity pointing at a local secret must be rejected at the schema gate (DOCTYPE is disallowed),
 * and the secret's contents must never end up anywhere we could echo back to the sender.
 */
class XxeHardeningTest {

    private final SchemaValidator validator = newValidator();

    private static SchemaValidator newValidator() {
        SchemaValidator v = new SchemaValidator();
        try {
            var init = SchemaValidator.class.getDeclaredMethod("init");
            init.setAccessible(true);
            init.invoke(v);
        } catch (Exception e) {
            throw new IllegalStateException("could not initialise SchemaValidator under test", e);
        }
        return v;
    }

    @Test
    void doctypeWithExternalEntityIsRejectedAndSecretNeverExpands() throws IOException {
        // A real on-disk secret the attacker tries to exfiltrate via an external entity.
        Path secret = Files.createTempFile("xxe-secret", ".txt");
        Files.writeString(secret, "TOP-SECRET-LEDGER-KEY-42");

        String attack = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [ <!ENTITY xxe SYSTEM "file://%s"> ]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08">
                  <FIToFICstmrCdtTrf>
                    <GrpHdr><MsgId>&xxe;</MsgId></GrpHdr>
                  </FIToFICstmrCdtTrf>
                </Document>
                """.formatted(secret.toAbsolutePath());

        // DOCTYPE is disallowed, so this never even parses far enough to expand the entity.
        SchemaInvalidException ex = assertThrows(SchemaInvalidException.class,
                () -> validator.validate(attack));

        // Belt-and-braces: whatever the error message says, it must not contain the secret.
        assertFalse(ex.getMessage().contains("TOP-SECRET-LEDGER-KEY-42"),
                "the external entity must never have been resolved");

        Files.deleteIfExists(secret);
    }

    @Test
    void plainWellFormedMessagePassesTheGate() throws Exception {
        String valid = new String(Files.readAllBytes(Path.of(
                "src/main/resources/samples/valid-pacs008.xml")), StandardCharsets.UTF_8);
        assertDoesNotThrow(() -> validator.validate(valid));
    }
}
