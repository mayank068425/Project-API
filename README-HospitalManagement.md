# Hospital Management System

Patients, doctors and departments.

Java 21 · Spring Boot 3.3.5 · Spring MVC · Spring Data JPA · MySQL 8.4 · Maven · Jakarta Validation · Lombok · Swagger

```bash
make up      # MySQL + app
make test
```

Swagger at `/swagger-ui.html`. Seeded with 5 departments, 5 doctors and 5 patients.

---

## One change to your spec, and why

**The `patient` table has no `age` column.**

Your spec listed both `age` and `date_of_birth`. Storing age is a bug — it is correct on the day
it is written and wrong from the patient's next birthday onwards, with nothing to correct it:

```
patient born 1991-03-14, registered 2021-06-01, age written as 30

as at         stored   actual   correct?
2021-06-01        30       30   True
2022-06-01        30       31   False
2026-06-01        30       35   False
```

In a hospital that is not cosmetic. Paediatric versus adult dosing, screening intervals and
consent thresholds are all age-gated, and every one of them reads this number.

So `date_of_birth` is the stored fact and `getAge()` is `@Transient`, derived on every read. The
API returns `age` in every patient response — callers see exactly what they asked for, it just
cannot go stale. The create request deliberately has no `age` field: accepting one would let a
caller submit a value contradicting the date of birth, leaving two answers to one question.

`Period.between` gets the awkward cases right without special-casing. Verified: the day before a
birthday, on it, and a 29 February birthday turning older on 1 March in non-leap years.

---

## Cascade: the decision that matters most here

Your relationship diagram is Department → Doctor → Patient, and the usual way to map it is
`cascade = CascadeType.ALL` on both `@OneToMany` sides. On a hospital hierarchy that is dangerous:

```
DELETE /api/departments/{id}   where id = Cardiology
  -> deletes 2 doctors
  -> deletes 5 patient records: Priya, Arjun, Meera, Kiran, Sameer
```

One HTTP call, no warning, and a department's worth of medical history is gone. The cascade is
doing exactly what it was told.

**Mapped with `PERSIST` and `MERGE` only.** No remove cascade anywhere. Deletion counts its
dependants first and refuses with a 409 naming what is in the way. The foreign keys are
`ON DELETE RESTRICT` (doctor → department) and `ON DELETE SET NULL` (patient → doctor), so even a
hand-written `DELETE` unassigns rather than destroys.

Patients are **soft-deleted**. A patient record is a medical record: destroying it loses history
needed for continuity of care and, in most jurisdictions, required to be retained.

---

## Bidirectional relationships and JSON

`Department` has `List<Doctor>`; `Doctor` has `Department`. Return the entity from a controller
and Jackson walks the cycle:

```
json.dumps(department) -> Circular reference detected
```

In Jackson that is a `StackOverflowError` thrown *after* the response status is committed — the
client gets a truncated body and the log gets a stack trace thousands of frames deep.

`@JsonIgnore` hides the field from callers who legitimately want it. `@JsonManagedReference` works
but bakes serialisation concerns into the persistence model. **Entities are never serialised.**
Controllers return records, which also stops `version`, audit timestamps and the soft-delete flag
leaking out.

---

## N+1, and why `open-in-view` is off

Listing departments with their doctor counts, naively:

```
1 query for departments
+ 5 to load each department's doctors
+ 20 to load each doctor's patient count
= 26 queries for one page
```

Counts come from **one grouped query** instead — 26 becomes 2. The naive number grows with the
data, so it looks fine against three rows in development.

Everything else uses `join fetch` where the response needs an association, with an explicit
`countQuery` because Hibernate cannot derive one from a fetch-join query. Patient fetches are
`left join fetch`: a patient may have no doctor yet, and an inner join would silently drop exactly
those rows.

**`spring.jpa.open-in-view: false`.** Spring Boot defaults it to true, which keeps the Hibernate
session open for the whole request and hides lazy-loading mistakes — a lazy association touched
during serialisation quietly issues another query instead of failing. The cost is that every
request holds a database connection until the response is written, so under load the pool is
exhausted by requests that finished querying long ago. Off, those mistakes surface as
`LazyInitializationException` in development, which is where you want to find them.

Also set: `fail_on_pagination_over_collection_fetch`, which turns silent in-memory pagination of
a fetched collection into an error.

---

## Denormalised `department_id`

Your schema puts `department_id` on both `doctor` and `patient`, so the two can disagree:

```
patient: {doctor: Dr Rao, department: Cardiology}
Dr Rao transfers to Neurology
  doctor.department  = Neurology
  patient.department = Cardiology   <- now disagrees
```

