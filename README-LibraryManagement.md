# Library Management System

Built to the package structure you supplied. `com.example.lms`, service interfaces split from
`serviceimpl`, `dto.request`/`dto.response`, a separate `BookReturn` entity, `Role` as a table,
`schema.sql`/`data.sql`, and Spring Security's own `UserDetails` in the `security` package.

Java 21 · Spring Boot 3.3.5 · Spring MVC · Spring Security + JWT · Spring Data JPA · Spring JDBC · MySQL 8 · Maven · Swagger · Lombok · JUnit 5

```bash
mvn spring-boot:run          # dev profile, expects MySQL on localhost:3306
```

Logins: `admin`, `librarian`, `priya`, `arjun`, `meera` — all `Password@123`.
Swagger at `/swagger-ui.html`, grouped into catalogue / circulation / membership / reporting.

**124 Java files.** Everything in your tree exists, plus a small number of additions noted at the
bottom.

---

## Three things about your structure that needed a decision

### 1. `schema.sql` + `data.sql` has an ordering trap

Spring Boot runs script-based initialisation **before** Hibernate's DDL by default. Leave
`ddl-auto` on `update` and `data.sql` inserts into tables that do not exist yet — the application
fails at every start-up with *"Table 'library.book' doesn't exist"*.

Two settings fix it and they have to agree. The choice made here:

```yaml
spring.jpa.hibernate.ddl-auto: none    # schema.sql owns the schema
spring.sql.init.mode: always
```

The alternative is letting Hibernate build the schema and setting
`spring.jpa.defer-datasource-initialization: true` so `data.sql` runs after. Mixing the two owners
gives a schema that differs between a fresh database and an existing one, which is the version of
this that takes a day to find.

Owning the DDL in SQL also means the check constraints, index names and `ON DELETE` rules survive
rather than being whatever Hibernate infers. Both scripts are idempotent (`CREATE TABLE IF NOT
EXISTS`, `INSERT IGNORE`, `NOT EXISTS` guards), so a restart against a populated database is a
no-op. `application-prod.yml` sets `mode: never` — running DDL at start-up means every instance in
a rolling deploy races to alter the same tables.

### 2. Splitting `BookIssue` and `BookReturn` creates one invariant

A separate return entity is a real modelling choice, not a duplicate: the return is an event with
its own actor, timestamp and condition, rather than three nullable columns on the loan.

It creates exactly one thing that must be enforced or the data lies:

```
ISS-001: 1 return row  -> ok
ISS-002: 2 return rows -> IMPOSSIBLE: returned twice
ISS-003: 0 return rows -> still out
```

Two rows means a double-click at the desk raised the copy count twice and, with a fine involved,
charged the member twice. So: **`UNIQUE` on `book_return.issue_id`** as the guarantee, plus an
`existsByIssueId` check in the service so the second click gets a readable message instead of a
raw constraint violation. `/api/reports/reconciliation` queries for rows that could only exist if
that constraint were ever dropped by a migration.

### 3. Three pairs in your tree do the same job

| Pair | Resolution |
|---|---|
| `security/JwtFilter` + `config/JwtAuthenticationFilter` | `JwtFilter` **is** the filter and is the only one registered. Two `OncePerRequestFilter` beans would authenticate twice per request — harmless for a valid token, confusing when debugging a rejected one. `JwtAuthenticationFilter` is a `@Configuration` that hands that single bean to `SecurityConfig`. |
| `config/OpenApiConfig` + `config/SwaggerConfig` | Genuinely different. `OpenApiConfig` defines the document (title, version, bearer scheme); `SwaggerConfig` defines the UI groupings. |
| `security/JwtTokenProvider` + `util/JwtUtil` | `JwtTokenProvider` holds the signing key and is the only thing that creates or verifies a token. `JwtUtil` reads claims from an *already-verified* `Claims` and has no key — so nothing outside the provider can accept an unverified token. |

All eight files exist. Each says in its javadoc which role it plays.

---

## Fines: the arithmetic, verified

Your worked example — due the 10th, returned the 15th, $2/day — gives exactly $10.00. The
interesting parts are around it.

**Off by one.** Returning *on* the due date is not late. `>=` instead of `>`, or a missing
`max(0, …)`, fines every punctual member by one day.

**The timezone.** This is the subtle one:

```
member returns at 2026-07-11 01:30 IST
  evaluated in IST -> date 2026-07-11, 1 day late, $2.00
  evaluated in UTC -> date 2026-07-10, on time,    $0.00
```

A due date is a calendar date; a return is an instant. The two zones disagree for **5½ hours of
every 24**. So `DateUtil.toLocalDate(Instant, ZoneId)` is the only conversion in the codebase and
it **has no overload that defaults the zone** — calling it without deciding is impossible by
construction. `BookReturn` stores both `returnedAt` (instant) and `returnedOn` (library-local
date), so a later configuration change cannot silently restate whether someone was late years ago.

