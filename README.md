# SceneFind (SFM) — Movie Scene Identifier

**Package:** `com.vitalsoft.sfm`  
**Min SDK:** API 19 (Android 4.4 KitKat)  
**Target SDK:** API 34  
**Language:** Plain Java (no lambdas, API 19 compatible)

---

## What It Does

SceneFind is a "Shazam for movies" — tap the listen button while a movie/show is playing, and the app uses Android's Speech Recognition to capture the dialogue. It then searches [yarn.co](https://getyarn.io) for matching clips and identifies the movie or show, showing the top match prominently and similar matches in a list below.

---

## Architecture

```
com.vitalsoft.sfm/
├── MainActivity.java          # Fullscreen activity hosting the WebView
├── AudioListenerManager.java  # SpeechRecognizer wrapper + amplitude callbacks
└── StorageManager.java        # SharedPreferences-based recents storage

app/src/main/assets/
└── index.html                 # Entire UI: HTML + CSS + JS (loaded via file:///android_asset/)
```

### WebView ↔ Java Bridge

All UI is in `index.html`. Java exposes a bridge object called `SFMBridge` to JavaScript:

| JS Call | Java Method | Description |
|---|---|---|
| `SFMBridge.requestAudioPermission()` | `SFMBridge.requestAudioPermission()` | Request mic permission |
| `SFMBridge.hasAudioPermission()` | `SFMBridge.hasAudioPermission()` | Check if already granted |
| `SFMBridge.startListening(durationMs)` | `SFMBridge.startListening(int)` | Start recording + STT |
| `SFMBridge.stopListening()` | `SFMBridge.stopListening()` | Stop early |
| `SFMBridge.saveResult(jsonString)` | `SFMBridge.saveResult(String)` | Persist a result to recents |
| `SFMBridge.getRecents()` | `SFMBridge.getRecents()` | Get JSON array of recent results |
| `SFMBridge.clearRecents()` | `SFMBridge.clearRecents()` | Clear all recents |
| `SFMBridge.showToast(msg)` | `SFMBridge.showToast(String)` | Show native Android toast |
| `SFMBridge.getAppVersion()` | `SFMBridge.getAppVersion()` | Returns versionName |

### Java → JS Callbacks

Java calls these global JS functions via `evaluateJavascript()`:

| JS Function | Triggered By | Payload |
|---|---|---|
| `window.onPermissionGranted()` | Permission approved | — |
| `window.onPermissionDenied()` | Permission denied | — |
| `window.onListenResult(text)` | STT completed | Transcribed string |
| `window.onListenError(error)` | STT failure | Error message string |
| `window.onAmplitude(amp)` | RMS change during listen | 0–100 integer |

---

## Search Flow

1. User taps microphone → `SFMBridge.startListening(8000)` called
2. `AudioListenerManager` starts Android `SpeechRecognizer`
3. On result → `window.onListenResult(text)` called with transcribed dialogue
4. JS sends XHR to `https://getyarn.io/yarn-api/public/clips/search?text=<encoded>`
5. Response parsed → deduplicated by show title → rendered as results
6. Top result stored via `SFMBridge.saveResult(json)` to recents

---

## Setup & Build

### Prerequisites
- Android Studio Hedgehog or later
- Android SDK with API 19–34 installed
- Java 8 (project compileOptions set to Java 1.7 for compatibility)

### Open the Project
1. Open Android Studio → `File > Open` → select the `SFM/` folder
2. Let Gradle sync
3. Connect a device or start an emulator (API 19+)
4. Click **Run**

### Permissions
The app requests `RECORD_AUDIO` at runtime (Android M+). On older devices it's granted at install.  
`INTERNET` is declared in the manifest for the yarn.co API calls.

### Note on Speech Recognition
Android's `SpeechRecognizer` requires Google services. If unavailable (e.g., emulator without Play), the recognizer returns an error. The app handles this gracefully and shows "No audio detected."

---

## UI Design

The entire UI lives in `assets/index.html` with inline CSS and JS.

- **Font:** Bebas Neue (headers) + DM Sans (body)
- **Palette:** Deep dark (#080a0e) + gold accent (#e8c44a)
- **Screens:** Home → Listening → Loading → Results → Detail
- **Animations:** Pulsing rings, waveform bars, shimmer skeletons, slide-up cards
- **No external dependencies** — fonts loaded from Google Fonts CDN (requires internet)

### Offline Fonts Fallback
For offline environments, replace the `<link>` to Google Fonts in `index.html` with locally bundled `.ttf` files placed in `assets/fonts/`.

---

## Customization

| What | Where |
|---|---|
| Listen duration | `SFMBridge.startListening(8000)` — change ms value |
| Max recents stored | `StorageManager.MAX_RECENTS = 50` |
| Search API endpoint | `searchYarn()` function in `index.html` |
| Colors / fonts | CSS `:root` variables in `index.html` |
| Screen transitions | `.screen { transition: opacity 0.35s }` in CSS |
