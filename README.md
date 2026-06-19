# SlimBook

A lightweight Android app that provides a clean, ad-free Facebook feed by wrapping Facebook's mobile web client (`web.facebook.com`) with content filtering.

## What it does

SlimBook loads Facebook's lightweight WebLite interface and automatically removes:

- **Ads** and sponsored posts
- **Stories** section
- **Group suggestions** (posts with "Join")
- **Page suggestions** (posts with "Follow")
- **"People you may know"** suggestions
- **"Open app"** banners
- **"Are you interested?"** prompts

The filter runs continuously via a MutationObserver, catching new content as it arrives over Facebook's WebSocket connection.

## Features

- 🛡️ Real-time content filtering with stats badge
- 🚫 Removes ads, sponsored posts, stories, reels
- 👥 Removes posts from unfollowed pages and unjoined groups
- 🙈 Removes "People You May Know" and "Are you interested?" prompts
- 💬 Messenger support (via messenger.com with desktop UA)
- 👤 Author management — see all detected authors, uncheck to block
- 📋 Group management — see all detected groups, uncheck to block
- 🔍 Searchable author and group lists
- ⏰ Post age filter (12h / 1d / 2d / 5d / 10d) — hides old posts
- 🔔 Background notification polling (15m–12h interval)
- 📷 Photo upload support for posts
- 🎬 Video autoplay disabled (tap to play)
- 🔄 Pull-to-refresh
- 🔗 External links open in system browser
- 🍪 Persistent login (cookies survive app restarts)
- 💾 WebView state preserved when switching apps
- 🐛 Debug tools (highlight mode with filter reason labels, DOM dump, console log viewer)
- 📡 Remote filter updates — filter rules updated without new APK (disabled by default)

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Install

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Debug

Long-press the stats badge (bottom-right corner) to access:

- **Highlight mode** — shows filtered elements with red borders and reason labels (AD, PAGE, GROUP, OLD, STORIES, etc.)
- **Manage authors** — list of all detected post authors, uncheck to block
- **Manage groups** — list of all detected groups, uncheck to block
- **Post age filter** — hide posts older than a chosen threshold
- **Notification poll** — background notification checking interval (requires "Display over other apps" permission)
- **Remote filter** — toggle fetching filter.js from GitHub (off by default, uses bundled)
- **View log** — filter console messages and tracking events
- **Dump DOM** — logs DOM structure for debugging
- **Re-run filter** — manually re-apply the filter

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Load Facebook |
| `POST_NOTIFICATIONS` | Show Android notifications for Facebook notifications |
| `SYSTEM_ALERT_WINDOW` | Background notification polling (invisible overlay WebView) |

### Notification Polling Setup

1. Long-press shield → "Notification poll" → select interval
2. If prompted, enable "Display over other apps" in Android Settings → Apps → SlimBook
3. The app polls `m.facebook.com/menu/bookmarks/` in the background to check notification counts
4. When notifications are found, an Android notification appears; tapping it opens SlimBook to the notifications page

## Messenger

The messenger icon opens `www.messenger.com` with a desktop user agent. Press back or tap the Facebook icon to return to the feed. The "Send in Messenger" share option on posts also works, opening messenger.com with the post link pre-filled.

## How it works

Facebook serves a lightweight mobile web client called **WebLite** at `web.facebook.com`. Unlike the desktop site (which uses React/Relay/GraphQL), WebLite:

- Delivers ~333KB of server-rendered HTML (vs ~3.8MB desktop)
- Streams content updates via WebSocket (not GraphQL)
- Uses only 7 JS files (vs 63+ on desktop)
- Renders posts as flat HTML elements

SlimBook injects a small JavaScript filter after each page load. The filter identifies unwanted content by text patterns ("Ad", "Join", "Follow", "Create story", etc.) and hides the parent container elements.

## Remote filter updates

The filter rules live in [`filter.js`](filter.js) at the repo root. Remote fetching is **disabled by default** (uses the bundled filter). To enable:

1. Long-press shield → "Remote filter (off)" → toggles to on
2. Restart the app
3. On start, SlimBook will fetch the latest `filter.js` from GitHub

To update filter rules without releasing a new APK:
1. Edit `filter.js`
2. Push to `main`
3. Users with remote filter enabled will get updates on next app start

## Known Issues / TODO

- **Photo upload feedback** — photos upload successfully but WebLite doesn't show a preview in the composer. A toast confirms upload.
- **Follow/Join detection** — uses top-60px heuristic; nested shared posts with Join/Follow are preserved but occasionally a false positive slips through.
- **Background notifications** — requires "Display over other apps" permission for the overlay WebView approach.
- **Engagement tracking** — Facebook sends binary WebSocket frames tracking viewport/scroll. See [`docs/engagement-tracking.md`](docs/engagement-tracking.md) for analysis.

## License

[MIT](LICENSE)