**The cap.** 60 days on a $25 book calculates to $120 and charges $25. Past the replacement cost
it is a lost-item charge, not a late fee. `Fine` stores `daysOverdue`, `ratePerDay`,
`calculatedAmount` **and** `amount`, because the question a member asks is not "how much" but
"why".

## Availability and the reservation queue

`available_copies` is derived and stored — "show me available books" is the most frequent query
and counting loans each time does not scale. Two things guard it:

- **Pessimistic locks** on book and member, always **book first then member** (opposite orders
  deadlock). Without them two concurrent issues of the last copy both pass the check and both
  decrement.
- **A reconciliation report**, surfaced on the admin dashboard. A counter with no cross-check
  drifts silently and the first symptom is a member sent to an empty shelf.

The queue: shelving a returned copy means the next walk-in takes it and **the queue never drains**.
So a return with a non-empty queue moves the copy into `heldCopies` and marks the head `READY`.
Holds expire and **pass down the queue**, falling back to general availability only when the queue
is empty — and lapsed holds are detected on the next issue attempt too, so correctness never
depends on the cron job having run.

## ISBN check digits

`BookValidator` verifies them, and it's worth knowing what that buys: **117/117 single-digit typos
caught, 10 of 11 adjacent transpositions.** The ISBN-13 scheme provably misses transposed pairs
differing by 5. The DTO regex confirms the shape; this confirms the number could have been issued.

---

## Security notes

- **`Role` is stored without the `ROLE_` prefix.** `hasRole("LIBRARIAN")` adds it before
  comparing, so storing it yields `ROLE_ROLE_LIBRARIAN` and every check fails in a way that looks
  like the role was never granted. The prefix is added in exactly one place, `Role.authority()`.
- **`User.roles` is `EAGER`** — the one justified case. Roles are needed on every request outside
  any application-opened transaction; lazy here forces `open-in-view` back on, trading one bug for
  a connection leak.
- **`CustomUserDetails` copies everything out at construction** and never holds the entity, so it
  cannot trigger lazy loads from wherever it happens to be read.
- **`SecurityUtils.requireSelfOrStaff` throws not-found, not forbidden.** A 403/404 split lets
  someone enumerate the membership by iterating ids.
- **Logout actually revokes.** A JWT is valid until expiry by design.
- **Login failures are indistinguishable** — unknown user, wrong password, locked account all
  return the same message, and the hash is computed even for unknown users so timing doesn't leak
  either. Lockout is time-limited; permanent would be a denial of service against the real member.
- **Reset tokens are stored hashed and are single-use.**
- **Self-registration always yields `MEMBER`.** An endpoint taking a role from the body grants
  librarian rights to anyone who reads the docs.

## Reports (Spring JDBC)

Every one is an aggregate over a join. Two worth flagging:

- `activeMembers` uses a **correlated** fines subquery. Joining fines alongside loans multiplies
  the loan count by the number of fine rows — fan-out that inflates every figure.
- `monthlyIssues` and `monthlyReturns` are **separate**. Grouping issues by `issue_date` while
  counting returns buckets returns into the month the book went *out*. Looks plausible, is wrong.
  The separate `book_return` table makes the correct version straightforward.

---

## What I added beyond your tree

Small and each for a reason: `LibraryPolicy` (borrowing rules from config, not constants);
`BusinessRuleException` (422 vs the 409s); `SecurityUtils` and `TokenBlacklist`; `AuditService` +
`AuditLogRepository` (your entity list has `AuditLog` but no repository or service);
`FineCalculator`; `PageResponse` and the response records for return/fine/reservation/dashboard;
the enums; `CirculationScheduler`; `IssueMapper`.

## Tests

`src/test/java/com/example/lms/{controller,service,repository,security,validation}` — 7 classes.

`RepositoryQueryContractTest` is the unusual one: it reflects over the repositories to assert that
every paginated fetch-join query has an explicit `countQuery` (Hibernate cannot derive one, and it
throws at first call), and that `lockById` on book and member still carry `@Lock`. Both are
regressions a later refactor introduces silently.

## Not here

- **No integration tests.** Testcontainers is wired in, tests aren't written. Since the copy
  counters rest on real MySQL row locking, that is the most valuable next commit — the concurrency
  behaviour is verified by simulation, not against the database.
- **No notifications.** `Reservation.notified` exists and nothing sends anything.
- **No renewals endpoint.** `renewalCount` and `maxRenewals` exist unused.
- **Search is `LIKE '%…%'`** — cannot use an index. Fine at this scale; past it the answer is a
  FULLTEXT index.
- **`TokenBlacklist` is in-memory** — single node only. Multi-instance means Redis, and the
  interface is small enough that it touches one class.

## On verification

Maven Central is unreachable from the environment this was generated in, so **the code has never
been compiled**. Checked structurally: POM and all three YAML profiles parse, package declarations
match directories, braces balance, every internal reference resolves, all 12 service interfaces
have an implementation, and every file in your specified tree exists. The fine arithmetic, the
timezone boundary, the ISBN check digits, the seeded bcrypt hash and the seed data's copy-count
invariant were each verified in Python first.

Expect to fix a few things on the first `mvn clean install`.
