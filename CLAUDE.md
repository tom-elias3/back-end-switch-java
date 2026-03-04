# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Java/Spring Boot L7 traffic router — similar to Envoy/Kong/Traefik. Incoming requests are forwarded to `POST /decide`, which evaluates routing patterns against the request (JWT claims, URL, HTTP method) and either responds with a **307 redirect** or proxies the upstream response back to the caller.

## Stack

Java 21, Spring Boot 3.4.3, Lombok `@Value` (immutable models). Docker image: `eclipse-temurin:21-jre-alpine`, port 8080.

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

# Build JAR and Docker image (requires Docker running)
./gradlew dockerBuild
```

## Architecture

**Request flow:**
1. Client sends `POST /decide` with an `Authorization: Bearer <jwt>` header and an `OriginalRequest` JSON body (`method`, `url`, `jsonPayload`, `headers`)
2. `DecisionController.decide` delegates entirely to `DecisionService.handleRequest(originalRequest, token, response)` — the controller is a pure delegation layer
3. `handleRequest` orchestrates: `matchPattern` → if match found, `extractClaims` + `extractRequestParams` + `evaluateLogic` → wraps result in a `Decision(destination, resolution)`
4. `handleRequest` then writes the HTTP response directly:
   - **No match / logic false** → 307 to `originalRequest.url`
   - **`REDIRECT` resolution** → 307 to `decision.destination`
   - **`FOLLOW` resolution** → `proxyRequest` calls the destination via `RestClient`, copies upstream status + headers + body back to the caller; 4xx/5xx propagated as-is. If the matched pattern has a `timeout`, a dedicated `RestClient` is built with that connect+read timeout (ms); otherwise the shared no-timeout instance is used.

**Routing configuration (`src/main/resources/routing.properties`):**
Patterns are loaded once at startup via `@PostConstruct` into a `TreeMap` (ascending key order). Each pattern has: `method`, `url` (supports `*` wildcards), `logic`, `destination`, and optionally `resolution` and `timeout`. Missing any of the first 4 fields causes that pattern to be skipped. `resolution` defaults to `REDIRECT` if absent or unrecognised. `timeout` defaults to `null` (no timeout) if absent. `matchPattern` uses `findFirst()` on the ordered stream — the lowest-id match wins and evaluation short-circuits immediately.

**Pattern fields:**
```
pattern.<id>.method=GET
pattern.<id>.url=https://*.example.com/path?param=*
pattern.<id>.logic=(({param.x} > 3) AND ({claim.iat} == 123))
pattern.<id>.destination=https://upstream.host
pattern.<id>.resolution=follow   # optional; redirect (default) or follow
pattern.<id>.timeout=3000        # optional ms; connect+read timeout, only applied for follow
```

**Logic types (evaluated in `evaluateLogic`):**
- **Expression logic** — strings like `(({param.operation} <= 3) AND ({claim.iat} == 1516239022))`. Parsed by `ExpressionParser` into an `Expression` tree. Variables in `{...}` are resolved from a merged context map:
  - `claim.<name>` — JWT claims (deserialized as `Map<String, Object>`, `.toString()`'d)
  - `param.<name>` — URL query parameters
  - `header.<name>` — request headers
  - `payload.<A>.<B>.<C>` — fields from `jsonPayload` using dot-path notation for arbitrary nesting. Parsed **lazily**: `jsonPayload` is only deserialized if the logic string contains `{payload.`. Nested JSON objects are recursively flattened into the context (e.g. `{"user":{"role":"admin"}}` → `payload.user.role=admin`). Unparseable payload is silently ignored (payload keys remain absent from context).
- **RANDOM logic** — logic string format: `RANDOM:<0-100>`. `probabilityDecision()` validates the value is in range, short-circuits for `100` (always route) and `0` (never route), otherwise generates `nextInt(100)` and returns the destination if `random < probability` (giving exactly `probability`% chance).

**Expression operators:** `AND`, `OR`, `NOT`, `==`, `!=`, `<`, `<=`, `>`, `>=`. Each maps to a concrete `Expression` subclass with `boolean evaluate()`. `NOT` is a unary prefix operator. Numeric operators throw `RuntimeException` on unparseable values. `ExpressionParser` is fully static. Missing context keys throw `RuntimeException` at evaluation time.

**Package structure:**
- `controller` — `DecisionController` — three endpoints: `POST /decide` (single-line delegation to service), `POST /reload` (re-reads `routing.properties` at runtime; clears the pattern map first so removed patterns don't linger), `GET /patterns` (returns the live pattern map as JSON, ordered by id)
- `service` — `DecisionService` — owns all routing logic AND response-writing: pattern loading, URL/method matching, claim extraction, param extraction, logic evaluation, redirect vs proxy decision
- `model` — `Pattern` (routing rule with `id`, `method`, `url`, `logic`, `destination`, `resolution`, `timeout`), `OriginalRequest` (`method`, `url`, `jsonPayload`, `headers`), `Decision` (record wrapping `destination` + `resolution`), `ResolutionType` (enum: `REDIRECT`, `FOLLOW`); models are Lombok `@Value` (immutable) except `Decision` which is a Java record
- `expression` — `Expression` (abstract), `ValueExpression` (leaf), operator classes, `ExpressionParser`
