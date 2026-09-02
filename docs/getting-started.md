# Getting started

Run the ISO 20022 `pacs.008` → `pacs.002` processor locally, with Docker, or as the full
Kong + Postgres stack.

## Prerequisites

- **JDK 21** and **Maven 3.9+** for local / test runs
- **Docker** (and Docker Compose) for the container and full stack

## Local (in-memory H2)

Default Spring profile is `local`: in-memory H2, no external services, Flyway off, Hibernate
`create-drop`. Ledger and idempotency store are **not** durable here.

```bash
mvn spring-boot:run
```

Service listens on `http://localhost:8080`.

### Submit a pacs.008

```bash
curl -s -X POST http://localhost:8080/api/v1/payments/pacs008 \
  -H "Content-Type: application/xml" \
  --data-binary @src/main/resources/samples/valid-pacs008.xml
```

Response is always `application/xml` `pacs.002` with HTTP `200`, plus:

| Header | Meaning |
|---|---|
| `X-Payment-Status` | Group status: `ACSC`, `PART`, or `RJCT` |
| `X-Idempotent-Replay` | `true` when the stored response for this `MsgId` was returned |

Submit the **same** sample again: identical body, `X-Idempotent-Replay: true`, no second ledger
posting.

### Sample messages

All under `src/main/resources/samples/`:

| File | Group status | Notes |
|---|---|---|
| `valid-pacs008.xml` | `ACSC` | Settles; inspect ledger afterward |
| `partial-batch-pacs008.xml` | `PART` | 2 of 3 settle; rejected leg gets `AM02` |
| `invalid-amount-pacs008.xml` | `RJCT` | `AM02` — amount not allowed |
| `same-party-pacs008.xml` | `RJCT` | `NARR` — debtor account = creditor account |

Malformed / schema-invalid XML returns group `RJCT` with reason `FF01` (via the fallback
generator; the batch never reaches the domain).

Business rules enforced after schema validation (`PaymentValidator`):

- Amount must be strictly positive → `AM02`
- Currency in `{ZAR, USD, EUR, GBP}` → else `AM03`
- Debtor and creditor accounts must differ → else `NARR`

### Inspect the H2 ledger

Open `http://localhost:8080/h2-console` with JDBC URL `jdbc:h2:mem:ledger` (user `sa`, empty
password).

### Metrics

```bash
curl -s http://localhost:8080/actuator/prometheus | grep iso20022
```

Counters include batch group status, per-transaction status/reason, replay count, and
settlement latency percentiles.

### Tests

```bash
mvn test
```

Coverage includes business rules, the double-entry invariant, full HTTP flow, partial-batch
settlement, concurrent duplicate delivery, and XXE rejection.

## Docker (standalone image)

Builds the app with the default `local` profile (H2). Non-root user and healthcheck are set in
the `Dockerfile`.

```bash
docker build -t iso20022-processor .
docker run -p 8080:8080 iso20022-processor
```

Then use the same `curl` against `localhost:8080`.

## Full stack: Kong + app + Postgres

```bash
docker compose up --build
```

Topology:

```
counterparty ──▶ kong :8000 ──▶ app :8080 ──▶ postgres :5432
                 (authn, rate-limit,         (durable ledger +
                  size cap, correlation)      idempotency store)
```

- App is **not** published to the host; only Kong (`:8000` proxy, `:8100` status/metrics) and
  Postgres (`localhost:5433` → container `5432`) are exposed.
- App profile: `postgres`. Schema is owned by **Flyway**
  (`src/main/resources/db/migration`); Hibernate runs `validate`-only.
- Named volume keeps ledger and answered `MsgId`s across restarts.

### Edge policy (`kong/kong.yml`)

| Plugin | Behavior |
|---|---|
| `key-auth` | Header `apikey` required; credentials stripped before upstream |
| `rate-limiting` | 60 req/min per Kong consumer |
| `request-size-limiting` | 512 KB body cap |
| `correlation-id` | Stamps / echoes `X-Correlation-ID` |
| `prometheus` | Edge metrics at `http://localhost:8100/metrics` |

Demo consumer key (rotate in any real deployment):

```bash
curl -s -X POST http://localhost:8000/api/v1/payments/pacs008 \
  -H "Content-Type: application/xml" \
  -H "apikey: demo-bank-key-please-rotate" \
  --data-binary @src/main/resources/samples/valid-pacs008.xml
```

Omit the key → Kong `401` before the app sees the request.

Inspect Postgres on the host: `localhost:5433`, database/user/password `payments`.

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `mvn -B verify` on JDK 21 for pushes and PRs
to `main`.

## Next reading

- [Architecture](architecture.md) — pipeline classes, transactional boundary, data model
