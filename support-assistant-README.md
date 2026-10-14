# AI-Powered Production Support Assistant

Rebuilt to the same enterprise structure as the banking, hospital and library projects —
`com.supportai`, service interfaces split from `serviceimpl`, `dto.request`/`dto.response`,
`schema.sql`/`data.sql`, three YAML profiles, four test packages.

Java 21 · Spring Boot 3.5.6 · **Spring AI 1.1.5** · PostgreSQL 16 + pgvector · Maven · Swagger

```bash
mvn spring-boot:run     # assistant DISABLED by default; everything else works
```

Logins: `admin`, `lead`, `arjun`, `viewer` — all `Password@123`. Swagger at `/swagger-ui.html`.
**104 Java files.**

---

## What changed in the restructure

The original was organised **by feature** — `incident/`, `knowledge/`, `assistant/`, `retrieval/`,
`iam/`, `audit/`, each with its own domain/service/controller inside. That is a legitimate layout
and it is not the one you asked for. Two things needed deciding.

**The verifiers went into `validation/`.** `CitationVerifier`, `RcaVerifier` and `Redactor` check
that something is fit to be used before it is used, which is what a validator is. Putting them
there rather than in an `ai/` package is a claim about how they should be treated: **model output
is untrusted input and goes through validation like any other untrusted input.** A separate AI
package invites the idea that these are optional plumbing rather than the checks that make the
feature shippable.

**Retrieval got its own package.** Chunking and rank fusion are neither a service in the
orchestration sense nor a repository in the persistence sense — they are an algorithmic layer with
its own contract. Folding them into `serviceimpl` would bury the two most important decisions in
the system inside classes named after use cases. Same reasoning that put `jdbc/` beside the
services in the library project.

Everything else maps cleanly onto the structure.

---

## The rule the whole system implements

**Every AI output is verified against what the model was actually shown, and anything unverifiable
is withheld rather than caveated.**

A caveat above an answer containing a subtly wrong command does not stop anyone running it —
during an incident, least of all. So:

| Feature | Guard |
|---|---|
| Documentation Q&A | Citations checked against retrieved chunks, quoted spans checked verbatim. A failure **withholds the whole answer** |
| RCA generation | Refused below 3 real timeline entries. Uncited "observations" downgraded to inferences; zero observations forces `INSUFFICIENT_EVIDENCE` |
| Precedents | Led by the past incident's *root cause*, never its fix alone. Ranked arithmetically |
| Customer emails | Redacted **after** generation; commitments flagged, not deleted |
| Summarisation | Stored beside the original, read by nothing else |

This shows up in the API as a status split that matters: `INSUFFICIENT_CONTEXT` and
`OUTPUT_REJECTED` are **422 and not retryable** (the system worked correctly and declined);
`PROVIDER_UNAVAILABLE` is **503 and retryable**. Collapsing them would have clients retrying a
refusal forever.

**The assistant is off by default.** Incidents, hybrid search, the dashboard and audit all work
without it. A support tool that dies when a model provider has an outage is a liability during
exactly the incidents it exists for.

---

## A correction to the previous README

The old version claimed that weighting the lexical ranker 2× **did not** fix the ordering problem.
I re-ran it while rebuilding and **that claim is wrong** — with the rank positions I used, 2× does
promote the exact match:

```
lexical ×1.0: B > A      (conceptually-related doc wins)
lexical ×2.0: A > B      (exact-token match wins)
```

The original measurement must have used different rank positions. The honest version of the finding
is narrower and still holds:

- **`k` genuinely does not reorder anything.** k of 5, 10, 20 and 60 gave identical ordering; both
  documents shift proportionally (the gap narrows from 5.8% to 0.8%, but the winner never changes).
- **Weighting the lexical ranker up does work — and it promotes *every* full-text hit**, which
  costs the semantic recall the hybrid search exists to preserve.
- **A separate additive literal-match signal is the better lever.** +0.02 is roughly one rank
  position at k=60 (one position ≈ 0.00026), so it breaks a near-tie in favour of the verbatim
  match without letting an irrelevant chunk climb.

**Fusion is on rank, never score.** Cosine runs 0–1 and `ts_rank_cd` runs 0–0.1. Worked example:
cosine 0.71/0.82 and ts_rank 0.089/0.012 sum to 0.799 and 0.832 — the document the lexical ranker
ranked first loses purely to the scale difference. Ranks are unitless.

---

## Chunking, and the bug it surfaced

Sliding a fixed window over a runbook produces chunks that retrieve beautifully and are dangerous
to act on. The one that motivated `StructureAwareChunker`:

```
: `kubectl rollout restart deploy/payments`.
7. Verify pool metrics recover within 3 minutes.
### 3.3 Escalation
```

No heading, no procedure, and **no step 3** — the one saying *do not restart before completing
step 4, restarting first loses the in-flight transaction log*. Top hit for "how do I restart the
payments pods". Invisible at ingestion, surfaces mid-incident.

