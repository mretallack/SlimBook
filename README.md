# SlimBook

A lightweight Android app that provides a clean, ad-free Facebook feed by wrapping Facebook's mobile web client (`web.facebook.com`) with content filtering.

## What it does

SlimBook loads Facebook's lightweight WebLite interface and automatically removes:

- **Ads** and sponsored posts
- **Stories** section
- **Posts from unfollowed pages** (header shows "Follow")
- **Posts from unjoined groups** (header shows "Join")
- **Posts from non-friends** (header shows "Add friend")
- **"People you may know"** suggestions
- **"Open app"** banners
- **"Are you interested?"** prompts

The filter runs continuously via a MutationObserver, catching new content as it arrives over Facebook's WebSocket connection.

## Features

- 🛡️ Real-time content filtering with stats badge
- 🌍 Works with Facebook in 35 languages (see [Languages](#languages))
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

Long-press the stats badge (bottom-right corner, auto-hides while typing) to access:

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

## Languages

Facebook shows its interface in your account's language, so the filter keeps a table of the labels it looks for ("Sponsored", "Join", "Follow", "Add friend", …) in each language. The table is `LABELS` at the top of [`filter.js`](filter.js) and currently covers:

Arabic, Bengali, Bulgarian, Chinese (Simplified and Traditional), Croatian, Czech, Danish, Dutch, English, Finnish, French, German, Greek, Hebrew, Hindi, Hungarian, Indonesian, Italian, Japanese, Korean, Malay, Norwegian, Persian, Polish, Portuguese, Romanian, Russian, Serbian, Slovak, Spanish, Swedish, Thai, Turkish, Ukrainian and Vietnamese.

Only English has been checked against a live feed. The other languages were written from Facebook's known translations and have **not** been verified on WebLite, so some labels may be wrong or missing. Matching ignores case, accents and surrounding "·" separators, so only the wording has to be right.

**If something is not filtered in your language:**

1. Long-press the stats badge → turn on **Highlight mode** to see what is and is not being caught.
2. Note the exact text Facebook shows on the item that got through (for example the word under the page name on an ad).
3. Add it to the matching category for your language in `LABELS` — or open an issue with the text and the language.

The post age filter still only understands English timestamps ("3h", "2d", "5 Jun"); in other languages it leaves posts alone.

## Remote filter updates

The content filter logic lives in [`filter.js`](filter.js) at the repo root. A copy is **bundled inside the APK** (`assets/filter.js`) and used by default — no network requests are made beyond Facebook itself.

### What the remote filter does

When enabled, the app downloads `filter.js` from this GitHub repository on each app start:

```
https://raw.githubusercontent.com/mretallack/SlimBook/main/filter.js
```

The downloaded script is then **executed inside the WebView** to filter Facebook content. This allows filter rules to be updated without releasing a new APK.

### Privacy implications

- **Disabled (default):** All communication is between your device and Facebook's servers only. The bundled filter is used.
- **Enabled:** The app additionally connects to GitHub (`raw.githubusercontent.com`) to fetch the filter script. The script runs in the same WebView context as Facebook.

### How to enable

1. Long-press the stats badge → "Remote filter (off)"
2. A confirmation dialog explains what will happen
3. Tap "Enable", then restart the app

### Updating filter rules (for maintainers)

To update filter rules without releasing a new APK:
1. Edit `filter.js`
2. Push to `main`
3. Users with remote filter enabled will get updates on next app start

## Known Issues / TODO

- **Photo upload feedback** — photos upload successfully but WebLite doesn't show a preview in the composer. A toast confirms upload.
- **Follow/Join detection** — uses top-60px heuristic; nested shared posts with Join/Follow are preserved but occasionally a false positive slips through.
- **Background notifications** — requires "Display over other apps" permission for the overlay WebView approach.
- **Engagement tracking** — Facebook sends binary WebSocket frames tracking viewport/scroll. See [`docs/engagement-tracking.md`](docs/engagement-tracking.md) for analysis.


## Release & Publishing

### F-Droid / GitHub Releases

SlimBook is designed to be published via GitHub Releases and F-Droid. To release a new version:

1. **Write the Fastlane changelog first.** Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`, where `<versionCode>` is the **new** version code you are about to set, not the version name. F-Droid reads this file from the **tagged commit**, so it must be committed and pushed *before* the tag is created — otherwise the release ships with no release notes and the only way to fix it is to move the tag (see "Do not move a published tag" below).
2. **Update version code and version name** in `app/build.gradle.kts` (increment `versionCode`, update `versionName`).
3. **Commit and push** changes to `main`.
4. **Create a GitHub Release / Tag:**
   - Go to your repository on GitHub → **Releases** → **Draft a new release**.
   - Create a new tag matching the version (e.g., `v1.2.1`).
   - Write release notes and publish the release.
   - *GitHub Actions will automatically build, sign using repository keystore secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), and attach `app-release.apk` to the release.*
   - **Check the Actions run went green** before moving on — the `release` job only runs if `build`, `lint` and `test` all pass, and a missing signing secret fails *after* the release is public.
5. **Verify the attached APK** before telling anyone the release is done:
   ```bash
   curl -sL -o /tmp/release.apk \
     https://github.com/mretallack/SlimBook/releases/download/v<version>/app-release.apk
   $ANDROID_HOME/build-tools/<ver>/aapt2 dump badging /tmp/release.apk | head -1   # versionCode/versionName
   $ANDROID_HOME/build-tools/<ver>/apksigner verify --print-certs /tmp/release.apk # must match AllowedAPKSigningKeys
   ```
6. **F-Droid Metadata Update (`fdroiddata`):**

   > **Check for an automatic MR first (untested).** The metadata sets
   > `AutoUpdateMode: Version` with `UpdateCheckMode: Tags`, which is supposed to
   > let the `checkupdates-bot` detect a new tag and open a metadata MR by itself —
   > see `fdroid/fdroiddata` MRs authored by `checkupdates-bot`.
   >
   > **In practice this has not fired for SlimBook.** Across `v1.2.0` and `v1.2.1`
   > no bot MR appeared, while the bot was actively opening them for other apps
   > (100+ in a single month, including this author's ClayModeller and ONVIF
   > Camera). The only SlimBook bot MR ever raised was `!50330` for 1.1.0, which
   > was closed because a manual MR (`!50827`) superseded it. **Cause unknown** —
   > SlimBook is the only one of these apps with a `Binaries:` line, so that
   > interaction is a plausible candidate, but it has not been confirmed.
   >
   > So: **look** for a bot MR before authoring one by hand, but do not wait for
   > it. If one appears and its `commit:` matches the tag, merge the bot's instead
   > of writing your own — that's less work and is the intended workflow.
   >
   > Worth revisiting: if the bot ever does fire reliably, this whole manual step
   > (and the reproducibility-verification constraints that come with `Binaries:`)
   > could be dropped for routine releases.

   Since SlimBook uses prebuilt binaries (`Binaries:`), each new release requires updating the F-Droid metadata repository (`fdroiddata`).
   The `commit:` for the new build must be the commit the release tag points at — confirm with `git rev-list -n1 v<version>` rather than assuming it is the bump commit, as it may include later commits such as changelog additions.
   - **Update local `fdroiddata` repository from upstream:**
     ```bash
     cd ../fdroiddata
     git checkout master
     git fetch upstream
     git reset --hard upstream/master
     ```
   - **Create a working branch:**
     ```bash
     git checkout -b update-slimbook-v1.2.1
     ```
   - **Edit metadata file (`metadata/uk.org.retallack.slimbook.yml`):**
     Add the new version entry under `Builds:` with its corresponding release tag commit hash, and update `CurrentVersion` / `CurrentVersionCode`.
   - **Run F-Droid linter / rewritemeta:**
     `fdroid rewritemeta` and `fdroid lint` take the **app id**, not the file path:
     ```bash
     fdroid rewritemeta uk.org.retallack.slimbook
     fdroid lint uk.org.retallack.slimbook
     ```
   - **Commit and push:**
     ```bash
     git add metadata/uk.org.retallack.slimbook.yml
     git commit -m "SlimBook: update to v1.2.1"
     git push -u origin update-slimbook-v1.2.1
     ```
   - Open a Merge Request on GitLab against `fdroid/fdroiddata`. If a previous MR for the same app is still open, open the new one as a **Draft** until the release is final, so nothing is built from a tag you may still change.

### Do not move a published tag

Deleting a tag that a published GitHub Release points at **detaches the release and silently converts it back to a draft**, at an `untagged-…` URL. It is not visible in the Releases list as published any more, and the workflow is not re-triggered. Recovering means deleting the orphaned draft and creating the release again from scratch.

Tags are effectively immutable once released. If something is wrong with a release, prefer a new patch version (e.g. `v1.2.2`) over moving the existing tag:

- Moving the tag produces a **different APK** (non-reproducible builds), so any verification already done no longer applies to the artifact being shipped.
- F-Droid may already have fetched the old asset against the recorded commit, creating a mismatch between the metadata `commit:` and the tag.
- Reinstalling a differently-signed-but-same-key APK is fine with `adb install -r`, but the artefact you tested is no longer the artefact you ship.

The only exception is a **draft** F-Droid MR where no build has run yet, and a GitHub release you are willing to delete and recreate.

### Version code and Fastlane changelogs

`versionCode` must increase by one for every release and is what Fastlane uses for changelog filenames. Keep them in step:

| `versionCode` | changelog file |
|---|---|
| 1 | `changelogs/1.txt` |
| 4 | `changelogs/4.txt` |

A missing file means that version shows no release notes on F-Droid. Gaps are harmless but pointless — check the `changelogs/` directory covers every version before tagging.

### Debug vs release builds on a device

The release APK is signed with the release keystore; local `assembleDebug` builds are not. Installing one over the other fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, and the only fix is to uninstall first — which wipes app data including the WebView cookie store, i.e. the user's Facebook login.

`adb install -r` succeeds only when the signing key is unchanged, so **release → release** updates are safe and preserve the session. Prefer keeping a release build on test devices.

## License

[MIT](LICENSE)
