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
1. Client sends `POST /decide` with an `Authorization: Bearer <jwt>` header and an `OriginalRequest` JSON body (`method`, `url`, `jsonPayload`)
2. `DecisionController` extracts JWT claims via `DecisionService.extractClaims()` — decodes the base64url payload and parses it as `Map<String, String>` using Jackson
3. `DecisionService.matchPattern(originalRequest)` runs a parallel stream over `patterns` (a `TreeMap<Integer, Pattern>`), filters by both HTTP method and URL wildcard match, and returns the lowest-id winner
4. `DecisionService.evaluateLogic(pattern, claims, params)` builds a merged context map and parses/evaluates the pattern's logic expression via `ExpressionParser`
5. Controller sends a **307 Temporary Redirect** to the destination (or `originalRequest.url` as fallback)

**Routing configuration (`src/main/resources/routing.properties`):**
Patterns are loaded once at startup via `@PostConstruct` into a static `TreeMap` (order matters — lower id wins). Each pattern has: `method`, `url` (supports `*` wildcards), `logic`, and `destination`. Missing any of the 4 fields causes that pattern to be skipped.

**Logic expressions:**
The `expression` package implements a recursive Interpreter pattern. `ExpressionParser.parse(logic, context)` builds an `Expression` tree from strings like `(({param.operation} <= 3) AND ({claim.id} == 1))`. Variables in `{...}` are resolved from the context map at parse time. The context map merges JWT claims (keyed as `claim.<name>`) and URL query params (keyed as `param.<name>`).

Supported operators: `AND`, `OR`, `==`, `<`, `<=`, `>`, `>=`. Each maps to a concrete `Expression` subclass with a `boolean evaluate()` method.

**Package structure:**
- `controller` — `DecisionController` (single `POST /decide` endpoint)
- `service` — `DecisionService` (pattern loading, URL/method matching, claim extraction, param extraction, logic evaluation)
- `model` — `Pattern` (routing rule), `OriginalRequest` (`method`, `url`, `jsonPayload`); both are Lombok `@Value` (immutable)
- `expression` — `Expression` (abstract), `ValueExpression` (leaf), operator classes, `ExpressionParser`
