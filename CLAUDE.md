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
- **Expression logic** — strings like `(({param.operation} <= 3) AND ({claim.iat} == 1516239022))`. Parsed by `ExpressionParser` into an `Expression` tree. Variables in `{...}` are resolved from a merged context map: JWT claims keyed as `claim.<name>` (deserialized as `Map<String, Object>`, `.toString()`'d into context), URL query params keyed as `param.<name>`, request headers keyed as `header.<name>`.
- **RANDOM logic** — logic string format: `RANDOM:<0-100>`. `probabilityDecision()` validates the value is in range, short-circuits for `100` (always route) and `0` (never route), otherwise generates `nextInt(100)` and returns the destination if `random < probability` (giving exactly `probability`% chance).

**Expression operators:** `AND`, `OR`, `==`, `!=`, `<`, `<=`, `>`, `>=`. Each maps to a concrete `Expression` subclass with `boolean evaluate()`. Numeric operators throw `RuntimeException` on unparseable values. `ExpressionParser` is fully static.

**Package structure:**
- `controller` — `DecisionController` (single `POST /decide` endpoint, thin — all logic in service)
- `service` — `DecisionService` (orchestration, pattern loading, URL/method matching, claim extraction, param extraction, logic evaluation)
- `model` — `Pattern` (routing rule), `OriginalRequest` (`method`, `url`, `jsonPayload`, `headers`); both are Lombok `@Value` (immutable)
- `expression` — `Expression` (abstract), `ValueExpression` (leaf), operator classes, `ExpressionParser`