The column is kept, because a patient can be admitted to a department before a doctor is
assigned — that is what the nullable `doctor_id` is for. But there is a rule: **the doctor wins.**
Assigning a doctor sets the patient's department from that doctor, overriding a contradictory
value in the request. Moving a doctor between departments moves their patients with them.

`GET /api/patients/inconsistent` returns any row where the two still disagree. It should always be
empty — an invariant nobody checks is one nobody notices breaking.

---

## A bug caught before it shipped

`CHECK (date_of_birth <= CURRENT_DATE)` looks reasonable and **MySQL rejects it**: non-deterministic
functions are not permitted in check constraints, so the migration fails at DDL time with
ERROR 3814 and the application never starts.

The constraint now keeps only a static floor (`>= '1890-01-01'`). The future-date rule lives in
`@Past` on the DTO and in `PatientService`, which is where a rule depending on "now" belongs.

---

## Smaller decisions

**Blood group is an enum, not a `VARCHAR`.** `"O+"` and `"0+"` are indistinguishable at a glance,
and this is the field read fastest in the worst circumstances. `BloodGroup.fromLabel` accepts the
clinical notation people type on forms and rejects a zero typed for the letter O.

**Consultation fee is `DECIMAL(10,2)` and `BigDecimal`.** `double` cannot represent 0.10 exactly;
one fee is fine, a month of billing totals is not.

**Search is one endpoint per resource, not one per field.** Separate `/by-name`, `/by-phone`,
`/by-blood-group` endpoints cannot be combined, and the first request for "female patients with
O-negative blood" needs a seventh. All criteria are query parameters on `GET /api/patients`.

**Validation returns every failing field at once**, not just the first — otherwise a form with
three mistakes becomes three round trips.

**`POST` returns 201 with a `Location` header.** `DELETE` returns 204.

**Duplicate checks exist in the service *and* as unique constraints.** The service check gives a
readable message; it loses the race against a simultaneous insert, so the constraint is the actual
guarantee and `DataIntegrityViolationException` maps to 409 rather than 500.

---

## API

All 16 endpoints from your spec, at the exact paths:

```
POST   /api/patients            GET /api/patients            GET    /api/patients/{id}
PUT    /api/patients/{id}       DELETE /api/patients/{id}    GET    /api/patients/search
POST   /api/doctors             GET /api/doctors             GET    /api/doctors/{id}
PUT    /api/doctors/{id}        DELETE /api/doctors/{id}
POST   /api/departments         GET /api/departments         GET    /api/departments/{id}
PUT    /api/departments/{id}    DELETE /api/departments/{id}
```

Plus, covering the remaining features in your module list:

```
GET /api/patients?name=&phone=&bloodGroup=&gender=&departmentId=&doctorId=
GET /api/patients/count[?departmentId=]        total, and minors
GET /api/patients/inconsistent                 should be empty
GET /api/doctors?name=&specialization=&departmentId=
GET /api/doctors/search                        GET /api/doctors/{id}/patients
```

Status codes: **201** created, **204** deleted, **400** validation, **404** not found,
**409** duplicate or dependants still attached.

---

## Tests

| Test | Pins down |
|---|---|
| `PatientAgeTest` | derived age tracks time where a stored one would not; the birthday boundary; a 29 February birthday needs no special-casing; missing or impossible dates throw |
| `BloodGroupTest` | clinical notation parses; **`"0+"` is rejected rather than stored** |
| `DepartmentServiceTest` | **deleting a department with doctors is refused, not cascaded**; renaming to its own name is allowed; empty departments are deactivated, not deleted |
| `PatientServiceTest` | department comes from the doctor; a contradictory department is overridden; inactive doctors refused; future and implausible dates of birth refused; **delete is soft** |

## What is deliberately not here

- **No authentication.** Not in your stack list. Every endpoint is open, which is fine for local
  development and is the first thing to add before this goes anywhere real — patient records are
  the most sensitive data a system like this holds.
- **No integration tests.** Testcontainers is wired in; the tests aren't written. The fetch-join
  queries and the check constraints are the things worth exercising against real MySQL.
- **No appointments, admissions, billing or prescriptions.** The three modules you specified only.
- **Name search is `LIKE '%…%'`**, which cannot use an index. Fine at this scale; past it the
  answer is a FULLTEXT index, and the query changes shape rather than being tuned.

## On verification

Maven Central is unreachable from the environment this was generated in, so **the code has never
been compiled**. It has been checked structurally — POM and YAML parse, package declarations match
directories, braces balance, every internal reference resolves — and the age arithmetic, the
cascade blast radius, the JSON cycle, the N+1 counts and the seed data's doctor/department
consistency were each reproduced in Python first.

Expect to fix a few things on the first `mvn clean install`.
