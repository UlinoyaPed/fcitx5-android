# Swan Input Method

[简体中文](./README.md)

Swan Input Method is a deeply customized Android IME based on [Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android).  
The current app name is `天鹅输入法`, and the main package name is `org.fxboomk.fcitx5.android`.

This fork mainly strengthens two areas:

- Clipboard workflow: it turns local clipboard, cross-device clipboard, and image/file clipboard handling into a more complete workflow.
- Prediction and extensibility: it keeps the Fcitx5 multi-engine architecture while further improving extensible scenarios such as the RIME plugin.

## Recent Updates

This summary covers the main changes from October 6 through October 17, 2026:

- Candidate window and physical-keyboard integration are more complete:
  - The floating candidate window gains a "Smart" position that chooses where to appear based on the cursor area, and a vertical window can flip above the cursor; when a physical keyboard drives the candidate bar, items show 1-9 selection-key hints.
  - Physical-keyboard digits commit the numbered AI prediction or calculator result, Space commits the currently visible candidate or first prediction, and after arrows move the candidate highlight, shortcuts such as choose character from phrase ("以词定字") and reset frequency act on that highlighted item.
  - Calculator results now also appear in the floating candidate window; collaboration between the floating window, toolbar, and physical-keyboard candidate bar is more stable, including when hiding the keyboard or handling invalid cursor anchors.
- Swipe gestures and key behavior are more refined:
  - MacroKey swipe macros can be configured per input state (no code/no candidates, no code/candidates, code/candidates, code/no candidates, has code (all), and All), resolved by preferring an exact state, then has-code (all), then All; an explicitly empty action blocks the fallback.
  - Timing editing now uses cards: one card can cover several states, cards can be added from a bottom-right FAB, retimed from the card header, and deleted after confirmation; the "Swipe Event" editor remains compatible with legacy configuration.
  - A new "Symbol swipe mapping" lets you configure half-width symbols, their Chinese full-width forms, and Caps uppercase forms separately; when Caps is on, key faces also show uppercase swipe symbols.
  - New "Auto-close paired symbols" and "Insert space between Chinese and English" toggles were added; paired symbols support caret placement and paired backspace, hardware backspace also triggers paired deletion, and the spacing option covers preedits committed with Return.
  - The layout editor now marks real alt/alt1 changes as customized.
- Settings, schemes, and input engines:
  - Key behavior settings were regrouped so "Keys and gestures" keeps only gesture-related items; the Rime scheme picker is now a radio-button single-choice list with the active scheme checked, and related punctuation-swipe and language-key labels were unified.
  - The popup and symbol-swipe mapping editors use a circular bottom-right FAB to add entries, and the last row remains scrollable above the button.
  - The built-in zh_CN KenLM language model was upgraded from 4-bit to 8-bit quantization, reducing quantization error and prediction-table entry loss, with reproducible build scripts; the table engine, its data, and related settings were removed entirely, so built-in Chinese input keeps only Pinyin and Shuangpin.
- Build and stability:
  - Build tasks are compatible with the Gradle 10 task DSL; native install tasks sharing a CMake build tree are serialized to avoid concurrent regeneration races with the configuration cache enabled.
  - Key, row, and macro editor dialogs preserve unsaved edits across screen rotation.

## Highlights

### 1. Stronger clipboard workflow

- Clipboard categories in the main app:
  - Local
  - Other Devices
  - Images & Files
  - All
- Image entries can show thumbnails.
- URL entries support an `Open link` context action.
- Image entries support a `View image` context action via the system default app.
- Image and file clipboard items are preserved as URI-based entries whenever possible, so file sending works beyond plain text.
- Long-text clipboard entries (more than 10 characters) support tokenized/chunked presentation for easier browsing and reuse.
- Clipboard history limits can be configured per category.
- Clipboard category switching and search refresh are more stable, reducing stale results when changing categories or reopening the search overlay.

### 2. Clipboard sync plugin

The project already integrates the `clipboard-sync` plugin build chain and can build/install it together with the main app.

Currently supported clipboard sync backends:

