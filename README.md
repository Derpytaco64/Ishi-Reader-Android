# Ishi Reader (Android)

A native Android client for [Ishi-Read](https://github.com/Derpytaco64/Ishi-Read) — because the mobile web experience on the Ishi-Read site is broke as fuck. This app talks to your self-hosted Ishi-Read server and gives EPUB reading and audiobook listening a proper native home on Android.

## Required companion server

This app is a **client only** — it needs a running Ishi-Read server to log into, sync against, and stream/download books from. You cannot use this app without one.

- **Ishi-Read (server):** https://github.com/Derpytaco64/Ishi-Read

Stand up an instance (or point at one you already run) before installing this app.

## Built on

- **[Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit)** — the EPUB parsing/rendering/navigation engine underneath the reader (`readium-shared`, `readium-streamer`, `readium-navigator`). Ishi Reader's `ReaderActivity` is built directly against Readium's navigator APIs (pagination, selection, decorations, TTS-adjacent locator handling), and this project's GPLv3 licensing follows from that dependency.
- **[Ishi-Read](https://github.com/Derpytaco64/Ishi-Read)** — the Next.js server this app is a client for. Several Android features are direct ports of the website's UX (custom shelf emoji, the `library-prefs` settings blob convention, the stats dialog, annotation/notes behavior), kept at feature parity with the site by design.

### Other core open-source libraries

| Library | Purpose |
|---|---|
| [Jetpack Compose](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/compose/) | UI toolkit for the entire app |
| [AndroidX Media3 (ExoPlayer)](https://github.com/androidx/media3) | Audiobook playback engine + media notification/lock-screen controls |
| [AndroidX Room](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/room/) | On-device cache / local-first data store |
| [AndroidX WorkManager](https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-main/work/) | Background sync of pending offline changes |
| [Retrofit](https://github.com/square/retrofit) + [OkHttp](https://github.com/square/okhttp) | HTTP client / API layer |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) + [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | JSON models and async/concurrency |
| [Coil](https://github.com/coil-kt/coil) | Cover image loading/caching |
| [Timber](https://github.com/JakeWharton/timber) | Logging (also used internally by Readium) |

## Features

### Library & organization
- Full library grid synced from the Ishi-Read server, with search
- Custom shelves with reordering and emoji icons (parity with the website's shelf convention)
- Series view with auto-scroll to the most recently read entry in a series
- Sort modes and persisted library display/accent preferences
- Book detail view: cover viewer, page count, tag/chip reordering, expandable Annotations section, Completed Reads section, current-read management

### Reading (EPUB)
- Full Readium-powered EPUB navigator: pagination, scrollbar/scroll view toggle, adjustable margins, font size, and other reader display settings (ported from the website's reader settings)
- Position retention across app restarts, rotation, and device changes, with time-based position resolution and a "return to position" bar (with dismiss)
- Table of contents panel with page numbers
- Dynamic page count / page numbers tracked per book
- Screen rotation lock and a manual rotation toggle
- Volume-button page turning
- Tap-to-show chrome, with header/footer chrome that fades together with the return-to-position bar
- Camera-notch and system-nav-bar inset handling for edge-to-edge devices

### Annotations
- Highlighting with selectable highlight colors, plus bookmarking and note-taking from the text-selection menu
- Annotations panel with jump-to-location (with a flash/highlight animation on jump) and copy-to-clipboard
- Notes rendered as Markdown, matching how notes render on the website
- Selectable, copyable text throughout the book detail and annotation views

### Dictionary / lookup
- Android-only "Dictionary" action in the text-selection menu (no website equivalent) — sends the selected passage to any installed dictionary/translator app that supports Android's standard `ACTION_PROCESS_TEXT`, configurable per-device in Reader Settings

### Audiobooks
- Dedicated audiobook player (Media3/ExoPlayer-based) with lock-screen and notification transport controls
- Chapter-aware scrub bar with chapter tick marks and a chapter list/jump view
- Square audiobook cover art handling
- Daily listening-session tracking and a "current listen" sessions view

### Reading stats & timers
- Reading timer with a dedicated timer sheet
- Current-read sessions section showing recent reading activity
- User stats screen (mirrors the website's `/api/userdata/stats` stats dialog), with a color-wheel accent picker

### Offline support
- Book downloads for offline reading, with per-book download/sync status rings in the library
- Delete-local-file option for downloaded EPUBs and audiobooks
- Full offline app entry: library, shelves, stats, and user icon all work without a connection
- Local-first sync layer: Room is the source of truth on-device, with a WorkManager-driven pending-sync outbox that drains once connectivity returns
- Book/library-prefs migration tooling for reconciling local and server state

### Image viewer
- Full-screen image viewer overlay (covers and in-book images) with pan/zoom and tap-to-close behavior

### Account, admin & app-wide
- Login screen with light/dark mode support
- Admin panel
- Settings drawer (logo, user icon, library accent color, offline/library preferences)
- App-wide dark mode, gold accent theming, and a custom app icon/splash screen

## Requirements

- Android 8.0 (API 26) or newer
- A reachable [Ishi-Read](https://github.com/Derpytaco64/Ishi-Read) server to log into

## License

GNU General Public License v3.0 — see [LICENSE](LICENSE).
