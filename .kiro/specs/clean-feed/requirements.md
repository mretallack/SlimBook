# SlimBook Requirements

## Overview

SlimBook is an Android app that provides a clean, ad-free Facebook feed experience by wrapping Facebook's lightweight mobile web client (`web.facebook.com` / WebLite) in a WebView with content filtering.

## Background

Facebook serves a lightweight mobile web client at `web.facebook.com` when accessed with a mobile user agent. This "WebLite" client:
- Uses server-rendered HTML with MComponent framework (not React/Comet)
- Streams content updates via WebSocket (`WebLitePipe`) instead of GraphQL
- Delivers ~333KB initial HTML (vs ~3.8MB for desktop facebook.com)
- Loads only 7 JS files (vs 63+ for desktop)
- Uses `z-m-static.xx.fbcdn.net` mobile-optimized CDN
- Renders posts as flat HTML with `data-mcomponent` and `data-action-id` attributes

The WebSocket connects to `kaios-d.facebook.com:443` / `kaios-z.facebook.com:443` and streams new content as binary-encoded messages decoded client-side.

## User Stories

### US-1: Clean Feed Viewing

As a user, I want to browse my Facebook feed without ads, suggested posts, or stories so that I only see content from people and pages I follow.

**Acceptance Criteria:**
- WHEN the feed loads THE SYSTEM SHALL hide all sponsored/ad posts (identified by "Ad" marker text in post attribution)
- WHEN the feed loads THE SYSTEM SHALL hide the stories section (horizontal scroller / "Create story" thumbnail area, height 190-250px)
- WHEN the feed loads THE SYSTEM SHALL hide "People you may know" / "Suggested for you" sections
- WHEN the feed loads THE SYSTEM SHALL hide the "Open app" banner
- WHEN new content arrives via WebSocket THE SYSTEM SHALL apply the same filtering rules automatically
- WHEN the user navigates to any Facebook page THE SYSTEM SHALL continue filtering ads and suggestions

### US-2: Authentication

As a user, I want to log in once and stay logged in so I don't have to re-authenticate each time.

**Acceptance Criteria:**
- WHEN the user first opens the app THE SYSTEM SHALL display the Facebook login page at web.facebook.com
- WHEN the user successfully logs in THE SYSTEM SHALL persist cookies across app restarts
- WHEN the app is reopened THE SYSTEM SHALL restore the session using saved cookies
- WHEN a session expires THE SYSTEM SHALL allow the user to re-authenticate naturally in the WebView

### US-3: Navigation

As a user, I want to navigate Facebook's core features while maintaining the clean experience.

**Acceptance Criteria:**
- WHEN the user taps a post THE SYSTEM SHALL navigate to the post detail page
- WHEN the user taps a profile THE SYSTEM SHALL navigate to the profile page
- WHEN the user taps notifications THE SYSTEM SHALL show the notifications page
- WHEN the user taps the back button THE SYSTEM SHALL navigate back in WebView history
- WHEN the user taps an external link (non-facebook.com) THE SYSTEM SHALL open it in the system browser
- WHEN the user pulls down THE SYSTEM SHALL reload the page (SwipeRefreshLayout)

### US-4: Debug Aids

As a developer, I want tools to diagnose when content filtering breaks.

**Acceptance Criteria:**
- THE SYSTEM SHALL display a floating stats badge showing filter counts (ads, stories, suggestions removed)
- THE SYSTEM SHALL provide a "highlight mode" that outlines filtered elements in red instead of hiding them
- THE SYSTEM SHALL bridge WebView console.log messages to an in-app log viewer

### US-5: Remote Filter Updates

As a developer, I want to update filter rules without releasing a new APK.

**Acceptance Criteria:**
- WHEN the app starts THE SYSTEM SHALL fetch the latest filter JS from `https://raw.githubusercontent.com/mretallack/SlimBook/main/filter.js`
- WHEN the remote fetch fails THE SYSTEM SHALL use the bundled fallback filter
- WHEN the remote filter is newer THE SYSTEM SHALL use it immediately

## Content Filtering Rules

### Ads Detection
- **Pattern:** Text area element (`data-mcomponent="TextArea"`) with height < 25px containing "Ad" (not "Add " or "Ads Manager") and total text length < 80 chars
- **Action:** Hide the parent MContainer with height 200-900px

### Stories Detection
- **Pattern:** Text area containing exactly "Create story"
- **Action:** Hide the parent MContainer with height 190-250px

### Suggestions Detection
- **Pattern:** Text area containing exactly "People you may know" or "Suggested for you"
- **Action:** Hide the parent MContainer with height 200-600px

### App Banner Detection
- **Pattern:** Text area containing exactly "Open app"
- **Action:** Hide 3 levels up from the text element

### Continuous Filtering
- A MutationObserver on `document.body` with `{ childList: true, subtree: true }` re-runs filtering whenever DOM changes (triggered by WebSocket content delivery)

## Design Decisions

| Decision | Choice |
|----------|--------|
| Session expiry | Let user re-login naturally in WebView |
| Filter updates | Remote fetch from GitHub repo, bundled fallback |
| Debug aids | Stats badge + highlight mode + console log bridge |
| External links | Open in system browser |
| Pull to refresh | SwipeRefreshLayout → webView.reload() |
| Notifications | None — in-page badge only |
| Dark mode | Let Facebook handle it |
| Distribution | Local APK build, sideload |
| Filter scope | Filter everywhere (all pages) |
| Remote filter host | `raw.githubusercontent.com/mretallack/SlimBook/main/filter.js` |

## Constraints

- Target Android API 26+ (Android 8.0+)
- Single activity app
- No external dependencies beyond Android SDK
- User agent must be mobile: `Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36`
