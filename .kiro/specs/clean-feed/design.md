# SlimBook Technical Design

## Architecture

```
┌──────────────────────────────────────────┐
│            SlimBook App                   │
├──────────────────────────────────────────┤
│  MainActivity                            │
│  ┌────────────────────────────────────┐  │
│  │  SwipeRefreshLayout                │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │         WebView              │  │  │
│  │  │  ┌────────────────────────┐  │  │  │
│  │  │  │  web.facebook.com      │  │  │  │
│  │  │  │  (WebLite client)      │  │  │  │
│  │  │  │                        │  │  │  │
│  │  │  │  ┌──────────────────┐  │  │  │  │
│  │  │  │  │ Filter JS        │  │  │  │  │
│  │  │  │  │ (MutationObserver│  │  │  │  │
│  │  │  │  │  + stats badge)  │  │  │  │  │
│  │  │  │  └──────────────────┘  │  │  │  │
│  │  │  └────────────────────────┘  │  │  │
│  │  └──────────────────────────────┘  │  │
│  └────────────────────────────────────┘  │
│                                          │
│  FilterManager (remote fetch + fallback) │
│  CookieManager (persistence)            │
│  DebugOverlay (stats badge, log viewer)  │
└──────────────────────────────────────────┘
```

## Components

### 1. MainActivity

Single-activity app with full-screen WebView inside SwipeRefreshLayout.

**Responsibilities:**
- Configure WebView settings
- Inject content filter JS after each page load
- Handle back navigation (WebView history)
- Manage cookie persistence
- Coordinate filter loading (remote → fallback)
- Open external links in system browser

### 2. WebView Configuration

```kotlin
webView.settings.apply {
    javaScriptEnabled = true
    domStorageEnabled = true
    userAgentString = MOBILE_USER_AGENT
    cacheMode = WebSettings.LOAD_DEFAULT
    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
}
```

Key settings:
- **User Agent:** Mobile UA to stay on web.facebook.com without redirect
- **DOM Storage:** Required for WebLite session state
- **JavaScript:** Required for WebLitePipe (WebSocket content delivery)

### 3. FilterManager

Manages filter JS loading with remote-first, fallback strategy.

```kotlin
class FilterManager(private val context: Context) {
    private val remoteUrl = "https://raw.githubusercontent.com/mretallack/SlimBook/main/filter.js"
    
    suspend fun getFilterJs(): String {
        return fetchRemote() ?: getBundledFallback()
    }
}
```

**Flow:**
1. On app start, fetch `filter.js` from GitHub (background thread)
2. If fetch succeeds, cache locally and use it
3. If fetch fails (offline, timeout), use bundled fallback from assets
4. Inject whichever JS is available into WebView after page load

### 4. Content Filter (filter.js)

Injected via `webView.evaluateJavascript()` after `onPageFinished()`.

The filter:
1. Queries all `[data-mcomponent="TextArea"]` elements
2. Matches text patterns (Ad, Create story, People you may know, Open app)
3. Walks up DOM to find appropriate parent MContainer by height range
4. Sets `display: none` (or red border in highlight mode) on matched containers
5. Marks filtered elements with `data-filtered` attribute
6. Reports stats back to Android via `console.log("SLIMBOOK_STATS:...")`

MutationObserver re-runs on every DOM change (WebSocket content arrival).

### 5. Debug Aids

#### Stats Badge
- Floating overlay (Android View on top of WebView)
- Shows: "🛡 Ads:1 Stories:1 Suggestions:1"
- Updates via console.log bridge from filter JS
- Tap to expand/collapse

#### Highlight Mode
- Toggle in a simple settings menu (long-press stats badge)
- Instead of `display:none`, filter applies `outline: 3px solid red; opacity: 0.5`
- Makes it easy to see what's being caught and what's slipping through

#### Console Log Bridge
- `WebChromeClient.onConsoleMessage()` captures filter debug output
- Messages prefixed with "SLIMBOOK:" are shown in a scrollable log overlay
- Accessed via long-press on stats badge

