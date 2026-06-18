# Facebook WebLite Engagement Tracking

## Overview

Facebook's WebLite client (`web.facebook.com`) tracks user engagement via two channels:
1. **Binary WebSocket frames** — real-time engagement/viewport data
2. **HTTP POST beacons** — page load timing and ad pixel events

## WebSocket Engagement Tracking

### Connection

WebLite opens a WebSocket connection immediately on page load via a global `window.__lws` instance. The connection URL format:
```
wss://<hostname>:<port>/ws/<zero-padded-sticky-token>?lid=<lid>&cm=deflate&...
```

The connection is established **synchronously** during the initial JS bundle execution, before `DOMContentLoaded` fires.

### Binary Frame Sizes and Purposes

| Frame Size (bytes) | Frequency | Likely Purpose |
|---|---|---|
| 15 | On connect | Initial handshake/session setup |
| 32-36 | Every 1-2s | Heartbeat/keepalive |
| 54 | Every 1-2s while scrolling | Viewport position / scroll update (contains timestamps) |
| 59 | Every 1-2s while browsing | Viewport visibility update |
| ~666-670 | When new post enters viewport | **Post impression event** |
| ~294 | Occasionally | Batch event / interaction summary |
| ~1044 | On state change | `app_state` event (JSON payload) |
| ~7662 | On navigation | `qpl_event` QPL performance logging (JSON payload) |
| 39971+ | On connect | Initial session state / bulk content delivery |

### Protocol Format

The binary frames use a custom binary protocol (not standard MQTT, but MQTT-inspired). Structure:
```
[2 bytes: frame length] [1 byte: type/flags] [variable: session ID "232d905dde8d6a2f"] [variable: sequence/topic] [payload]
```

All frames share a common session identifier (`232d905dde8d6a2f` in hex = `#-.].Þj/`).

### JSON Payloads Identified

Larger frames embed JSON objects with these event types:

#### `app_state`
```json
{"name": "app_state", "time": <timestamp>, ...}
```
Sent on app foreground/background transitions.

#### `qpl_event` (QPL = Quick Performance Logger)
```json
{"name": "qpl_event", "time": <timestamp>, ...}
```
Performance measurement events. ~7KB of data per event.

#### `fblite_oz_player`
```json
{"name": "fblite_oz_player", ...}
```
Video player state events (play, pause, viewport visibility).

#### `weblite_polyfill_usage`
```
weblite_polyfill_usage:name=<feature>
```
Reports which browser polyfills are being used.

### 54-byte Viewport/Timestamp Frames

These fire regularly during scrolling and contain:
- A constant header with session ID
- A timestamp (8 bytes, milliseconds since epoch: `0000019ed922b46b` = 1781XXXXXXXXX)
- Viewport flags/counters

These are the **primary engagement tracking mechanism** — they tell Facebook exactly when you're viewing content and for how long.

### ~666-byte Post Impression Frames

Fire each time a new post scrolls into view. Likely contain:
- Post/story ID
- Timestamp of first visibility
- Viewport percentage visible
- Duration visible (updated on subsequent frames or on scroll-away)

## HTTP Tracking Beacons

Sent via `navigator.sendBeacon()` and XHR POST to these endpoints:

### `/ajax/weblite_load_logging/`
Page load lifecycle events:
- `client_impression` — page first rendered
- `pipe_first_response_complete` — WebSocket delivered first data
- `pipe_first_screen_flushed` — first screen content rendered
- `lite_bundle_main_started` — JS bundle executed
- `lite_first_screen_drawn` — first paint complete
- `device_info_fetched` — device capabilities reported
- `core_web_vitals` — CLS, LCP, FID metrics

Parameters include: viewport dimensions (`iw`, `ih`, `ow`, `oh`), device width (`dw`, `dh`), URL, timing (`t`).

