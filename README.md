# MD Reader

A feature-rich Markdown reader & editor for Android, built with Jetpack Compose and Markwon.

<p align="center">
  <strong>Minimal. Fast. Offline-first.</strong>
</p>

---

## Features

### Reader

- **Native rendering** via Markwon 4.6.2 — tables, task lists, strikethrough, soft breaks, inline HTML, linkify
- **LaTeX math** — both `$$...$$` blocks and `$...$` inline with baseline-correct alignment and LRU cache (64 entries)
- **Progress tracking** — bottom 3dp progress bar + scroll-triggered "xx% · Chapter" pill (auto-hides after 1.2s)
- **Table of contents** — hierarchical chapter navigation with current-section highlight and tap-to-jump
- **In-document search** — highlighted matches, `1/4` counter, previous/next navigation, `Aa` case-sensitive toggle, search-term history (up to 8 terms, persisted via DataStore)
- **Long-press text selection** — custom long-press gesture → system text selection handle → copy/share
- **Pinch-to-zoom font size** — `ScaleGestureDetector` on the reading surface, range 0.75×–2.2×
- **Custom background color picker** — 24 presets (12 light paper, 12 dark night-reading) + HSV sliders + HEX input + live WCAG contrast-ratio hint. Foreground, accent, code-block, and table-header colors are all derived from the chosen background via `DerivedReaderColors` (luminance → ink/paper-text, accent tint, code planes)
- **Theme system** — 5 seed palettes (Blue/Green/Orange/Purple/Red), dynamic color or fixed palette, system/light/dark mode
- **Reading preferences** — font scale, line height (default 1.55), code-block theme (follow/light/dark), LaTeX toggle, keep-screen-on

### Editor

- **Syntax highlighting** — Sora Editor 0.23.6 with TextMate grammar (`markdown.tmLanguage.json`) + custom light/dark themes
- **Undo / Redo** — toolbar buttons + menu items
- **Formatting chips** — H1, H2, H3, H4, blockquote, list, task list
- **Regex find & replace** — `TYPE_REGULAR_EXPRESSION` search mode
- **Word / character count** — live status bar
- **Inline preview** — toggle rendered Markdown overlay via menu

### File Management

- **SAF (Storage Access Framework)** — open, save, and save-as via system file picker; no broad storage permissions needed
- **Atomic writes** — content is written to a local temp file (tmp + rename) before copying to the destination URI, so a complete, valid copy always exists even if the URI write is interrupted
- **Recent files** — persisted with title, size, last-opened time, reading progress, anchor position, and pin state
- **Pin & search** — pinned files sort to the top; home screen search filters by title
- **Draft recovery** — unsaved edits are cached locally (up to 5 MB); draft badge shown on home; untitled new documents get their own draft key
- **Cache for offline** — each opened file is cached to app-private storage so recents can reopen instantly without SAF permission

### Sharing & Export

- **Share Markdown source** — via Android share sheet
- **Share rendered HTML** — commonmark `HtmlRenderer` with inline CSS, exported via FileProvider
- **Image save/share** — tap a rendered image for fullscreen preview + save-to-gallery + share

### Intent Handling

- Opens `.md` / `.markdown` / `.mdown` / `.mdtxt` files from any app (QQ, WeChat, file manager, etc.)
- Handles `ACTION_VIEW` for `text/plain`, `text/markdown`, `text/x-markdown`, `application/octet-stream`
- Handles `ACTION_SEND` / `ACTION_SEND_MULTIPLE` for shared text and file attachments

---

## Architecture

```
MainActivity (Compose, single-Activity)
 └─ NavHost
     ├─ HomeScreen          — recent files, search, pin, FAB
     ├─ ReaderScreen        — Markwon rendering, progress, search, TOC
     ├─ EditorScreen        — Sora editor, chips, find/replace, preview
     └─ SettingsScreen      — theme, palette, reader prefs, about

MdViewModel (shared state)
 ├─ Document state (Ready / Error / Loading)
 ├─ RecentFilesRepository  (JSON file in filesDir)
 ├─ SettingsRepository     (DataStore Preferences)
 └─ DraftStore             (JSON files in filesDir/drafts)

Rendering pipeline (no coroutines)
 └─ Dedicated single-thread Executor → Main Handler callback
    (avoids kotlinx-coroutines resumption loss with Compose)
```

### Key Design Decisions

