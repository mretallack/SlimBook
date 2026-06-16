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
- 🔄 Pull-to-refresh
- 🔗 External links open in system browser
- 🍪 Persistent login (cookies survive app restarts)
- 🐛 Debug tools (highlight mode, DOM dump, console log viewer)
- 📡 Remote filter updates — filter rules can be updated without a new APK

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

- **Highlight mode** — shows filtered elements with red borders instead of hiding them
- **Dump DOM** — logs the structure of Join/Follow elements for debugging
- **View log** — shows filter console messages

## How it works

Facebook serves a lightweight mobile web client called **WebLite** at `web.facebook.com`. Unlike the desktop site (which uses React/Relay/GraphQL), WebLite:

- Delivers ~333KB of server-rendered HTML (vs ~3.8MB desktop)
- Streams content updates via WebSocket (not GraphQL)
- Uses only 7 JS files (vs 63+ on desktop)
- Renders posts as flat HTML elements

SlimBook injects a small JavaScript filter after each page load. The filter identifies unwanted content by text patterns ("Ad", "Join", "Follow", "Create story", etc.) and hides the parent container elements.

## Remote filter updates

The filter rules live in [`filter.js`](filter.js) at the repo root. On app start, SlimBook fetches the latest version from GitHub. If the fetch fails (offline), it uses the bundled fallback.

To update filter rules without releasing a new APK:
1. Edit `filter.js`
2. Push to `main`
3. Restart the app

## License

[MIT](LICENSE)
