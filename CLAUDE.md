# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Java/Spring Boot L7 traffic router — similar to Envoy/Kong/Traefik. Incoming requests are forwarded to `POST /decide`, which evaluates routing patterns against the request (JWT claims, URL, HTTP method) and responds with a **307 redirect** to the matched destination.

## Commands

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Test
./gradlew test

# Single test
./gradlew test --tests "com.tom.backendswitch.SomeTestClass"
```

## Architecture

**Request flow:**
1. Client sends `POST /decide` with an `Authorization: Bearer <jwt>` header and an `OriginalRequest` JSON body (`method`, `url`, `jsonPayload`, `headers`)
2. `DecisionController` delegates entirely to `DecisionService.handleRequest(originalRequest, token)`
3. `handleRequest` orchestrates: `matchPattern` → if match found, `extractClaims` + `extractRequestParams` + `evaluateLogic`
4. Returns the destination URL, or `null` if no pattern matches (controller falls back to `originalRequest.url`)
5. Controller sends a **307 Temporary Redirect**

**Routing configuration (`src/main/resources/routing.properties`):**
Patterns are loaded once at startup via `@PostConstruct` into a `TreeMap` (ascending key order). Each pattern has: `method`, `url` (supports `*` wildcards), `logic`, and `destination`. Missing any of the 4 fields causes that pattern to be skipped. `matchPattern` uses `findFirst()` on the ordered stream — the lowest-id match wins and evaluation short-circuits immediately.

**Logic types (evaluated in `evaluateLogic`):**
- **Expression logic** — strings like `(({param.operation} <= 3) AND ({claim.iat} == 1516239022))`. Parsed by `ExpressionParser` into an `Expression` tree. Variables in `{...}` are resolved from a merged context map:
  - `claim.<name>` — JWT claims (deserialized as `Map<String, Object>`, `.toString()`'d)
  - `param.<name>` — URL query parameters
  - `header.<name>` — request headers
  - `payload.<A>.<B>.<C>` — fields from `jsonPayload` using dot-path notation for arbitrary nesting. Parsed **lazily**: `jsonPayload` is only deserialized if the logic string contains `{payload.`. Nested JSON objects are recursively flattened into the context (e.g. `{"user":{"role":"admin"}}` → `payload.user.role=admin`). Unparseable payload is silently ignored (payload keys remain absent from context).
- **RANDOM logic** — logic string format: `RANDOM:<0-100>`. `probabilityDecision()` validates the value is in range, short-circuits for `100` (always route) and `0` (never route), otherwise generates `nextInt(100)` and returns the destination if `random < probability` (giving exactly `probability`% chance).

**Expression operators:** `AND`, `OR`, `NOT`, `==`, `!=`, `<`, `<=`, `>`, `>=`. Each maps to a concrete `Expression` subclass with `boolean evaluate()`. `NOT` is a unary prefix operator and binds tighter than `AND`/`OR`. Numeric operators throw `RuntimeException` on unparseable values. `ExpressionParser` is fully static.

**Package structure:**
- `controller` — `DecisionController` — two endpoints: `POST /decide` (routing) and `POST /reload` (re-reads `routing.properties` at runtime; clears the pattern map first so removed patterns don't linger)
- `service` — `DecisionService` (orchestration, pattern loading, URL/method matching, claim extraction, param extraction, logic evaluation)
- `model` — `Pattern` (routing rule), `OriginalRequest` (`method`, `url`, `jsonPayload`, `headers`); both are Lombok `@Value` (immutable)
- `expression` — `Expression` (abstract), `ValueExpression` (leaf), operator classes, `ExpressionParser`
