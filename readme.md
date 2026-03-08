# Back-End Switch - JAVA Edition
*(sounds like "Bait & Switch")*

Similar to Envoy Proxy / Kong Gateway / Traefik / Istio or Linkerd — a specialised L7 reverse proxy / traffic router.

## How it works

1. Client sends `POST /decide` with the original request metadata
2. The service matches the request against configured patterns (by HTTP method and URL)
3. If a match is found, the pattern's logic is evaluated using JWT claims, URL query params, request headers, and the request body
4. The outcome depends on the pattern's `resolution`:
   - **`redirect`** (default) — client receives a **307 Temporary Redirect** to the destination
   - **`follow`** — the service calls the destination itself and proxies the response back (status, headers, and body)
   - No match or logic false — **307** back to the original URL

## API

### `POST /decide`

Routes a request based on the configured patterns.

**Headers:**
- `Authorization: Bearer <jwt>` — JWT whose claims are available in routing logic
- `X-logging-level: trace` *(optional)* — elevates log verbosity for this request only (any valid level: `trace`, `debug`, `info`, `warn`, `error`)

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

**Response:**
- `redirect` pattern (or no match): `307 Temporary Redirect` with a `Location` header
- `follow` pattern: upstream status code, headers, and body proxied directly to the caller

---

### `POST /reload`

Re-reads `routing.properties` and reloads all patterns without restarting the service. The pattern map is cleared first, so removed patterns take effect immediately.

---

### `GET /patterns`

Returns the currently loaded routing patterns as JSON, ordered by id.

---

### `GET /actuator/health`

Returns service health status. Suitable for Kubernetes liveness and readiness probes.

---

## Routing configuration

Patterns are defined in `src/main/resources/routing.properties`. `method`, `url`, `logic`, and `destination` are required; `resolution` and `timeout` are optional.

```properties
pattern.<id>.method=GET
pattern.<id>.url=https://*.example.com/api/*
pattern.<id>.logic=({param.operation} > 3) AND ({claim.role} == admin)
pattern.<id>.destination=https://backend-a.example.com
pattern.<id>.resolution=follow
pattern.<id>.timeout=3000
```

- **`id`** — integer; lower id wins when multiple patterns match the same request (order matters — configure from more specific to less specific)
- **`url`** — supports `*` wildcards
- **`logic`** — expression string or `RANDOM:<0-100>`
- **`resolution`** — `redirect` (default) or `follow`
- **`timeout`** — milliseconds; applies connect and read timeout for `follow` patterns; omit for no timeout

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

## External routing configuration

By default the service loads `routing.properties` from the classpath (baked into the JAR). Set the `ROUTING_PROPERTIES_PATH` environment variable to point at an external file instead — useful for Docker volume mounts and Kubernetes ConfigMaps.

When `ROUTING_PROPERTIES_PATH` is set, the service also watches the file for changes and reloads automatically — no `POST /reload` needed. `POST /reload` still works on demand.

**Docker:**

```bash
docker run -p 8080:8080 \
  -e ROUTING_PROPERTIES_PATH=/etc/config/routing.properties \
  -v ./routing.properties:/etc/config/routing.properties \
  backendswitch
```

**Kubernetes ConfigMap (mounted as file):**

```yaml
# ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: backendswitch-routing
data:
  routing.properties: |
    pattern.1.method=GET
    pattern.1.url=https://*.example.com/api/*
    pattern.1.logic=({param.operation} > 3)
    pattern.1.destination=https://backend-a.example.com

# Deployment env + volumeMount
env:
  - name: ROUTING_PROPERTIES_PATH
    value: /etc/config/routing.properties
volumeMounts:
  - name: routing-config
    mountPath: /etc/config
volumes:
  - name: routing-config
    configMap:
      name: backendswitch-routing
```

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

## Docker

Build the JAR and Docker image in one step:

```bash
./gradlew dockerBuild
```

Or separately:

```bash
./gradlew bootJar
docker build -t backendswitch .
```

Run the container:

```bash
docker run -p 8080:8080 backendswitch
```