### `/ajax/weblite_resources_timing_logging/`
Resource load performance:
```json
{"styles": {"n": 0, "s": 26957, "d": 31, "r": "wl", "t": "css"},
 "polyfills": {"n": 0, "s": 8487, "d": 34, "r": "wl", "t": "js"},
 "bundle": {"n": 0, "s": 1504847, ...}}
```

### `/paid_ads_pixel/logging/`
Ad funnel tracking with `sem_pixel_category` values (35, 37, 39, 40) — likely different stages of ad rendering/viewability.

## Network Information Leaked

The WebSocket frames also send:
- Network type: `"wifi"` / `"WIFI"`
- Timezone: `"Etc/GMT-1"`
- Connection quality metrics

## Blocking Strategy

### Option 1: Block WebSocket sends (targeted)
Drop the 54-byte and ~666-byte frames (viewport updates and impressions) while allowing larger content-delivery frames through. Risk: Facebook may detect missing heartbeats.

### Option 2: Block HTTP beacons
Return empty responses for `/ajax/weblite_load_logging/`, `/paid_ads_pixel/logging/`, and `/ajax/weblite_resources_timing_logging/`. Lower risk — these are fire-and-forget.

### Option 3: Block all outgoing WebSocket
Nuclear option. Would break content streaming since Facebook uses the same WebSocket for both content delivery and telemetry.

### Option 4: Throttle/randomize
Allow frames through but at reduced frequency or with randomized timing, making the engagement signal noisy/useless.

## Prior Art / References

- [How Tracking Companies Circumvented Ad Blockers Using WebSockets](https://www.researchgate.net/publication/334081068_How_Tracking_Companies_Circumvented_Ad_Blockers_Using_WebSockets) — Research documenting A&A companies using WebSockets to bypass ad blocking (2019)
- [The Hitchhiker's Guide to Facebook Web Tracking with Invisible Pixels and Click IDs](https://arxiv.org/abs/2208.00710) — Academic paper on Facebook's FBCLID and pixel tracking (2022)
- [Facebook In-App Browser Tracking](https://keepnetlabs.com/blog/facebooks-in-app-browser-within-ios-apps-track-anything-you-do-on-any-website) — Felix Krause's research on Meta injecting JS into in-app browsers
- [FacebookTrackingRemoval](https://github.com/mgziminsky/FacebookTrackingRemoval) — Browser extension that removes tracking from Facebook
- [Facebook's Hotel California: Cross-Site Tracking](https://www.eff.org/2011/october/facebook%E2%80%99s-hotel-california-cross-site-tracking-and-potential-impact-digital-privacy) — EFF analysis of Facebook's `datr` cookie tracking
- [Covert web-to-app tracking via localhost](https://news.ycombinator.com/item?id=44169115) — Recent (2025) research showing Meta Pixel using WebRTC SDP munging to exfiltrate tracking data to native apps via localhost ports
- [Meta internal "impressions" logging](https://medium.com/meta-analytics/using-log-time-denormalization-for-data-wrangling-at-meta-3b6fc050268a) — Meta engineering blog confirming they log a row each time a person sees content

## Configuration in SlimBook

The tracking investigation hooks are in `MainActivity.kt`:
- `onPageStarted` — attempts early WebSocket.prototype.send hook
- `shouldInterceptRequest` — logs HTTP tracking requests
- `injectFilter()` — installs beacon/WS interceptors after page load

The Banzai logging config (from Facebook's JS) confirms these tracking routes:
```
known_routes: ["artillery_javascript_actions", "artillery_javascript_trace",
"logger", "falco", "gk2_exposure", "js_error_logging", "loom_trace",
"marauder", "perfx_custom_logger_endpoint", "qex", "require_cond_exposure_logging"]
```

## TODO

- [ ] Decode the full binary protocol (likely protobuf or Thrift-encoded)
- [ ] Map specific post IDs in the ~666-byte impression frames
- [ ] Test blocking 54-byte frames to see if content delivery breaks
- [ ] Implement configurable blocking in filter.js or via shouldInterceptRequest
