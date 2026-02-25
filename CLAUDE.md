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
1. Client sends `POST /decide` with an `Authorization: Bearer <jwt>` header and an `OriginalRequest` JSON body (`url`, `jsonPayload`)
2. `DecisionController` extracts JWT claims via `DecisionService.extractClaims()` — decodes the base64url payload and parses it as `Map<String, String>` using Jackson
3. `DecisionService.matchPattern()` runs a parallel stream over `patterns` (a `TreeMap<Integer, Pattern>`), filters by URL match, and returns the lowest-id winner
4. `DecisionService.evaluateLogic(pattern, claims)` evaluates the pattern's logic expression against the claims to determine the destination
5. Controller sends a **307 Temporary Redirect** to the destination (or `originalRequest.url` as fallback)

**Routing configuration (`src/main/resources/routing.properties`):**
Patterns are loaded once at startup via `@PostConstruct` into a static `TreeMap` (order matters — more specific patterns first). Each pattern has: `method`, `url` (supports `*` wildcards), `logic` (e.g. `{operation} gt 3`), and `destination`. Missing any of the 4 fields causes that pattern to be skipped.

**Package structure:**
- `controller` — `DecisionController` (single `POST /decide` endpoint)
- `service` — `DecisionService` (pattern loading, URL matching, claim extraction, logic evaluation)
- `model` — `Pattern` (routing rule), `OriginalRequest` (inbound request body); both are Lombok `@Value` (immutable)

**URL matching** (`DecisionService.matchUrl`): splits the pattern URL on `*` and checks the input URL sequentially against each token, advancing through the string on each match.