- [`OneClip`](https://oneclip.cloud/)
- `ClipCascade`
- [`SyncClipboard`](https://github.com/Jeric-X/SyncClipboard)

Plugin capabilities include:

- Automatically pulling desktop clipboard records to Android.
- Supporting multiple clipboard content types including text, images, and files.
- Pushing Android clipboard content back to the server.
- Manually uploading clipboard entries to the server when on-demand sync is needed.
- Background keep-alive, reconnect logic, foreground service mode, and quick settings tile control.
- OneClip pull, push, image, and file downloads support access tokens.
- File sync can save to the configured directory while preserving Unicode characters such as Chinese text in filenames where possible.
- Sync filters for:
  - text length
  - file extensions
  - maximum file size
- Built-in settings for connection testing, push testing, and system clipboard permission guidance.

If your workflow is “copy on desktop, input on phone” or “copy on phone, receive on desktop”, this fork treats it as a core feature.

### 3. Prediction and input-method extensibility

- Keeps the original Fcitx5 multi-language input framework.
- Built-in Chinese input keeps only Pinyin and Shuangpin; table-based input methods, their code tables, and the table addon have been removed from this fork.
- RIME plugin support remains available for custom schemas, dictionaries, and advanced configuration.
- Prediction, suggestion, and language-model related capabilities from libime and the Chinese plugin stack are preserved.
- The built-in zh_CN KenLM has been upgraded from 4-bit to 8-bit quantization, reducing quantization error and prediction-table entry loss; `scripts/libime-lm` provides reproducible model build and verification scripts.
- The keyboard pulls up ready to type: the fcitx engine stays warm, so returning to the IME or leaving the settings screen no longer repeats Rime initialization and data sync.
- The input hot path has been through several performance passes: fewer Binder round trips per keystroke, fewer redundant candidate renders, and a raised fcitx thread priority.
- [`librime` (the Rime plugin)](https://github.com/boomker/librime) prediction capability has been enhanced so it can learn from user input history and supports backup of prediction data.
- AI prediction and Q&A capabilities have been added and expanded, supporting multiple model providers with dedicated handling for streaming responses, failure fallback, and request throttling.
- The AI provider list now includes options such as Moonshot.
- AI candidates support an expanded candidate window for browsing more prediction results.
- The floating candidate window supports smart positioning and flipping above the cursor; the physical keyboard can select visible AI/calculator candidates with digits, commit the currently visible candidate or first prediction with Space, and route shortcuts to the arrow-highlighted item.
- Calculator results appear in both the horizontal candidate bar and the floating candidate window.
- On the virtual keyboard, AI predictions and native/Rime candidates follow the same prediction-space behavior; on a physical keyboard, Space prioritizes the currently visible candidate or first prediction.
- Swiping a digit-key alternate character selects the matching candidate when candidates are visible and still commits the digit otherwise; on a physical keyboard, Rime candidates can be navigated with the arrow keys.
- Horizontal candidate bars support independent item-spacing and highlight styling, with correct refreshes when paging.
- The manually selected keyboard layout is preserved after an input-method restart, reducing unexpected fallback to the default layout.
- Candidate interaction is enhanced: swipe up to pick a character, swipe down to reset frequency, and use an explicit cancel area outside the candidate region; the expanded candidate window uses a more compact adaptive grid. Long-pressing a candidate commits its first or last character; decompose and reset-frequency now require a long press before dragging up/down, which avoids accidental triggers and frees plain up/down drags for scrolling the expanded candidate window.
- The keyboard layer keeps gaining configurable features: MacroKey (including physical-direction up/down swipe macros), a customizable uppercase-key toggle whose lock state survives keyboard rebuilds and layout switches, Shift behavior switches, and popup gesture candidate selection.

### 4. Keyboard layout and popup preset sharing

- A standalone "Keyboard layout management" page centralizes all layouts (set-as-default, move-up ordering, collapse, CRUD, and QR import/share), while the layout editor focuses on content editing with a live preview and is reachable via deep links from the manager; ordering and collapse state are persisted.
- Text keyboard layouts and popup presets can be shared via QR codes.
- Shared data can be imported either by camera scanning or from a file.
- QR images can be previewed before sharing so you can verify the content first.
- Text keyboard layout JSON also supports direct key color configuration, making it easier to share complete visual layout presets.
- Text keyboard layout JSON supports separate portrait and landscape keyboard-height settings for each layout.
- Rime input schemes (submodes) can set their own keyboard height, applied immediately when switching schemes and reflected in the layout preview.
- Keyboard height supports three-level overrides that cascade in order: profile < base layout < submode.
- Opening the layout editor no longer switches the input method you are actually using (it reads the deployed Rime schema_list instead), and previewing a Rime layout uses the Rime engine's own identity, space-key label, and icon, with the preview background matching exactly one keyboard height.
- The keyboard layout editor can move a complete row upward, and alternate characters for custom keys preserve their original text.
- The layout manager shows the active “profile - input method - Rime schema” chain and can hide base layouts that have not been customized; groups with customized child layouts remain visible.
- MacroKey supports macros by physical swipe direction (up/down) and can further configure actions per input state; timings are managed as cards, resolved by the most specific state before falling back to "has code (all)" and "All", and an explicitly empty action blocks fallback. The key editor still migrates legacy configuration automatically.
- A new "Symbol swipe mapping" can configure output for half-width symbols, their Chinese full-width forms, and Caps uppercase forms separately; when Caps is on, key faces show the uppercase swipe symbols as well.
- Symbol-key labels are normalized to ASCII equivalents before reaching the engine, and committing a non-ASCII custom symbol label tops up the candidate first.
- New "Auto-close paired symbols" and "Insert space between Chinese and English" toggles were added; paired symbols support caret placement and paired backspace, hardware backspace also triggers paired deletion, and the spacing option covers preedits committed with Return.
- Punctuation swipe behavior can be configured independently; key widths support per-key overrides with legacy weight conversion, making non-uniform custom layouts easier to build.
- The layout editor marks real alt/alt1 changes as customized, making default and custom key positions easier to distinguish.
- Toolbar and keyboard buttons can be configured to toggle the number keyboard.
- Custom keyboard keys can show configurable uppercase hints, and modified keys are marked with a distinct editor border.
- Popup preset editing can restore default candidate content and continues to support QR import/export.

### 5. Toolbar and UI customization

- Toolbar buttons support both icon-font and drawable-based icon sources, making style unification easier.
- Toolbar button icons can be customized with iconfont code points for a consistent icon-font style.
- Toolbar previews support custom button icons and inline font sizes, with icons tinted according to the active theme.
- Toolbar height can be configured independently, and the candidate bar wraps dynamically within the available space so narrow screens and larger fonts remain usable.
- The main settings page supports search with cross-page navigation, automatic positioning, and highlighting of matched settings; opening the page no longer grabs focus and pops up the keyboard, since the search box activates only after tapping its placeholder.
- Key behavior settings were regrouped so "Keys and gestures" keeps only gesture-related items; the Rime scheme picker is now a radio-button single-choice list with the active scheme checked, and related punctuation-swipe and language-key labels were unified.
- The popup and symbol-swipe mapping editors use a circular bottom-right FAB to add entries, and the last row remains scrollable above the button.
- Key, row, and macro editor dialogs preserve unsaved edits across screen rotation.
- Layout, key, font, and popup editors use state-aware save icons.
- The input bar includes a more semantic hide-keyboard icon for better visual clarity.
- With a Rime engine active, the status area offers default and Rime tab pages, switchable by tapping or swiping, with the active tab highlighted.
- UI details such as the input method picker, keyboard adjustment overlay, and floating-keyboard state persistence continue to be refined, and layout refresh is more stable when themes change.

### 6. In-app updates and version retrieval

- The About page includes a `Check for updates` entry that compares against the latest stable GitHub Release.
- When a new version is found, the app downloads an APK matching the current package name and device ABI, then lets the user start installation manually.
- Locally built and CI artifacts use a date plus short commit hash in their version segment to make issue tracing easier.
- Application and plugin update downloads show determinate progress and support canceling the current download.

### 7. Multi-theme switching

- Multiple themes can be selected for both light mode and dark mode.
- Tapping the light/dark switch button cycles through the selected themes of the current mode.
- Each mode can keep multiple themes for fast visual switching.
- Theme editing provides explicit save, share, and import actions, while previews stay aligned with current keyboard behavior.
- Theme import tolerates unknown fields in foreign fork theme JSON, so third-party themes import smoothly.
- Theme previews support a horizontal theme-pool carousel; the theme list can toggle pool membership directly and smart-collapse groups, while duplicate cards and stale files left after deletion are cleaned up.

### 8. Preserving the Fcitx5 plugin architecture

- The main app still loads additional input engines or feature plugins through the plugin mechanism.
- Beyond the main app, this repository also includes plugins/components such as RIME, Anthy, UniKey, Thai, Hangul, Chewing, Sayura, Jyutping, and clipboard filtering/synchronization.
- Compatibility with external plugin installation and integration scenarios is preserved.

## Supported input methods

- English
- Chinese
  - Pinyin / Shuangpin
  - Zhuyin / Bopomofo
  - Jyutping
  - Chewing
- Vietnamese
- Japanese
- Korean
- Sinhala
- Thai
- Custom RIME schemas

## Download

- GitHub Releases:
  [https://github.com/boomker/fcitx5-android/releases](https://github.com/boomker/fcitx5-android/releases)

If you only want to try the actively maintained version, the recommended choice is the official package from the Releases page.

If the main app is already installed, you can also download and install the latest stable package in-app via About -> Check for updates.

## Build

### Requirements

- Android SDK Platform / Build-Tools 36
- Android NDK 28
- CMake 3.31.6
- `extra-cmake-modules`
- `gettext`

The app currently supports Android 7.0 (API 24) and newer.

### Initialize repository

```sh
git clone git@github.com:boomker/fcitx5-android.git
cd fcitx5-android
git submodule update --init --recursive
```

### Build locally

```sh
./gradlew assembleDebug
```

To build only the clipboard sync plugin:

```sh
./gradlew :plugin:clipboard-sync:assembleRelease
```

To rebuild the built-in Chinese language model, see [`scripts/libime-lm/README.md`](./scripts/libime-lm/README.md).

Build tasks are compatible with the Gradle 10 task DSL, and native install tasks sharing a CMake build tree are serialized for stability with the configuration cache enabled.

## Who this fork is for

- Users who want to use a Fcitx5-based Android IME.
- Users who rely on RIME or multiple input-engine switching.
- Users who want bidirectional clipboard sync between phone and desktop.
- Users who want images, files, links, and text to work inside one unified clipboard workflow.

## Credits

- Upstream project:
  [fcitx5-android/fcitx5-android](https://github.com/fcitx5-android/fcitx5-android)
- Fork and feature enhancement reference:
  [fxliang/fcitx5-android](https://github.com/fxliang/fcitx5-android)
- Contributors of Fcitx5, libime, fcitx5-chinese-addons, RIME, and related upstream projects
