# TASK.md

RedCard: local forward proxy and traffic analyzer (Spring Boot, WebFlux, Redis). Learning project, so the user writes the code and Claude mentors.

## Done
- [x] Reactive stack (WebFlux and Reactor Netty), pom cleaned up
- [x] Blocking vs non-blocking experiments, `Mono` laziness
- [x] Milestone 1: HTTP forward proxy (method, headers, streamed body, status relay, no gzip rewriting)
- [x] Milestone 2: blocklist check returns 403 with a text body
- [x] Milestone 3: blocklist in Redis (`blocked:sites` hash, host to unlock seconds), `unlocked:<host>` TTL marker
- [x] `BlocklistService`: `isBlocked`, `addSite`, `removeSite`, `listAllBlockedSites`
- [x] REST API: `POST /api/blocked`, `DELETE /api/blocked/{host}`, `GET /api/blocked`
- [x] Admin page `/admin`: list, add, remove sites (Post/Redirect/Get, input error message)

## To do
- [ ] `BlocklistService.unlock(host)`: read seconds from the hash, set `unlocked:<host>` with that TTL
- [ ] Puzzle page for blocked requests, and the endpoint that checks the answer and unlocks
- [ ] Tighten the local-request rule in `ProxyFilter` (host and port, not just `localhost`)
- [ ] Remove debug `System.out.println` calls from `ProxyFilter`
- [ ] Move the `"unlocked:"` prefix into one constant
- [ ] DELETE endpoint should distinguish "not found" (404) from success
- [ ] `@Scheduled` daily reset and analytics logging
- [ ] HTTPS: handle `CONNECT` (refuse blocked hosts), decide on decryption
- [ ] Retest gzip passthrough once DNS works on the machine
- [ ] Tests (unit and integration)
