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
- [x] `BlocklistService.unlockSite(host)`, `POST /admin/unlock`
- [x] `PuzzleService.createPuzzle` / `verify` (answer in Redis, one attempt via GETDEL)
- [x] `CONNECT` answered with 501, header-value debug logging removed
- [x] README.md

## To do
- [ ] Puzzle page (`GET /puzzle?host=&url=`), `POST /puzzle/solve` (verify, unlock, redirect), and ProxyFilter redirecting blocked requests to the page
- [ ] Replace the temporary `/puzzle/verify` endpoint
- [ ] Common base type for puzzles (QAAbstract idea)
- [ ] Authentication for admin page and API; bind to 127.0.0.1
- [ ] Tighten the local-request rule in `ProxyFilter` (host and port, not just `localhost`)
- [ ] Move the `"unlocked:"` prefix into one constant
- [ ] DELETE endpoint should distinguish "not found" (404) from success
- [ ] `@Scheduled` daily reset and analytics logging
- [ ] HTTPS: handle `CONNECT` (refuse blocked hosts), decide on decryption
- [ ] Retest gzip passthrough once DNS works on the machine
- [ ] Tests (unit and integration)
