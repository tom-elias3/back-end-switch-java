# Back-End Switch - JAVA Edition
*(sounds like "Bait & Switch")*

Similar to Envoy Proxy / Kong Gateway / Traefik / Istio or Linkerd — a specialised L7 reverse proxy / traffic router.

## How it works

1. Client sends `POST /decide` with the original request metadata
2. The service matches the request against configured patterns (by HTTP method and URL)
3. If a match is found, the pattern's logic is evaluated using JWT claims, URL query params, request headers, and the request body
4. The client is redirected (**307 Temporary Redirect**) to the destination URL, or back to the original URL if no pattern matches or the condition is false

## API

### `POST /decide`

Routes a request based on the configured patterns.

**Headers:**
- `Authorization: Bearer <jwt>` — JWT whose claims are available in routing logic

**Body:**
```json
{
  "method": "GET",
  "url": "https://api.example.com/perform?operation=5",
  "jsonPayload": "{\"user\":{\"role\":\"admin\"}}",
  "headers": {
    "X-Tenant": "acme"
  }
}
```

**Response:** `307 Temporary Redirect` with a `Location` header pointing to the matched destination, or the original URL if no pattern matched.

---

### `POST /reload`

Re-reads `routing.properties` and reloads all patterns without restarting the service. The pattern map is cleared first, so removed patterns take effect immediately.

---

### `GET /patterns`

Returns the currently loaded routing patterns as JSON, ordered by id.

---

## Routing configuration

Patterns are defined in `src/main/resources/routing.properties`. Each pattern requires four fields:

```properties
pattern.<id>.method=GET
pattern.<id>.url=https://*.example.com/api/*
pattern.<id>.logic=({param.operation} > 3) AND ({claim.role} == admin)
pattern.<id>.destination=https://backend-a.example.com
```

- **`id`** — integer; lower id wins when multiple patterns match the same request (order matters — configure from more specific to less specific)
- **`url`** — supports `*` wildcards
- **`logic`** — expression string or `RANDOM:<0-100>`

### Logic: expressions

Variables are referenced with `{prefix.name}` syntax:

| Prefix | Source |
|---|---|
| `claim.<name>` | JWT claim |
| `param.<name>` | URL query parameter |
| `header.<name>` | Request header |
| `payload.<A>.<B>` | Field from `jsonPayload` using dot-path (arbitrary depth) |

Supported operators: `AND`, `OR`, `NOT`, `==`, `!=`, `<`, `<=`, `>`, `>=`

```properties
# Combined claim and query param check
pattern.1.logic=({param.operation} > 3) AND ({claim.iat} == 1516239022)

# Negate
pattern.2.logic=NOT ({claim.tier} == free)

# Nested payload field
pattern.3.logic={payload.user.role} == admin

# Header-based
pattern.4.logic={header.X-Tenant} == acme
```

### Logic: random / probabilistic routing

Routes a percentage of matching traffic to the destination; the rest falls through to the original URL.

```properties
pattern.5.logic=RANDOM:30
```

`RANDOM:100` always routes. `RANDOM:0` never routes.

## Building and running

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Test
./gradlew test

# Single test class
./gradlew test --tests "com.tom.backendswitch.SomeTestClass"
```
