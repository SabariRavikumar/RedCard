# RedCard

A local HTTP forward proxy that blocks distracting websites, written with Spring Boot WebFlux and Redis.

You point your browser at the proxy. For every request it checks the destination host against a blocklist stored in Redis. Blocked hosts are refused, and everything else is streamed through to the real site without blocking a thread. Each blocked site has its own unlock duration, and an unlock expires on its own through a Redis TTL.

RedCard started as a learning project for reactive Java (Project Reactor, WebFlux, reactive Redis), so the code favours clarity over cleverness and the limitations below are stated plainly.

> **Status:** work in progress. HTTP traffic is proxied and blocked correctly. HTTPS tunnelling and the "solve a puzzle to unlock" page are not finished yet. See [Limitations](#limitations) and [Roadmap](#roadmap).

## Contents

- [Features](#features)
- [How it works](#how-it-works)
- [Redis data model](#redis-data-model)
- [Getting started](#getting-started)
- [Using the proxy](#using-the-proxy)
- [HTTP endpoints](#http-endpoints)
- [Project structure](#project-structure)
- [Design notes](#design-notes)
- [Limitations](#limitations)
- [Roadmap](#roadmap)
- [Testing](#testing)

## Features

Working today:

- **Forward proxy for plain HTTP.** Method, headers and body are forwarded, and the status, headers and body come back unchanged. Bodies are streamed as `DataBuffer` chunks, so binary files arrive byte for byte (checked with a SHA-256 comparison).
- **Non-blocking end to end.** No `.block()`, no `Thread.sleep`, and no blocking client anywhere on the request path.
- **Redis-backed blocklist.** Blocked sites live in Redis and can be changed while the app is running.
- **Per-site unlock duration.** Each blocked site stores how long an unlock lasts. An unlock is a Redis key with a TTL, so it re-locks by itself.
- **Management API and admin page.** Add, remove and list blocked sites over REST, or use the Thymeleaf page at `/admin`.
- **Puzzle generator and checker (in progress).** A maths puzzle service stores the answer in Redis and checks it exactly once.

Not done yet: HTTPS tunnelling (`CONNECT`), the blocked page that shows the puzzle, scheduled resets and usage analytics. See the [Roadmap](#roadmap).

## How it works

```
 Browser / curl
      |   GET http://example.com/ HTTP/1.1      <- a proxy client sends the full URL
      v
+------------------------------------------------------------+
|  ProxyFilter  (WebFilter, runs on every request)           |
|                                                            |
|   method is CONNECT?  ---- yes --> 501 Not Implemented     |
|   host is "localhost"? --- yes --> normal controllers      |
|        |                           (admin page, REST API)  |
|        no                                                  |
|        v                                                   |
|   BlocklistService.isBlocked(host)  <---- Redis            |
|        |                                                   |
|   blocked --> 403 "Blocked by RedCard"                     |
|   allowed --> WebClient streams the request to the site    |
|               and streams the response back               |
+------------------------------------------------------------+
```

A request counts as **blocked** when the host is in the `blocked:sites` hash **and** there is no active `unlocked:<host>` key. Host names are lower-cased before every lookup.

When forwarding, the proxy:

- sends the client's headers upstream, except hop-by-hop headers (`Connection`, `Keep-Alive`, `Proxy-Connection`, `Transfer-Encoding`, `Upgrade`, `TE`, `Trailer`, `Proxy-Authenticate`, `Proxy-Authorization`) and `Host`, which the client library sets from the target URL;
- copies the upstream status and headers back, again without the hop-by-hop ones;
- turns off Reactor Netty's automatic compression. With it on, the client library added its own `Accept-Encoding: gzip` and decompressed responses, which would leave `Content-Encoding` and `Content-Length` headers that no longer matched the body. With it off, the browser's own `Accept-Encoding` is forwarded and the response bytes are passed through as they are.

## Redis data model

| Key | Type | Value | Lifetime |
|---|---|---|---|
| `blocked:sites` | hash | field = host (lower case), value = unlock duration in seconds | permanent until removed |
| `unlocked:<host>` | string | placeholder (`true`) | expires after the host's unlock duration |
| `puzzle:<uuid>` | string | the correct answer to a puzzle | expires after 5 minutes, deleted on the first check |

Only the **existence** of `unlocked:<host>` matters. The duration comes from the hash when the unlock is created.

Inspect it by hand:

```bash
redis-cli HGETALL blocked:sites
redis-cli TTL unlocked:reddit.com
```

## Getting started

### Prerequisites

- **JDK 21**
- **Redis**, reachable on `localhost:6379`. No configuration is needed for that default.
- Docker or Podman, if you want to run Redis in a container.

Maven is not required, because the project includes the Maven wrapper.

### Start Redis

```bash
docker run -d --name redcard-redis -p 6379:6379 redis:7
docker exec redcard-redis redis-cli ping     # prints PONG
```

(Podman works the same way with `podman` in place of `docker`.)

### Run the app

```bash
# JAVA_HOME must point at a JDK 21
./mvnw spring-boot:run
```

The app listens on port **8080**. Open the admin page at <http://localhost:8080/admin>.

Use `localhost` for the admin page and the API. See [Limitations](#limitations) for why `127.0.0.1` behaves differently.

## Using the proxy

### Try it with curl

Start a throwaway web server to proxy to. Its address is a plain IP, so it needs no DNS:

```bash
cd /tmp && python3 -m http.server 9000 --bind 127.0.0.1
```

Add the host to the blocklist with a 600-second unlock, and request it through the proxy:

```bash
curl -X POST http://localhost:8080/api/blocked \
  -H "Content-Type: application/json" \
  -d '{"host":"127.0.0.1","unlockSeconds":600}'

curl -i -x http://localhost:8080 http://127.0.0.1:9000/     # 403 Blocked by RedCard
```

Unlock it, which creates `unlocked:127.0.0.1` with a 600-second TTL:

```bash
curl -i -X POST http://localhost:8080/admin/unlock -d "host=127.0.0.1"
curl -i -x http://localhost:8080 http://127.0.0.1:9000/     # 200, the directory listing
```

Remove it:

```bash
curl -X DELETE http://localhost:8080/api/blocked/127.0.0.1
```

### Chrome

Use a separate Chrome profile so your normal browser and system settings stay untouched:

```bash
google-chrome --user-data-dir=/tmp/chrome-redcard \
  --proxy-server="http://localhost:8080" \
  --proxy-bypass-list="<-loopback>" \
  http://127.0.0.1:9000/
```

`--proxy-bypass-list="<-loopback>"` matters. Chrome never uses a proxy for `localhost` or `127.0.0.1` by default, so without it the page loads directly and RedCard never sees the request.

### Firefox

Settings → Network Settings → Manual proxy configuration → HTTP Proxy `localhost`, port `8080`.

> Only `http://` sites work through the proxy for now. HTTPS sites arrive as `CONNECT` requests, which RedCard answers with `501` (see [Limitations](#limitations)). A fresh browser profile also makes background requests of its own, such as update checks, and those are proxied or refused too.

## HTTP endpoints

### Blocklist API

| Method | Path | Body or parameters | Result |
|---|---|---|---|
| `POST` | `/api/blocked` | JSON `{"host": "...", "unlockSeconds": 600}` | adds or updates a site |
| `DELETE` | `/api/blocked/{host}` | none | removes the site and any active unlock |
| `GET` | `/api/blocked` | none | JSON list of `{host, unlockSeconds}` |

`unlockSeconds` must be greater than 0. Entries in Redis with a non-numeric duration are skipped and logged as a warning when listing.

### Admin page

| Method | Path | Form fields | Result |
|---|---|---|---|
| `GET` | `/admin` | none | table of blocked sites with an add form |
| `POST` | `/admin/add` | `host`, `unlockSeconds` | adds a site and redirects to `/admin` (`/admin?error=invalid` for bad input) |
| `POST` | `/admin/remove` | `host` | removes a site and redirects to `/admin` |
| `POST` | `/admin/unlock` | `host` | unlocks a blocked site for its configured duration |

### Puzzle (in progress)

| Method | Path | Parameters | Result |
|---|---|---|---|
| `GET` | `/puzzle/new` | none | JSON `{id, question}`. The answer is never included. |
| `POST` | `/puzzle/verify` | query params `id`, `ans` | `true` or `false` |

`verify` deletes the puzzle on the first check, right or wrong, so one puzzle allows one attempt. Surrounding whitespace in the answer is ignored. These two endpoints exist for testing the service and are not part of a finished flow yet.

### Health and experiments

`/ping`, `/string-ping`, `/slow-mono` and `/slow-block` are small endpoints from the early reactive experiments. See [Design notes](#design-notes).

## Project structure

```
src/main/java/com/zero/redcard/
├── RedcardApplication.java
├── proxy/
│   ├── ProxyFilter.java          the proxy: classify, check blocklist, forward or refuse
│   ├── BlocklistService.java     Redis operations: isBlocked, addSite, removeSite,
│   │                             unlockSite, listAllBlockedSites
│   └── BlocklistController.java  REST API under /api/blocked
├── admin/
│   └── AdminController.java      Thymeleaf admin page
├── puzzle/
│   ├── PuzzleService.java        create a maths puzzle, check an answer once
│   └── PuzzleController.java     /puzzle endpoints
├── records/                      small immutable data types (Puzzle, BlocklistEntry, ...)
└── health/                       the reactive experiments (ping, slow-mono, slow-block)

src/main/resources/templates/admin.html
testscripts/                      load scripts for the blocking vs non-blocking comparison
```

## Design notes

**Everything on the request path is non-blocking.** Reactor Netty runs a small fixed set of event-loop threads, roughly one per CPU core. One blocking call parks a whole thread, and a proxy that blocks would freeze every browser tab. The repository keeps the experiment that showed this:

- `testscripts/blockTest.sh` sends 36 requests to `/slow-block` (a 5-second `Thread.sleep`). On a 16-core machine the requests completed in three waves 5 seconds apart. Whether a `/ping` sent during the flood stayed fast depended on luck: in one run it landed on an idle thread and answered at once, and in another it waited about 5 seconds behind a sleeping one.
- `testscripts/monoTest.sh` sends the same 36 requests to `/slow-mono` (a 5-second `delayElement`). All 36 handlers started within about 40 ms, and no event-loop thread was held.

**Bodies are never turned into `String`.** An early version read the response as a `String` and wrote it back. The bytes of a random binary file did not survive that round trip (different SHA-256). The proxy now passes `Flux<DataBuffer>` straight from the upstream response to the client, and does the same for request bodies.

**The blocklist is a Redis hash, not a set.** A hash lets each site carry its own unlock duration, and one structure means the list of blocked sites and their durations cannot drift apart.

**Unlocks are Redis keys with a TTL.** The database does the expiry, so no application timer is needed and an unlock survives an app restart.

**Puzzle answers stay on the server.** The page only ever gets a random id and the question. The answer lives in Redis under `puzzle:<id>` and is read with `GETDEL`, one atomic operation, so a puzzle cannot be accepted twice by two simultaneous requests. The id is always prefixed with `puzzle:` before it touches Redis, so a caller cannot choose an arbitrary key.

**Redis lookups sit behind a service.** `ProxyFilter` calls `BlocklistService.isBlocked(host)` and gets a `Mono<Boolean>`, so moving the blocklist from an in-memory set to Redis changed nothing in the filter.

## Limitations

- **No HTTPS.** For an `https://` site the browser sends `CONNECT host:443` and then encrypted bytes. RedCard answers `CONNECT` with `501 Not Implemented`. Tunnelling would need raw TCP handling, which Spring's WebFlux layer may not expose. It could mean dropping down to Reactor Netty. Showing a custom page on an HTTPS site would additionally require decrypting the traffic (a locally trusted certificate authority), and that is not planned for now.
- **The "is this request for me?" rule is simplistic.** A request counts as local only when its host is exactly `localhost`. A request for `127.0.0.1` is treated as a proxy request, and a request to `127.0.0.1:8080` would be forwarded back to the proxy itself. Always use `localhost` for the admin page and the API. A proper rule would compare host and port.
- **No authentication, and it listens on all interfaces.** Spring Boot binds to every network interface by default, and the admin page, the REST API and the proxy itself have no login. Anyone who can reach port 8080 can use your proxy and change your blocklist. Until authentication exists, set `server.address=127.0.0.1` (for example in `src/main/resources/application.properties`) so only your own machine can connect.
- **The puzzle is not connected to the blocked response.** A blocked request currently gets a plain `403` with the text `Blocked by RedCard`. The page that shows a puzzle, accepts the answer and calls `unlockSite` is the next piece of work. Until then, unlocking is manual through `/admin/unlock`.
- **Compressed responses are untested.** The request no longer carries a proxy-added `Accept-Encoding` header (checked against a raw socket), but the proxy has not yet been run against a real gzip-compressed response from a public site.
- **Tested mainly by hand.** See [Testing](#testing).

## Roadmap

- [ ] Puzzle page for blocked requests, plus the endpoint that checks the answer and unlocks the host
- [ ] A common base type for puzzles, so other kinds (words, sequences) can sit next to the maths one
- [ ] Tighten the local-request rule (host and port)
- [ ] Return `404` from `DELETE /api/blocked/{host}` when the site was not on the list
- [ ] `@Scheduled` daily reset and usage analytics
- [ ] `CONNECT` handling for HTTPS: refuse blocked hosts, tunnel the rest
- [ ] Authentication for the admin page and the API
- [ ] Check compressed (gzip) responses through the proxy against a public site
- [ ] Automated tests

`TASK.md` tracks the same list in more detail, and `CHANGELOG.md` records what changed and when.

## Testing

The only automated test is the default Spring Boot context-load test (`RedcardApplicationTests`). Everything else was checked by hand with `curl`, `redis-cli` and a real Chrome profile. Adding unit tests for `BlocklistService` and `PuzzleService` (with an embedded or containerised Redis) and a `WebTestClient` test for `ProxyFilter` is on the roadmap.

```bash
./mvnw test
```

The context-load test passes without a Redis server running, because the Redis connection is opened lazily.

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.1 with Spring WebFlux (Reactor Netty) |
| HTTP client | Spring `WebClient` on Reactor Netty |
| Data store | Redis through Spring Data Reactive Redis |
| Views | Thymeleaf |
| Build | Maven (wrapper included) |
