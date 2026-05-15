# DB Module Audit Report

Scope: `freeway-db`

Assessment date: 2026-05-14

## Summary

The DB module already has a usable shape: builder, pool, transaction, row mapping, and migration support are all present. The main problems are not missing features, but weak contract boundaries, lenient failure handling, and a few concrete correctness risks.

From a first-principles perspective, the module should optimize for three things:

1. predictable SQL binding
2. explicit resource ownership
3. diagnosable failures

The current implementation is close, but it still violates those goals in several places.

## High Priority Findings

### 1. Named-parameter parsing is regex-only and will misread SQL literals/comments

`NamedParamParser` matches `:name` and `#name` with a raw regex, then rewrites the whole SQL string. It does not understand quoted strings, comments, or dialect-specific syntax. That means a colon inside a string literal or comment can be treated as a bind parameter.

Evidence:
- [`NamedParamParser.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/NamedParamParser.java:13-36)

Impact:
- incorrect SQL rewriting
- false parameter detection
- hard-to-debug failures when SQL contains text literals or vendor syntax

Recommendation:
- replace the regex pass with a small SQL lexer that tracks string/comment state
- at minimum, ignore content inside single quotes, double quotes, line comments, and block comments
- add tests for literals, comments, PostgreSQL casts, and edge cases like `':name'`

### 2. Parameter binding can silently accept mismatches

`QueryImpl` expands positional and named parameters without enforcing a strict placeholder-to-argument contract. Missing named keys are bound as `null`, and extra positional arguments are not rejected. That makes bad calls look valid until the database returns a confusing result.

Evidence:
- [`QueryImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/QueryImpl.java:171-200)
- [`QueryImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/QueryImpl.java:216-240)
- [`BatchQueryImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/BatchQueryImpl.java:49-87)

Impact:
- silent data loss
- accidental `NULL` writes
- query bugs that surface far away from the caller

Recommendation:
- fail fast when placeholder count and supplied values do not match
- fail fast when a named parameter is missing
- forbid mixing positional and named styles in the same statement unless the syntax explicitly supports it
- add tests for extra params, missing params, mixed styles, and empty collection expansion

### 3. Timeout values are truncated to whole seconds

`DatabaseBuilder` and `ConnectionPool` convert `Duration` to `int` seconds using `toSeconds()`. Any sub-second timeout becomes `0`, and larger values lose precision. That is a real behavior bug, not just an implementation detail.

Evidence:
- [`DatabaseBuilder.java`](freeway-db/src/main/java/com/jujin/freeway/db/DatabaseBuilder.java:193-205)
- [`ConnectionPool.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java:216-263)

Impact:
- `isValid(0)` and `setQueryTimeout(0)` behave differently across drivers
- sub-second health checks and query timeouts are effectively unsupported even though the API accepts `Duration`

Recommendation:
- define timeout semantics in milliseconds, not truncated seconds
- validate and convert once at the boundary
- if the JDBC API requires seconds, explicitly round up instead of truncating

### 4. Connection-pool semantics are conceptually inconsistent

The pool uses both a semaphore and a `total` counter, but permits are released when connections are returned to idle. That means the semaphore is controlling concurrent borrowers, while `total` is controlling physical connection creation. The comments imply a single max-size story, but the implementation is split across two mechanisms.

Evidence:
- [`ConnectionPool.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java:89-110)
- [`ConnectionPool.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/ConnectionPool.java:190-199)

Impact:
- pool sizing is harder to reason about
- shutdown behavior depends on active borrowers returning in time
- stats and pool limits can become confusing under load

Recommendation:
- decide whether `maxSize` means total physical connections or concurrent leases
- encode that choice in one mechanism, not two
- make shutdown deterministic instead of busy-waiting on `Thread.yield()`

### 5. Migration safety is overstated

`MigrationRunner` says multi-instance safety is guaranteed by the unique `version` column. That only protects the bookkeeping row. It does not guarantee safety for non-transactional DDL or driver-specific auto-commit behavior. It also silently skips blank or missing migration files.

Evidence:
- [`MigrationRunner.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/MigrationRunner.java:16-29)
- [`MigrationRunner.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/MigrationRunner.java:73-76)
- [`MigrationRunner.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/MigrationRunner.java:130-149)

Impact:
- the safety claim is too strong for real-world databases
- missing files can disappear without a hard failure
- DDL behavior varies by engine, so the same migration can behave differently across databases

Recommendation:
- downgrade the claim in documentation
- fail when a declared migration file is missing
- add an optional checksum column so file edits are detectable
- document which databases are expected to support transactional DDL

## Medium Priority Findings

### 6. The DB module is a service aggregator, not just a module binder

