# Architecture

How the `pacs.008` → `pacs.002` processor is structured: layers, pipeline, transactional
boundary, persistence, and the optional Kong edge.

## Package layout

```
com.muvhulawa.payments
├── api/                  PaymentController, GlobalExceptionHandler
├── application/          PaymentProcessingService, PaymentSettlementService
├── domain/
│   ├── model/            PaymentBatch, CreditTransfer, statuses, ReasonCode
│   ├── validation/       PaymentValidator, BusinessRuleViolation
│   └── ledger/           LedgerService, LedgerEntry, LedgerRepository, UnbalancedLedgerException
├── idempotency/          IdempotencyService, ProcessedMessage (+ repository)
├── messaging/            SchemaValidator, Pacs008Parser, Pacs002Generator, Pacs002Fallback, JAXB types
└── observability/        PaymentMetrics (Micrometer)
```

XML/JAXB types live under `messaging.jaxb` and **stop at** `Pacs008Parser`. Everything
downstream uses plain domain records (`PaymentBatch`, `CreditTransfer`).

## Request path

`POST /api/v1/payments/pacs008` (`Content-Type: application/xml`)

`PaymentController` always returns HTTP `200` with a `pacs.002` body for understood outcomes
(including business and schema rejects). Headers:

- `X-Payment-Status` — group status (`ACSC` | `PART` | `RJCT`)
- `X-Idempotent-Replay` — whether the response was served from the idempotency store

Only unexpected internal faults (for example a ledger invariant breach handled as an error) map
to 5xx via `GlobalExceptionHandler`.

## Processing pipeline

Orchestrated by `PaymentProcessingService` (deliberately **not** `@Transactional`):

| Step | Component | Failure / outcome |
|---|---|---|
| 1. XSD validate | `SchemaValidator` | Whole-batch `RJCT` / `FF01` via `Pacs002Fallback` |
| 2. Parse | `Pacs008Parser` (JAXB → domain) | Domain `PaymentBatch` |
| 3. Idempotency read | `IdempotencyService.findExisting(MsgId)` | Replay stored `pacs.002` |
| 4. Business rules | `PaymentValidator` per `CreditTransfer` | Per-txn `ACSC` or `RJCT` (`AM02` / `AM03` / `NARR`) |
| 5. Render status | `Pacs002Generator` (XSLT) | `pacs.002` with per-txn `TxSts` + group status |
| 6. Atomic commit | `PaymentSettlementService.settleBatch` | Ledger posts + `processed_message` row |

Schema validation and XSLT stay outside the DB transaction so a connection is not held across
XML work. MDC key `msgId` is set after parse for end-to-end log correlation.

### Group status

Derived from per-transaction outcomes (`ProcessingOutcome.groupStatusOf`):

- all accepted → `ACSC`
- mix of accepted and rejected → `PART`
- all rejected → `RJCT`

### Concurrent duplicates

If two deliveries race past the in-memory/read check, both attempt commit. One wins the unique
constraint on `message_id`; the other hits `DataIntegrityViolationException`, rolls back, and
replays the winner’s stored response — no double pay.

## Settlement and ledger

`PaymentSettlementService` owns the **single** transactional unit: for every accepted transfer,
post balanced ledger entries, then record the `MsgId` and the exact `pacs.002` XML to replay.

`LedgerService` writes exactly two postings per settled transfer (debit debtor, credit creditor)
and asserts they net to zero before commit. Violation → `UnbalancedLedgerException` → rollback.

## Persistence

Flyway migration `V1__initial_schema.sql` (Postgres profile):

**`processed_message`**

- `message_id` — unique; the real idempotency guard
- `status` — original group status
- `reason_code` — optional ISO reason for rejects
- `pacs002xml` — exact first response (replayed verbatim)
- `processed_at`

**`ledger_entry`**

- `transaction_id`, `account_id`, `direction` (`DEBIT` / `CREDIT`)
- `amount`, `currency`, `posted_at`
- index on `transaction_id` for the zero-sum check

### Profiles

| Profile | Store | Schema ownership | Durability |
|---|---|---|---|
| `local` (default) | H2 in-memory | Hibernate `create-drop`; Flyway off | Lost on restart |
| `postgres` | Postgres | Flyway migrations; Hibernate `validate` | Survives restart (named volume in Compose) |

Container / Compose sets `SPRING_PROFILES_ACTIVE=postgres`.

## XML engine lifecycle

Built once at startup (immutable, thread-safe): `Schema`, `JAXBContext`, XSLT `Templates`.

Created per call (stateful): `Validator`, `Unmarshaller`, `Transformer`.

XXE defenses: DOCTYPE / external entities disabled on the schema factory, StAX reader, and
transformer factory; covered by `XxeHardeningTest`.

`Pacs002Generator` computes verdicts in Java and supplies them to the stylesheet as an
in-process node-set (`document()` never fetches remotely). XSLT remains a renderer.

## Edge topology (Compose)

```
counterparty ──▶ Kong :8000 ──▶ app :8080 ──▶ Postgres
```

Declarative config: `kong/kong.yml` (DB-less Kong). Edge plugins: key-auth, per-consumer
rate-limiting (60/min), 512 KB request-size limit, correlation-id, Prometheus on `:8100`.

The app port is only exposed on the Compose network; counterparties talk to Kong alone.

## Observability

`PaymentMetrics` registers:

- `iso20022.batches.processed` — tagged by `groupStatus`
- `iso20022.transactions.processed` — tagged by `status`, `reason`
- `iso20022.batches.replayed`
- `iso20022.settlement.duration` — timer with percentiles

Scraped via Spring Actuator `/actuator/prometheus`. Behind Kong, pair `X-Correlation-ID` with
log `msgId` for edge-to-ledger traces.

## What this is not

Documented intentionally as out of scope today (see README “Production hardening”): official
full ISO schema pack, MQ/JMS ingress, real balance/`AC04` checks, idempotency TTL archival,
and OpenTelemetry spans. Do not assume those exist in code.
