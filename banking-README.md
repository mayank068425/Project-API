# Banking Account Management API

Built to the structure you supplied. `com.banking`, service interfaces split from `serviceimpl`,
`dto.request`/`dto.response`, `schema.sql`/`data.sql`, ModelMapper config alongside hand-written
mappers.

Java 21 · Spring Boot 3.3.5 · Spring MVC · Spring Security + JWT · Spring Data JPA · MySQL 8 · Maven · Swagger · Lombok · JUnit 5

```bash
mvn spring-boot:run          # dev profile, expects MySQL on localhost:3306
```

Logins: `admin`, `teller`, `priya`, `arjun`, `meera` — all `Password@123`.
Swagger at `/swagger-ui.html`. **84 Java files**, all 24 endpoints from your spec, everything in
your tree present.

---

## The decision the whole design rests on: a transfer is two ledger rows

The obvious model is one row per transfer carrying `from_account` and `to_account`. Here is what
happens when you ask the destination account for its own history:

```
ONE ROW per transfer:
  WHERE account_id = ACC1 -> 2 rows, balance 700.00
  WHERE account_id = ACC2 -> 0 rows, balance 0        <-- the money arrived, the row didn't

TWO ROWS sharing one reference:
  WHERE account_id = ACC1 -> 2 rows, balance 700.00
  WHERE account_id = ACC2 -> 1 row,  balance 300.00
```

The destination's balance is not derivable from its own rows. Every statement query becomes an OR
across two columns, and the receiving customer's history is simply missing the credit.

So a transfer writes a `TRANSFER_OUT` debit and a `TRANSFER_IN` credit, sharing one
`transaction_reference`, in one database transaction. Two things fall out of that:

- Any account's balance is `SUM(signed(amount))` over its own rows.
- A transfer nets to zero across the pair, so summing signed amounts across the **whole ledger**
  equals money that genuinely entered the bank. That system-wide reconciliation isn't expressible
  in the one-row model.

`transaction_reference` is deliberately **not unique** — carrying it on two rows is the point.

## Three separate concurrency failures, all on the transfer endpoint

Each was reproduced before the fix was written.

**Lost update.** Balance 1000, two concurrent withdrawals of 600. Both read 1000, both pass the
sufficiency check, both write. Final balance **400**, 1200 withdrawn, nothing raised.
→ `PESSIMISTIC_WRITE` on every account touched. Optimistic locking alone detects it only after the
work is done, turning every contended debit into a retry.

**Deadlock.** Transfer A→B locks `[ACC1, ACC2]`; transfer B→A locks `[ACC2, ACC1]`. Each holds
what the other needs next; MySQL kills one. Only appears under real concurrency.
→ `lockByAccountNumbersOrdered` sorts by primary key, so both callers take `[ACC1, ACC2]` and no
cycle is constructible. `RepositoryQueryContractTest` asserts the `order by a.id` is still there.

**Account number collision.** `MAX(account_number)+1` — two concurrent openings read the same
maximum and both write the number after it. The unique index saves the data, but one ordinary
account opening fails with a constraint error the teller can't act on.
→ `UPDATE number_sequence SET next_value = LAST_INSERT_ID(next_value + 1)` makes the increment and
the read one atomic statement.

## Beneficiaries: two controls doing different jobs

Your spec lists Add / Verify / List but leaves open whether an unverified payee can receive money.
It can't, and there's a second rule beyond that:

| payee | verified | age | ₹50,000 transfer |
|---|---|---|---|
| Mum | yes | 40 days | **allowed** |
| Landlord | yes | 90 min | **refused** — capped at ₹5,000 inside the window |
| NewPayee | no | — | **refused** — not verified |

- **Verification** stops a typo'd account number succeeding silently into a stranger's account. The
  confirmed name is taken from the bank's record, not from what the payer typed — otherwise
  mistyping *both* number and name produces a payee that looks entirely correct.
- **The cooling-off window** is anti-fraud. A session takeover adds a payee and empties the account
  in seconds; a few hours at a low cap is the window in which the real customer sees the
  notification and calls.

Both are enforced on the **transfer** path, not just at add time. A rule checked once when a record
is created is not a rule. The account number is also not editable on update — changing it would
repoint a verified payee at somewhere nobody confirmed, which is the exact attack verification
exists to prevent.

## Smaller decisions worth knowing