`DbModule` currently owns config creation, primary database creation, named datasource creation, health checks, and migrations. That is a lot of unrelated policy in one class.

Evidence:
- [`DbModule.java`](freeway-db/src/main/java/com/jujin/freeway/db/DbModule.java:72-220)

Impact:
- harder to test individual behaviors
- harder to evolve one concern without touching the others
- the module becomes a coordination knot

Recommendation:
- split into smaller internal builders or startup handlers
- keep `DbModule` as the composition root only
- move health check and migration startup into separate classes

### 7. Named datasource discovery silently skips invalid entries

`buildDatabases()` logs and skips datasources with missing URL, username, or password. That is convenient, but it also allows a misconfigured production deployment to start partially and hide the actual error.

Evidence:
- [`DbModule.java`](freeway-db/src/main/java/com/jujin/freeway/db/DbModule.java:149-181)

Impact:
- partial startup with hidden config mistakes
- brittle operational behavior

Recommendation:
- fail fast if a named datasource is declared but incomplete
- only skip if the name itself is absent from the declaration list
- surface a single aggregated error listing the bad entries

### 8. Public API leaks internal implementation type

`DatabaseBuilder.rowMapper(...)` is typed against `DefaultRowMapper`, which is an internal implementation class. That makes the public builder API depend on implementation details.

Evidence:
- [`DatabaseBuilder.java`](freeway-db/src/main/java/com/jujin/freeway/db/DatabaseBuilder.java:37-44)
- [`DatabaseBuilder.java`](freeway-db/src/main/java/com/jujin/freeway/db/DatabaseBuilder.java:211-215)
- [`DatabaseImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/DatabaseImpl.java:21-40)

Impact:
- public API becomes harder to refactor
- external callers can start depending on internals

Recommendation:
- expose `RowMapper` or a dedicated builder interface instead
- keep `DefaultRowMapper` package-private if possible

### 9. Transaction docs and semantics need tighter wording

`Transaction` Javadoc references a `Database.TransactionWork` type that is not present in the current API. That is a documentation contract mismatch.

Evidence:
- [`Transaction.java`](freeway-db/src/main/java/com/jujin/freeway/db/Transaction.java:3-12)
- [`Database.java`](freeway-db/src/main/java/com/jujin/freeway/db/Database.java:65-74)
- [`DatabaseImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/DatabaseImpl.java:54-64)

Impact:
- readers get a misleading mental model
- API documentation drifts from actual signatures

Recommendation:
- align the Javadoc with the current transaction API
- state clearly that manual transactions should be used with try-with-resources

## Lower Priority / Technical Debt

### 10. Batch API is lenient in ways that deserve explicit policy

`BatchQueryImpl` accepts positional and named rows, but it does not validate the contract aggressively. Missing values become `null`, and there is no strict shape check across all rows.

Evidence:
- [`BatchQueryImpl.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/BatchQueryImpl.java:31-87)

Recommendation:
- define a stricter row-shape contract
- reject mixed styles and incomplete rows early
- add tests for batch binding edge cases

### 11. Row mapping is permissive and can hide schema drift

`DefaultRowMapper` uses fallback coercion, reflection, and null defaults for missing columns. That is convenient, but it can hide schema drift and bad mappings.

Evidence:
- [`DefaultRowMapper.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/DefaultRowMapper.java:72-80)
- [`DefaultRowMapper.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/DefaultRowMapper.java:115-140)
- [`DefaultRowMapper.java`](freeway-db/src/main/java/com/jujin/freeway/db/internal/DefaultRowMapper.java:145-220)

Recommendation:
- consider a strict mode that fails on missing columns or ambiguous coercions
- keep permissive behavior only where the caller opts into it

## Recommended Refactor Order

1. Harden parameter parsing and binding.
2. Fix timeout truncation.
3. Clarify and simplify pool semantics.
4. Tighten migration behavior and documentation.
5. Split `DbModule` into smaller units.
6. Remove internal-type leakage from public builders.
7. Add tests for failure paths and misconfiguration.

## Test Gaps Worth Closing

- SQL literals and comments containing `:name`
- missing named params
- extra positional params
- mixed positional and named binding
- sub-second timeout behavior
- missing migration files
- concurrent migration startup behavior
- datasource declaration with incomplete config

## Overall Assessment

The DB module is structurally usable, but it is not yet strict enough for a framework core. The biggest risk is silent acceptance of bad input: invalid SQL parsing, missing bind values, truncated timeouts, and skipped datasources. Those issues do not just reduce polish; they directly reduce trust in the framework.

The right direction is to make the module more explicit, less forgiving at the boundary, and more honest about what each piece guarantees.