Building the fix surfaced a subtler problem, and I hit it again during this rebuild:
`6. Restart the payments pods` matches every reasonable numbered-**heading** pattern — capital
letter, no terminal punctuation. A naive detector promotes step 6 to a heading and splits the
procedure at the worst possible point.

In isolation a numbered heading and a numbered step are genuinely indistinguishable. **The signal
is in the neighbours**: steps come in runs of consecutive incrementing numbers. Two-pass detection
finds the runs first, so `6. Restart the payments pods` stays a step while `4. Cache Eviction
Storms` stays a heading. Verified in Python before the Java was written — and the first Java
version had a broken helper (`firstNumberAt` returned 0 unconditionally) that I caught and replaced
with a proper `RunState`.

---

## Other decisions worth knowing

**Clearance is applied inside every retrieval query, not afterwards.** A post-filter lets a
RESTRICTED chunk occupy a top-k slot and then vanish — so a user without clearance silently gets
fewer sources than one with it, with no indication anything was removed. Their answer is quietly
worse, and retrieval is the least obvious place anyone looks.

**No `QuestionAnswerAdvisor`.** Spring AI's stock RAG advisor is convenient and removes the two
things this app needs most: scoping retrieval to the caller's clearance, and knowing which chunks
were used so citations can be checked.

**Superseding a document deletes its chunks.** Leaving them is the quiet failure of every RAG
system shipped without thinking about updates — old chunks keep matching, keep scoring well, and
keep carrying a citation that resolves to a real document.

**`embedding_model` and `embedding_dimension` per document.** Vectors from different models aren't
comparable; a swap without re-indexing degrades silently rather than failing.

**Assistant-authored timeline entries are excluded from RCA evidence.** An analysis treating its
own prior output as observation compounds until nothing traces back to what happened. The seed data
includes one ASSISTANT entry so the exclusion is demonstrable on a fresh database.

**Blameless RCA is enforced, not requested.** A root cause naming a person is withheld and
confidence dropped. Blame changes what people write on timelines, and the timeline is the only
evidence this feature runs on.

**`MITIGATED` is separate from `RESOLVED`.** Customer impact stops well before the fault is fixed.

**Audit stores a SHA-256 of the prompt, not the prompt.** Incident text contains customer data.
What it stores instead — retrieved chunk ids, evidence refs, verification outcome — is what tells
you weeks later whether a wrong answer came from bad retrieval or bad generation.

**Median, mean and p90 together.** One incident left open over a weekend drags the mean far above
anything typical. Empty samples report **null, not zero**: zero reads as "we acknowledge instantly".

---

## Version and infrastructure traps

**Spring AI 2.0.x tracks Spring Boot 4 and will not resolve against a Boot 3.x parent.** This pins
1.1.5. Artifact names also changed at 1.0: `spring-ai-starter-model-openai`, not the older
`spring-ai-openai-spring-boot-starter` most blog posts still show.

**PostgreSQL, not MySQL** — unlike the other three projects. This needs pgvector *and*
`ts_rank_cd` in the same query so one clearance filter covers both. The image must be
`pgvector/pgvector`; `CREATE EXTENSION vector` won't find it on a stock postgres image.

**`AI_EMBEDDING_DIMENSIONS` must match both the model and the `vector(768)` column** — 768 for
`nomic-embed-text`, 1536 for `text-embedding-3-small`. A mismatch fails at insert, which is the
good outcome.

**HNSW rather than IVFFlat** for the vector index: it needs no training pass, so it works on an
empty table and stays correct as documents arrive one at a time. `vector_cosine_ops` must match
the `<=>` operator in the query or the index is silently unused.

## Not here

- **No integration tests.** Testcontainers is wired in, unused. Retrieval correctness rests on real
  pgvector and real full-text ranking, so that is the most valuable next commit.
- **No file upload.** Documents are posted as JSON text. Parsing PDF and DOCX is a separate problem.
- **No conversational memory.** Every call is one-shot; multi-turn RAG needs query rewriting.
- **No reranker model.** A cross-encoder would likely beat this fusion; it is another model to host.
- **`TokenBlacklist` is in-memory** — single node only.
- **`AssistantHealth.callsLast7Days` is wired to zero** in the dashboard. The rate is computed; the
  raw counts aren't plumbed through. Flagged rather than quietly left looking correct.

## On verification

Maven Central is unreachable from this environment, so **the code has never been compiled**.
Checked structurally: POM and all three YAML profiles parse, packages match directories, braces
balance, every internal reference resolves, all 6 service interfaces have an implementation, and
every file in the specified structure exists. The rank-fusion behaviour, the step-run detector, the
redaction rules and the seeded bcrypt hash were each verified in Python before the Java was
written — including the correction above, which came from re-running the fusion measurement rather
than trusting the earlier claim.

The Spring AI API surface is the part most likely to need adjustment on first build.