### 6. WebViewClient

```kotlin
override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
    val url = request.url.toString()
    return if (isFacebookUrl(url)) {
        false // let WebView handle it
    } else {
        // Open external links in system browser
        startActivity(Intent(Intent.ACTION_VIEW, request.url))
        true
    }
}

override fun onPageFinished(view: WebView, url: String) {
    view.evaluateJavascript(filterJs, null)
}
```

### 7. Cookie Persistence

```kotlin
CookieManager.getInstance().apply {
    setAcceptCookie(true)
    setAcceptThirdPartyCookies(webView, true)
}

// Flush on pause
override fun onPause() {
    super.onPause()
    CookieManager.getInstance().flush()
}
```

### 8. SwipeRefreshLayout

```kotlin
swipeRefresh.setOnRefreshListener {
    webView.reload()
}

// Clear refresh indicator when page finishes loading
override fun onPageFinished(view: WebView, url: String) {
    swipeRefresh.isRefreshing = false
    // ... inject filter
}
```

## Sequence Diagram

```
User              MainActivity       FilterManager    WebView          web.facebook.com
 │                    │                   │              │                     │
 │  Open app          │                   │              │                     │
 │───────────────────>│                   │              │                     │
 │                    │  getFilterJs()    │              │                     │
 │                    │──────────────────>│              │                     │
 │                    │                   │──── fetch github raw ────>         │
 │                    │  filterJs         │              │                     │
 │                    │<──────────────────│              │                     │
 │                    │  loadUrl()        │              │                     │
 │                    │─────────────────────────────────>│                     │
 │                    │                   │              │  GET / (mobile UA)  │
 │                    │                   │              │────────────────────>│
 │                    │                   │              │  HTML + WebSocket   │
 │                    │                   │              │<────────────────────│
 │                    │ onPageFinished    │              │                     │
 │                    │<─────────────────────────────────│                     │
 │                    │ evaluateJS(filter)│              │                     │
 │                    │─────────────────────────────────>│                     │
 │                    │                   │              │  Filter hides junk  │
 │                    │ console.log(stats)│              │                     │
 │                    │<─────────────────────────────────│                     │
 │                    │ update badge      │              │                     │
 │  Clean feed        │                   │              │                     │
 │<───────────────────│                   │              │                     │
```

## File Structure

```
SlimBook/
├── filter.js                          # Remote-updatable filter (fetched by app)
├── app/
│   ├── src/main/
│   │   ├── java/com/slimbook/app/
│   │   │   ├── MainActivity.kt       # WebView setup, navigation, filter injection
│   │   │   ├── FilterManager.kt      # Remote fetch + bundled fallback
│   │   │   └── DebugOverlay.kt       # Stats badge, highlight toggle, log viewer
│   │   ├── assets/
│   │   │   └── filter.js             # Bundled fallback filter
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   └── activity_main.xml # SwipeRefreshLayout > WebView + overlay
│   │   │   └── values/
│   │   │       ├── strings.xml
│   │   │       └── themes.xml        # Edge-to-edge, no action bar
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/
└── .gitignore
```

## URL Handling

Facebook URLs (stay in WebView):
- `*.facebook.com`
- `*.fbcdn.net`
- `*.fb.com`

Everything else → open in system browser via `Intent.ACTION_VIEW`.

## Error Handling

- **Login expired:** WebLite shows login page → user re-authenticates naturally
- **No network:** WebView shows default offline error
- **Filter fetch fails:** Use bundled fallback silently
- **Filter miss:** Unfiltered content is visible but functional (graceful degradation)

## Security Considerations

- No credentials stored by app (handled by WebView cookie storage)
- All traffic over HTTPS (enforced by Facebook)
- No data leaves the device beyond normal Facebook communication
- Filter runs client-side only, modifying DOM visibility
- Remote filter fetched over HTTPS from GitHub
