# DB Module Fix Priorities

This is the execution order for `freeway-db`, derived from the audit report.

## 1. Must Fix First

These are correctness problems or contract violations. Leaving them in place will keep producing wrong behavior.

### 1.1 Make SQL named-parameter parsing real

Current risk:
- `NamedParamParser` uses a regex and can misread SQL literals, comments, and dialect-specific syntax.

Fix:
- replace regex parsing with a small SQL scanner that tracks quote/comment state
- preserve existing `#name` and `:name` syntax if you want, but only outside literals/comments

Why first:
- every query path depends on this
- all higher-level DB features inherit the bug

### 1.2 Make parameter binding strict

Current risk:
- missing named parameters become `null`
- extra positional parameters can be dropped silently
- mixed binding styles are not clearly rejected

Fix:
- validate placeholder count before execution
- reject missing names and extra values
- define a clear rule for mixing named and positional styles

Why first:
- silent binding failure is worse than a thrown exception
- this is the kind of bug that corrupts data without making noise

### 1.3 Fix timeout truncation

Current risk:
- `Duration` values are converted with `toSeconds()`
- sub-second values become `0`

Fix:
- convert with millisecond precision at the boundary
- if a JDBC API only accepts seconds, round up explicitly and document it

Why first:
- it makes a public API lie about its behavior
- it affects both connection health checks and query timeouts

## 2. Should Fix Next

These are design problems that can hide failures or make the module harder to reason about.

### 2.1 Simplify connection-pool semantics

Current risk:
- the semaphore and `total` counter split responsibility in a confusing way
- shutdown uses a yield loop and depends on borrowers returning connections in time

Fix:
- define one explicit meaning for `maxSize`
- make the pool’s accounting model match that meaning
- replace busy-wait shutdown with a deterministic close path

Why next:
- this directly affects reliability under load and shutdown behavior

### 2.2 Stop silent datasource skipping

Current risk:
- declared datasources with missing config are skipped with only a warning

Fix:
- fail fast when a declared datasource is incomplete
- if partial startup is allowed, make it an explicit opt-in mode

Why next:
- framework startup should not quietly ignore broken configuration

### 2.3 Tighten migration guarantees

Current risk:
- migration safety claims are stronger than the code can guarantee
- missing or blank migration files can disappear without a hard failure

Fix:
- add a checksum or content fingerprint
- fail on missing declared migration files
- document transactionality limits per database engine

Why next:
- migrations are one of the most failure-sensitive parts of the DB module

## 3. Should Fix After That

These issues are architectural or API-shape problems. They matter, but they do not need to block correctness fixes.

### 3.1 Split `DbModule`

Current risk:
- one class handles config, startup, named datasources, ping, and migrations

Fix:
- keep `DbModule` as a composition root
- move startup logic into smaller helpers
- keep policy and wiring separate

Why here:
- improves testability and clarity after the functional bugs are removed

### 3.2 Remove internal-type leakage from public API

Current risk:
- `DatabaseBuilder` exposes `DefaultRowMapper` in its public surface

Fix:
- type the builder against `RowMapper`
- keep implementation classes internal or package-private

Why here:
- this protects future refactoring and keeps the public surface honest

### 3.3 Align transaction docs with the actual API

Current risk:
- `Transaction` Javadoc refers to a type that no longer exists in the public API

Fix:
- update the docs to match current method signatures
- state clearly how manual transactions should be closed

Why here:
- this is low-cost cleanup, but it should follow the behavioral fixes above

## 4. Low Priority / Optional Hardening

These are not urgent, but they will improve framework quality over time.

### 4.1 Make row mapping stricter

Optional hardening:
- add a strict mode that fails on missing or ambiguous columns
- keep permissive fallback only when the caller opts in

### 4.2 Make batch binding stricter

Optional hardening:
- validate row shape consistently
- reject incomplete rows early
- decide whether `null` insertion should be explicit or implicit

### 4.3 Add more failure-path tests

Test cases to add:
- SQL literals and comments containing `:name`
- missing named parameters
- extra positional parameters
- mixed binding styles
- sub-second timeouts
- incomplete named datasource config
- missing migration files
- concurrent migration startup

## Practical Implementation Order

If this were being fixed in code, I would do it in this order:

1. parser and binding
2. timeout semantics
3. pool semantics
4. migration hardening
5. module split and API cleanup
6. stricter tests

That sequence minimizes the chance of building new logic on top of broken assumptions.
