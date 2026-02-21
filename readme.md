<h1>Back-End Switch</h1>
(sounds like "Bait & Switch")<br>

similar to Envoy Proxy/Kong Gateway/Traefik/Istio or Linkerd (service mesh) - specialized L7 reverse proxy / traffic router

request entity:<br>
logged in user with JWT/Paseto and claims
url/http request (payload optional)

configuration:<br>
multiple of: (order matters - from more specific to less specific)
  - HTTP method - GET/POST/PUT/DELETE/etc...
  - url pattern with wildcards and pattern matching - i.e.   https://*.tom.com/perform?operation={X}
  - condition on parameters/claims/random/probablistic/mirroring (both routes)  {param.X} {claim.X} {header.X}
  - destination

saved locally in properties file, or in database. loaded on app start and kept in static memory (changes require restart)

user logs in
user makes a request -> forwarded to /decide
evaluate patterns in order, to match the request
extract request data according to condition type (lazy evaluation for payload)
evaluate condition and decide on destination
