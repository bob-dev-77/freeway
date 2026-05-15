# Web Module Fix Priorities

This is the execution order for `freeway-web`, derived from the audit findings.

## 1. Must Fix First

These are the issues most likely to cause incorrect runtime behavior or broken contracts.

### 1.1 Make exception handling isolate mapper failures

Current risk:
- `handleException()` iterates mappers directly
- if a mapper itself throws, the failure path can become noisy or incomplete

Fix:
- wrap each mapper invocation in its own `try/catch`
- if a mapper fails, log it and continue to the next mapper
- only fall back to the generic 500 response after all mappers fail or decline

Why first:
- error handling is the last line of defense
- it must not be able to fail in the same way as application code

### 1.2 Make route conflict behavior explicit

Current risk:
- route registration and matching rely on ordering and override behavior that can be easy to misunderstand
- a later route can shadow an earlier route without enough visibility

Fix:
- define a strict conflict policy
- log or fail on duplicate route definitions unless override is intentional
- make route priority rules explicit in code and docs

Why first:
- route collisions are a correctness issue, not a cosmetic one

### 1.3 Fix health-route behavior

Current risk:
- the built-in health endpoint swallows its own JSON-send failure and only logs a warning
- there is no guaranteed response path if the health response itself fails

Fix:
- keep the health route simple and deterministic
- on failure, send a plain-text fallback response if possible
- avoid depending on JSON serialization for a health check path

Why first:
- health endpoints are expected to be the most reliable route in the service

### 1.4 Define executor lifecycle ownership

Current risk:
- `Executors.newVirtualThreadPerTaskExecutor()` is created and not explicitly closed
- lifecycle ownership is unclear inside startup code

Fix:
- keep the executor in a field
- register it for shutdown explicitly
- close it as part of application shutdown

Why first:
- unmanaged executors leak resources and make shutdown semantics fuzzy

## 2. Should Fix Next

These problems weaken clarity, testability, or operational predictability.

### 2.1 Split `WebModule` into smaller responsibilities

Current risk:
- one class handles binding, defaults, route bootstrapping, server startup, filter composition, health route injection, and exception handling

Fix:
- separate server bootstrap, route registration, filter composition, and error handling into smaller units
- keep `WebModule` as the composition root only

Why next:
- the module is already doing too much
- smaller units will be easier to test and reason about

### 2.2 Make filter-chain semantics stricter

Current risk:
- filters are composed in reverse order, but the ordering contract is not strongly enforced
- the current behavior is easy to misunderstand when multiple filters interact

Fix:
- document the order model precisely
- validate filter names and ordering hints
- add tests for before/after/first/last style sequencing if supported

Why next:
- middleware ordering bugs are hard to diagnose once deployed

### 2.3 Tighten request-context lifecycle behavior

Current risk:
- per-request context is created through thread-local/per-thread machinery in startup code
- the lifecycle is implicit rather than explicit

Fix:
- make request-context creation and disposal explicit at the request boundary
- keep thread-local usage as an implementation detail, not the contract

Why next:
- lifecycle bugs in request state become cross-request contamination bugs

### 2.4 Clarify CORS defaults and override policy

Current risk:
- the built-in CORS defaults are broad and unconditional
- users may not realize how much policy is enabled by default

Fix:
- make the default policy explicit and documented
- allow route- or app-level override with clear precedence
- add tests for preflight and credentialed requests

Why next:
- CORS behavior is a user-facing policy decision, not just a helper detail

## 3. Should Fix After That

These are mostly API-shape and maintainability concerns.

### 3.1 Remove public exposure of implementation details

Current risk:
- public package surface is fairly wide for a framework module

Fix:
- keep helper types package-private where possible
- export only the minimum API needed by applications and extension modules

Why here:
- reducing public surface lowers long-term maintenance cost

### 3.2 Reduce magic in startup flow

Current risk:
- startup performs multiple implicit actions at once: health route injection, server creation, handler wiring, filter registration, shutdown registration

Fix:
- split the startup path into explicit steps with clear ownership
- keep each step testable in isolation

Why here:
- this improves observability and makes startup failures easier to diagnose

### 3.3 Normalize module naming and documentation

Current risk:
- terminology is serviceable but not yet tight enough for a framework surface

Fix:
- standardize names like `filter chain`, `exception mapper chain`, `route registry`, and `request context`
- make the public docs match the code’s actual behavior

Why here:
- a framework lives or dies by how clearly its surface is explained

## 4. Low Priority / Optional Hardening

These changes improve polish and confidence, but they should not block the core fixes.

### 4.1 Add failure-path tests

Test cases to add:
- exception mapper throwing during handling
- duplicate route registration
- route precedence and shadowing
- health endpoint failure
- filter chain ordering
- shutdown after server startup

### 4.2 Add startup/shutdown diagnostics

Optional hardening:
- log the executor and server lifecycle milestones
- report the active route count and filter count with clearer context

### 4.3 Review the default health endpoint contract

Optional hardening:
- decide whether `/health` should be a framework-owned reserved route or a user-overridable convention
- encode that decision in the route registry policy

## Practical Implementation Order

If this were being fixed in code, I would do it in this order:

1. isolate exception-mapper failures
2. define route conflict policy
3. fix health-route fallback behavior
4. close and own the executor lifecycle
5. split `WebModule`
6. tighten filter and request-context semantics
7. add tests

That sequence removes the highest-risk runtime bugs before changing the surrounding structure.