| Decision | Rationale |
|---|---|
| Executor + Handler instead of coroutines for rendering | kotlinx-coroutines 1.7.3 `withContext(Dispatchers.Default)` + Compose had silent resumption loss causing blank screens |
| Markwon + Sora Editor | Lightweight native rendering, no WebView; Sora provides TextMate grammar support |
| Atomic write (tmp + rename → copy to URI) | SAF has no atomic replace; this ensures a valid local copy always exists |
| DataStore for settings | Type-safe, lifecycle-aware, survives app restart |
| SHA-256 keyed disk cache for images | Content-addressed, no eviction logic needed for typical doc sizes |
| No WebView | Faster startup, smaller APK, better battery life |

---

## Tech Stack

| Layer | Library | Version |
|---|---|---|
| UI | Jetpack Compose + Material 3 | 1.7.6 / 1.3.1 |
| Navigation | Navigation Compose | 2.8.5 |
| State | ViewModel + Lifecycle Compose | 2.8.7 |
| Persistence | DataStore Preferences + JSON files | 1.1.1 |
| Markdown rendering | Markwon (core, tables, latex, tasklist, strikethrough, image, html, linkify, inline-parser) | 4.6.2 |
| Editor | Sora Editor + TextMate | 0.23.6 |
| HTML export | commonmark (via Markwon transitive dep) | 0.13.0 |
| Build | AGP 8.9.1 + Kotlin 2.0.21 | — |

**Requirements**: Android 8.0+ (API 26), Java 17, Android SDK 36

---

## Project Structure

```
app/src/main/java/com/yumao/mdreader/
├── MainActivity.kt              — single Activity, Compose root
├── MdReaderApp.kt               — Application class
├── MdViewModel.kt               — shared ViewModel (navigation, documents, recents)
├── data/
│   ├── AppSettings.kt           — settings model (theme, reader, editor prefs)
│   ├── SettingsRepository.kt    — DataStore CRUD + search history
│   ├── RecentFile.kt            — recent file model (JSON serialization)
│   └── RecentFilesRepository.kt — recents persistence + pin/sort
├── reader/
│   └── ReaderScreen.kt          — reading UI, progress, search, TOC, color picker
├── editor/
│   ├── EditorScreen.kt          — editor UI, chips, find/replace, preview
│   ├── EditorActions.kt         — format/insert helpers
│   └── SoraSetup.kt             — Sora theme + grammar configuration
├── home/
│   └── HomeScreen.kt            — home screen, recent list, search
├── settings/
│   └── SettingsScreen.kt        — theme/palette/reader/editor settings
├── markdown/
│   ├── MarkwonFactory.kt        — Markwon builder + code highlight plugin
│   ├── MarkdownPalette.kt       — color palette data class
│   ├── DerivedReaderColors.kt   — foreground/accent derivation from bg color
│   ├── CachedNetworkSchemeHandler.kt — SHA-256 disk-cached image loader
│   ├── ContentSchemeHandler.kt  — content:// URI image loader
│   ├── InlineLatexPlugin.kt     — $...$ inline math with baseline alignment
│   ├── InlineLatexPatterns.kt   — LaTeX regex patterns
│   └── TextSearch.kt            — search match finding + highlighting
├── files/
│   ├── DocumentState.kt         — document model
│   ├── FileIo.kt                — read/write/cache/file utilities
│   └── DraftStore.kt            — draft persistence
├── ui/
│   ├── AppTheme.kt              — Material 3 theme setup
│   ├── ErrorState.kt            — shared error UI component
│   ├── MarkdownBridge.kt        — Markwon ↔ Compose bridge
│   └── ReaderColorPicker.kt     — background color picker dialog
└── assets/textmate/             — TextMate grammar + theme files
```

---

## Build

### Prerequisites

- JDK 17
- Android SDK 36 (compileSdk)
- Android Gradle Plugin 8.9.1

### Build APK

```bash
# Debug
./gradlew assembleDebug

# Release (requires keystore.properties)
./gradlew assembleRelease
```

### Signing

Create `keystore.properties` at the project root:

```properties
storeFile=release.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_key_password
```

The release APK is ~3.8 MB (R8 + resource shrinking enabled).

### Run Tests

```bash
./gradlew test
```

---

## APK

| Variant | Size |
|---|---|
| Release | 3.78 MB |
| Debug | 29.79 MB |

---

## License

MIT
