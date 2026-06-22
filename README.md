# ISO 20022 Payment Message Processor

A Spring Boot service that ingests, validates, transforms, settles, and acknowledges
**ISO 20022 `pacs.008`** (FI-to-FI Customer Credit Transfer) messages, responding with a
**`pacs.002`** (FI-to-FI Payment Status Report) on every request.

This is the kind of component that sits at the heart of a national payments or interbank
clearing system. It is deliberately built to demonstrate **payments-engineering judgment** —
not just "it works", but *why each decision is the safe one when real money moves*.

---

## What it does

```
  pacs.008 XML
       │
       ▼
 ┌─────────────────┐   FF01 (schema-invalid)
 │ 1. XSD validate │──────────────────────────► pacs.002 RJCT
 └─────────────────┘
       │ valid
       ▼
 ┌─────────────────┐
 │ 2. Parse (JAXB) │  → clean domain Payment (no XML types leak past this line)
 └─────────────────┘
       │
       ▼
 ┌─────────────────┐   already seen
 │ 3. Idempotency  │──────────────────────────► replay stored pacs.002 (no double-pay)
 └─────────────────┘
       │ first time
       ▼
 ┌─────────────────┐   AM02 / AM03 / NARR
 │ 4. Business     │──────────────────────────► pacs.002 RJCT (+ ISO reason code)
 │    validation   │
 └─────────────────┘
       │ acceptable
       ▼
 ┌─────────────────┐
 │ 5. Settle       │  → balanced double-entry ledger postings (debit + credit, nets to 0)
 │    (ledger)     │
 └─────────────────┘
       │
       ▼
 ┌─────────────────┐
 │ 6. Generate     │  → pacs.002 ACSC  (XSLT transform of the original message)
 │    pacs.002     │
 └─────────────────┘
```

Spec requirements this single project demonstrates: **XSD validation, XSLT transformation,
XML processing, ISO 20022 message standards, and payment-domain logic** — the five things
that move a CV from "general Java dev" to "payments engineer".

---

## Run it

```bash
mvn spring-boot:run
```

Submit the valid sample (settles, returns ACSC):

```bash
curl -s -X POST http://localhost:8080/api/v1/payments/pacs008 \
  -H "Content-Type: application/xml" \
  --data-binary @src/main/resources/samples/valid-pacs008.xml
```

Submit it **again** — same `MsgId` — and you get the *identical* response with
`X-Idempotent-Replay: true` and **no second ledger posting**.

Other samples that exercise rejection paths:

| Sample | Outcome | ISO reason |
|---|---|---|
| `valid-pacs008.xml` | ACSC (settled) | — |
| `invalid-amount-pacs008.xml` | RJCT | `AM02` Not allowed amount |
| `same-party-pacs008.xml` | RJCT | `NARR` Debtor = Creditor |
| any malformed XML | RJCT | `FF01` Invalid file format |

Inspect the ledger at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:ledger`).

```bash
mvn test   # unit tests for business rules + the double-entry invariant
```

---

## Design decisions (the part interviewers actually probe)

**1. Schema validation and business validation are separate layers — on purpose.**
A schema-valid message can still be a financially invalid instruction (a perfectly well-formed
message instructing a negative transfer). Conflating "well-formed" with "acceptable" is a
classic payments bug. XSD is the syntax gate; `PaymentValidator` is the semantics gate.

**2. A rejected payment is a *successful* exchange, not an HTTP error.**
Business rejections return `200 OK` carrying a `pacs.002 RJCT` with an ISO reason code — never a
`4xx/5xx` with a stack trace. The counterparty's reconciliation depends on receiving a
structured status. We fail *with a document*, not *with an exception*. Only a genuine internal
fault (e.g. a ledger invariant breach) becomes a 5xx.

**3. Idempotency is keyed on `MsgId`, and the database is the real guard.**
Payment transports (IBM MQ, SWIFT, real-time clearing) are at-least-once — duplicates are
*expected*, not exceptional. The in-memory check is an optimisation; the **unique constraint**
on `processed_message.message_id` is what makes it correct under concurrency. If two duplicate
deliveries race, one loses on the constraint and we return the winner's stored response rather
than paying twice. Without this, a redelivered `pacs.008` = a double payment.

**4. Double-entry ledger with an enforced zero-sum invariant.**
Every settlement writes exactly two postings — a debit and a credit — that must net to zero,
checked *before* commit. The settlement is one transaction: both legs commit or neither does.
Money systems fail closed: if the books would go out of balance, the transaction rolls back.

**5. Compile-once / use-per-call for all XML engines.**
`Schema`, `JAXBContext`, and XSLT `Templates` are immutable, thread-safe, and expensive — built
once at startup. `Validator`, `Unmarshaller`, and `Transformer` are stateful and **not**
thread-safe — created per call. Getting this split wrong is a real production footgun: either
you pay the compile cost on every request, or you corrupt state under load.

**6. XXE hardening on every parser.**
Payment ingress is untrusted input. DOCTYPE declarations and external entities are disabled on
the schema factory, the StAX reader, and the transformer factory. XML-based payment rails are a
textbook XXE target.

**7. JAXB model is decoupled from the domain.**
XML/JAXB types stop at `Pacs008Parser`. Everything downstream works with the plain `Payment`
record, so the ledger and validation never depend on the wire format. Swapping `pacs.008.001.08`
for a newer version touches the binding layer only.

---

## Production hardening (the honest "what's next")

This is a portfolio-scale build. To run in a real clearing context you would add:

- **Official ISO 20022 schemas.** The bundled `pacs.008.001.08.xsd` is a faithful, valid
  *subset* so the project runs out of the box. Drop the official schema from
  [iso20022.org](https://www.iso20022.org/iso-20022-message-definitions) into
  `src/main/resources/schema/` and the validation gate uses it unchanged.
- **IBM MQ ingress** instead of (or alongside) the HTTP endpoint — a JMS listener with a
  dead-letter queue, poison-message handling, and the same idempotent consumer logic. (This is
  the companion "IBM MQ Integration Gateway" project.)
- **Multi-transaction batches.** A pacs.008 can carry many `CdtTrfTxInf` entries; this build
  settles the first and is explicit about it. Real ingress iterates every transaction and reports
  a per-transaction status in the pacs.002.
- **Real account/balance checks** — `AC04` closed account, insufficient-funds, limit checks.
- **Persistent idempotency store** (the in-memory H2 table resets on restart) and a retention
  policy.
- **Observability** — OpenTelemetry traces per message, queue-depth and reject-rate metrics
  wired to the Actuator endpoints already enabled here.

---

## Stack

Java 21 · Spring Boot 3.3 · Spring Data JPA · JAXB (jakarta) · JAXP (XSD + XSLT) · H2 · JUnit 5