**Minimum balance is waived on closure.** A savings account with a ₹1,000 floor can't be withdrawn
below it — but closing requires a zero balance. Applying the floor to both would trap the
customer's money inside a rule meant to protect it. `drainForClosure()` ignores it; `debit()` does
not.

**A closed account refuses credits.** Not just debits. Crediting one puts money somewhere nobody
can withdraw from, and unpicking it needs a human. A freeze blocks both directions too — blocking
only debits would let money keep arriving in an account under investigation.

**Self-transfer is refused.** Not harmless: it writes two rows netting to zero and inflates the
statement with a movement that never happened.

**`CustomerMapper` is hand-written.** ModelMapper is configured (STRICT, ambiguity fatal) and
available — but a reflective mapper copies every name-matching field, including `national_id`,
which must never leave the system. An omission enforced by a hand-written method is visible; one
that depends on a response record happening not to declare a field silently reverses the moment
somebody adds one.

**Age is derived from date of birth, never stored.** It gates account opening, and a stored age is
wrong from the customer's next birthday. Leap-day birthdays verified.

**`value_date` is separate from `transacted_at`.** Statements and daily limits group on the banking
date in the bank's timezone. A deposit at 23:55 on the 31st belongs to that month; grouping on a
UTC instant moves it for any customer east of Greenwich.

**Money is `DECIMAL(19,4)` and `BigDecimal` everywhere.** Four stored places for interest accrual,
two presented, rounded once at the mapper boundary. Jackson emits it as a plain string so a
JavaScript client can't parse it into a double.

**`schema.sql` owns the schema** (`ddl-auto: none` + `sql.init.mode: always`). Script init runs
*before* Hibernate's DDL by default, so with `ddl-auto: update` your `data.sql` inserts into tables
that don't exist yet. Both scripts are idempotent; prod sets `mode: never`.

**The `date_of_birth` CHECK is a static floor** (`>= '1890-01-01'`), not `<= CURRENT_DATE`. MySQL
rejects non-deterministic functions in CHECK constraints — ERROR 3814 — and the migration would
never apply. The future-date rule lives in `@Past` and `CustomerValidator`.

**Luhn check digits on account numbers.** 99/99 single-digit typos caught, all adjacent
transpositions except 09↔90, which is a documented property of the algorithm and pinned in a test
so nobody assumes it's exhaustive.

## Security

Roles stored **without** the `ROLE_` prefix (`hasRole()` adds it; storing it gives
`ROLE_ROLE_BANK_EMPLOYEE` and every check fails as though the role was never granted).
`requireSelfOrStaff` throws **not-found, not forbidden** — a 403/404 split enumerates the customer
base. Login failures are indistinguishable and the hash is computed even for unknown usernames, so
timing doesn't leak either. Lockout is time-limited. Logout actually revokes. Cash deposit and
withdrawal are staff-only — cash arrives at a counter, and a customer crediting their own account
from a session breaks the correspondence between balances and money the bank holds.

## Reconciliation

`findUnreconciledAccounts` compares each stored balance against the signed sum of its own ledger
rows; `findUnbalancedTransfers` finds any reference without exactly two halves. Both should always
return nothing — which is why they're worth running. A cached balance with no cross-check drifts
silently, and the first symptom is a customer disputing a statement.

The seed data satisfies both: opening deposits are written as real ledger rows, so a fresh database
reconciles cleanly rather than reporting itself broken on the first request.

## Not here

- **No integration tests.** Testcontainers is wired in but unused. Since the balance rules rest on
  real MySQL row locking, that's the most valuable next commit — the concurrency behaviour is
  verified by simulation, not against the database.
- **`idempotencyKey` is accepted and not yet honoured.** The field is on `TransferRequest`; the
  dedupe store isn't built. Flagged rather than quietly dropped.
- **Interest accrual has no scheduler.** `INTEREST_CREDIT` and `findInterestBearing()` exist.
- **`TokenBlacklist` is in-memory** — single node only.
- **Customer search is `LIKE '%…%'`** — cannot use an index.

## On verification

Maven Central is unreachable from the environment this was generated in, so **the code has never
been compiled**. Checked structurally: POM and all three YAML profiles parse, packages match
directories, braces balance, every internal reference resolves, all 5 service interfaces have an
implementation, all 24 spec endpoints are mapped, every file in your tree exists. The two-row
ledger, all three concurrency failures, the beneficiary rules, the statement arithmetic, Luhn, and
the seeded bcrypt hash were each verified in Python first.

Expect to fix a few things on the first `mvn clean install`.
