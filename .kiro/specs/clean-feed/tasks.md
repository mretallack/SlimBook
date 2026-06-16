# SlimBook Implementation Tasks

## Phase 1: Project Setup

- [x] 1. Create Android project structure (Kotlin, min SDK 26, single activity)
- [x] 2. Configure build.gradle.kts (no external dependencies)
- [x] 3. Create .gitignore for Android project
- [x] 4. Add INTERNET permission to AndroidManifest.xml

## Phase 2: Core WebView

- [x] 5. Create layout: SwipeRefreshLayout wrapping WebView
- [x] 6. Configure WebView settings (JS, DOM storage, mobile UA, cookies)
- [x] 7. Set up WebViewClient — keep Facebook URLs internal, external links to system browser
- [x] 8. Load `https://web.facebook.com/` on app start
- [x] 9. Handle back button for WebView history navigation
- [x] 10. Implement SwipeRefreshLayout pull-to-reload
- [x] 11. Enable cookie persistence via CookieManager, flush on pause

## Phase 3: Content Filter

- [x] 12. Create filter.js with full filter logic + MutationObserver + stats reporting
- [x] 13. Bundle filter.js in app/src/main/assets/ as fallback
- [x] 14. Create FilterManager.kt — fetch from GitHub raw URL, fall back to bundled
- [x] 15. Inject filter JS in onPageFinished callback

## Phase 4: Debug Aids

- [x] 16. Set up WebChromeClient to capture console.log messages prefixed with "SLIMBOOK:"
- [x] 17. Create DebugOverlay — floating stats badge showing filter counts
- [x] 18. Implement highlight mode toggle (red borders instead of hiding)
- [x] 19. Create log viewer overlay (scrollable list of SLIMBOOK: messages)
- [x] 20. Long-press stats badge to access highlight toggle + log viewer

## Phase 5: Polish

- [x] 21. Edge-to-edge theme (no action bar, translucent status bar)
- [x] 22. Set app icon and name ("SlimBook")
- [x] 23. Place filter.js in repo root for remote updates
- [ ] 24. Test on device with real Facebook account

## Build Status

✅ BUILD SUCCESSFUL — APK: `app/build/outputs/apk/debug/app-debug.apk` (3.1MB)
