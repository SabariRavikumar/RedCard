# Changelog

## 2026-10-09
- Admin page: Remove button per row, `POST /admin/remove` (form binding via `@ModelAttribute`, since WebFlux `@RequestParam` reads only the query string)

## 2026-10-05
- Admin page `/admin` with add form, validation error message and blocked-sites table
- `BlocklistService.listAllBlockedSites` skips and logs entries with invalid unlock seconds

## 2026-10-04
- Blocklist moved from a hardcoded set to a Redis hash (`blocked:sites`: host to unlock seconds)
- `addSite`, `removeSite` (also clears the `unlocked:<host>` marker), REST endpoints under `/api/blocked`
- `ProxyFilter` forwards request body and headers, relays status and response headers, disables compression, returns 403 with a text body for blocked hosts

## 2026-10-03
- Switched pom to the reactive stack (WebFlux, Reactor Netty)
- Added `ProxyFilter` (WebFilter) and basic forwarding with `WebClient`
- Added `.vscode/launch.json` and `.vscode/settings.json`
