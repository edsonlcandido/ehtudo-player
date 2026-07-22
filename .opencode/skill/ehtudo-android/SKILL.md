---
name: ehtudo-android
description: Use when working on the Android app under `apps/android/` — a Kotlin/Compose IPTV player derived from `another-iptv-player`, single-tenant, with libmpv via JNI as the playback engine and Room as the only persistence. Applies to tasks that touch Compose screens (`PlaylistDashboardScreen.kt`, `LiveCategoryDetailScreen.kt`, `PlaylistSettingsScreen.kt`, `PlayerScreen.kt`), navigation (`AppNavigation.kt`), the data layer (`data/local/*`, `data/PlaylistRepository.kt`, `data/PlaylistContentStore.kt`), the player (`player/MPVPlayer.kt`, `player/MPVLib.kt`, `ui/player/PlayerViewModel.kt`), networking (`networking/XtreamApiClient.kt`, `networking/M3UService.kt`, `networking/M3UParser.kt`), the player UI (`ui/player/PlayerScreen.kt`, `ui/player/PlayerGestures.kt`), the PiP/brightness plumbing (`MainActivity.kt`, `player/PlayerActivityHooks.kt`), the local-properties / AGP wiring (`apps/android/build.gradle.kts`, `apps/android/app/build.gradle.kts`), or building/installing the debug APK on a phone. Skip for iOS, web, or desktop work elsewhere in the repo.
---

# EH! IPTV — Android app architecture

The Android build lives in `apps/android/` (application id `app.ehtudo.iptv`,
namespace `app.ehtudo.iptv`). It is a Compose app, single-tenant (one
auto-created Xtream playlist), built with AGP 8 + Kotlin 2 + Room + libmpv via
JNI. The original Swift source it was forked from lives at `ios/another-iptv-player/`;
many files in this app carry comments starting with `iOS counterpart:` that
point at the Swift class they mirror.

Read these together to load the full mental model:

- `apps/android/app/src/main/java/app/ehtudo/iptv/AnotherIptvPlayerApp.kt` — DI container
- `apps/android/app/src/main/java/app/ehtudo/iptv/MainActivity.kt` — single Compose host + PiP/brightness plumbing
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/AppNavigation.kt` — `Routes`, `NavHost`, bootstrap
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/LocalRepositories.kt` — CompositionLocals
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/dashboard/PlaylistDashboardScreen.kt` — bottom-nav + pager
- `apps/android/app/src/main/java/app/ehtudo/iptv/data/PlaylistContentStore.kt` — catalog state machine
- `apps/android/app/src/main/java/app/ehtudo/iptv/player/MPVPlayer.kt` + `MPVLib.kt` — playback engine

## Build & install (this machine)

- JDK: `/usr/lib/jvm/msopenjdk-17-amd64`
- Android SDK: `/home/edson/Android/Sdk` (`ANDROID_HOME`)
- NDK: `27.0.12077973` (declared in `apps/android/local.properties`)
- CMake: `3.22.1`
- AGP supports the bundled NDK without `local.properties`, but libmpv's
  prebuilt `.so`s live in `app/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/`.

```bash
cd apps/android
JAVA_HOME=/usr/lib/jvm/msopenjdk-17-amd64 \
  ANDROID_HOME=/home/edson/Android/Sdk \
  ./gradlew :app:assembleDebug --no-daemon
```

Output: `apps/android/app/build/outputs/apk/debug/app-debug.apk`. The same
parameters live in `/tmp/dev-run.sh` which `adb push` → `pm install -r` →
`am start` (MIUI blocks `adb install`).

## Source tree (under `apps/android/app/src/main/java/app/ehtudo/iptv/`)

```
AnotherIptvPlayerApp.kt        ← Application: lazy singletons (no DI framework)
MainActivity.kt                ← ComponentActivity + Compose host
data/
  AppConfig.kt                 ← constants (server URL, default playlist name)
  DeviceIdProvider.kt          ← ANDROID_ID with UUID fallback
  PlaylistRepository.kt        ← CRUD + firstOrCreateDefault()
  PlaylistContentStore.kt      ← catalog StateFlows + Xtream sync (see below)
  VodRepository.kt / SeriesRepository.kt / FavoriteRepository.kt
  M3uImporter.kt / M3uContentStore.kt / M3uFavoriteStore.kt
  DownloadManager.kt / DownloadStorage.kt / DownloadWorker.kt
  RatingManager.kt / PlayerPreferences.kt / HiddenCategoryStore.kt
  LastPlaylistStore.kt / WatchHistoryDao.kt / PlaybackUrlBuilder.kt
  local/                       ← Room: AppDatabase, Entities, DAOs
networking/
  XtreamApiClient.kt           ← player_api.php → typed lists (OkHttp + json)
  XtreamModels.kt / XtreamError.kt / FlexibleSerializers.kt
  M3UService.kt / M3UParser.kt
player/
  MPVPlayer.kt                 ← high-level controller (StateFlows, mpvQueue)
  MPVLib.kt                    ← JNI facade (one-line wrappers around native*)
  MPVSurfaceView.kt            ← Compose SurfaceView wrapper
  PictureInPicture.kt / PlayerAudioFocus.kt / SubtitleAppearance.kt
  PlayerActivityHooks.kt       ← PiP + brightness state shared with the Activity
ui/
  AppNavigation.kt             ← Routes + NavHost + bootstrap
  LocalRepositories.kt         ← staticCompositionLocalOf<T>() providers
  dashboard/                   ← PlaylistDashboardScreen, M3uDashboardScreen, shelves
    category/                  ← Live/Movie/SeriesCategoryDetailScreen, LiveChannelList
    detail/                    ← MovieDetailScreen, SeriesDetailScreen + components
  search/  favorites/  history/  downloads/  settings/  components/  theme/  util/
  player/                      ← PlayerScreen, PlayerViewModel, PlayerGestures, SubtitleSheet
model/
  Playlist.kt                  ← also PlaylistKind (XTREAM / M3U)
```

## Layered architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Compose screens (ui/**)                                    │
│   • StateFlow.collectAsStateWithLifecycle()                 │
│   • Navigation callbacks (onOpenMovie, onPlayLive, …)      │
├─────────────────────────────────────────────────────────────┤
│  ViewModels (ui/player/PlayerViewModel.kt)                  │
│   • AndroidViewModel holds libmpv across config changes    │
│   • Coroutines in viewModelScope                            │
├─────────────────────────────────────────────────────────────┤
│  Repositories / Stores (data/**)                            │
│   • Hold StateFlows for reactive catalog                    │
│   • Wrap DAOs + network calls                               │
├─────────────────────────────────────────────────────────────┤
│  Room DAOs (data/local/*Dao.kt) + Entities (Entities.kt)    │
│   • Single AppDatabase (version 1, exported)               │
│   • Suspend reads + Returns/Flow for observation            │
├─────────────────────────────────────────────────────────────┤
│  Networking (networking/**) + libmpv (player/)              │
│   • OkHttp + kotlinx.serialization                          │
│   • Media-kit libmpv prebuilt .so → JNI (anotheriptv_mpv)   │
└─────────────────────────────────────────────────────────────┘
```

## Dependency injection: Application + CompositionLocal

No Hilt/Koin. The `Application` subclass owns **all** lazy singletons; the
Activity root publishes them as `staticCompositionLocalOf<…>()` so any
composable reaches them via `LocalX.current`.

- Container: `AnotherIptvPlayerApp.kt:31` exposes `playlistRepository`,
  `playlistContentStore`, `vodRepository`, `seriesRepository`,
  `favoriteRepository`, `hiddenCategoryStore`, `lastPlaylistStore`,
  `m3uContentStore`, `m3uFavoriteStore`, `m3uImporter`, `downloadStorageForWorker`,
  `downloadManager`, `ratingManager`, `playerPreferences`, `deviceIdProvider`.
- Database: lazy `database` (`AppDatabase.get(this)`); two cross-cutting
  escapes — `appDatabaseForDownloads` for `DownloadWorker.kt` and a suspend
  `findLiveStream()` for `PlayerViewModel`. Both intentionally bypass the
  repository wrapper because the only caller doesn't need it.
- `MainActivity.kt:51` calls `CompositionLocalProvider(Local… provides …)`
  for every store the UI needs.
- `ui/LocalRepositories.kt` declares the providers (one per repository).
  `LocalPlayerActivityState` lives separately at `player/PlayerActivityHooks.kt:30`
  because the player layer owns it.
- `staticCompositionLocalOf` (not `compositionLocalOf`) is correct: provider
  values never change in the same process, so we want the no-tracking variant.

When adding a new singleton: add the lazy val in `AnotherIptvPlayerApp`, add
the `staticCompositionLocalOf` in `LocalRepositories.kt` (or co-locate near
the feature if it's not shared), wire it in `MainActivity.onCreate`, then read
it via `LocalX.current`.

## State management conventions

- Repositories expose `val foo: StateFlow<T>` backed by a private
  `MutableStateFlow` (`_foo`); `_foo.asStateFlow()` is the public read.
- Composable reads with
  `val foo by store.foo.collectAsStateWithLifecycle()` (import
  `androidx.lifecycle.compose.collectAsStateWithLifecycle`). Use
  `initialValue = …` for `Flow<Optional>` reads — see
  `PlaylistDashboardScreen.kt:106`.
- For search/filter inputs on the UI thread, debounce via
  `produceState` + `flow.debounce(250.ms)` or the shared
  `ui/components/DebouncedQuery.kt` helper.
- One-shot work uses `LaunchedEffect(key) { … }`; navigation side-effects
  call `navController.navigate(...)` with
  `popUpTo(route) { inclusive = true }; launchSingleTop = true` to avoid
  stacking duplicates.
- Persist UI state across navigation with
  `rememberSaveable { mutableIntStateOf(…) }` — see the dashboard pager fix
  at `PlaylistDashboardScreen.kt:137` (active-tab persistence) and the
  bottom-bar reconciliation at `PlaylistDashboardScreen.kt:224`.

## Navigation

- Single `NavController` in `AppNavigation.kt:123`, `startDestination = Routes.BOOTSTRAP`.
- Bootstrap is a one-shot suspend (`playlistRepository.firstOrCreateDefault()`
  with an 8s timeout, see `AppNavigation.kt:536`) followed by
  `navigate(Routes.dashboard(id)) { popUpTo(BOOTSTRAP) { inclusive = true } }`.
- Routes live in the private `Routes` object at `AppNavigation.kt:60`. Add a
  new screen: define `const val` + a builder `fun foo(…)`, then a
  `composable(route = "${Routes.FOO}/{$ARG}", arguments = listOf(navArgument…))`
  entry that wires callback lambdas back to `navController.navigate(Routes.x(…))`.
- Cross-screen navigation callbacks (e.g. `onOpenVodCategory`,
  `onResumeEpisode`) are passed **down** from `AppNavigation` into the
  dashboard — never read `LocalNavController` from a child screen.
- `onBack` defaults to `navController.popBackStack()`. For the player
  replacing itself with the next episode, see `AppNavigation.kt:325` for the
  `popUpTo(…, inclusive = true) + launchSingleTop` pattern.

## Dashboard (`ui/dashboard/PlaylistDashboardScreen.kt`)

The bottom bar has 5 tabs in this order (must stay in sync):

| index | screen                         | route from `Routes`                       |
| ----- | ------------------------------ | ----------------------------------------- |
| 0     | `SearchBody` (live+movie+series) | inline in same file                  |
| 1     | `LiveTabBody` (shelf of shelves) | inline                               |
| 2     | `MoviesTabBody` (shelf+recent) | inline                                    |
| 3     | `SeriesTabBody`                | inline                                    |
| 4     | `PlaylistSettingsBody`         | `ui/settings/PlaylistSettingsScreen.kt`   |

`TAB_TITLE_IDS` (`PlaylistDashboardScreen.kt:434`), `DashboardBottomBar` and
the `when (page)` inside `DashboardPager` (`PlaylistDashboardScreen.kt:377`)
**must agree**. Adding a tab = edit all three + bump `TAB_COUNT`.

Critical facts:
- `HorizontalPager(userScrollEnabled = false)` — tabs only change via the
  bottom bar; swipe is deliberately disabled.
- Default page is 4 (Settings) so the user fills in Xtream credentials on
  first launch. On the first composition where the playlist has credentials,
  `LaunchedEffect(playlist)` (`PlaylistDashboardScreen.kt:150`) sets
  `initialNavigationDone = true` and calls `pagerState.scrollToPage(1)`.
- `savedInitialPage` is mirrored back to a `rememberSaveable` int so the
  tab persists across `popBackStack()` from category-detail screens
  (`PlaylistDashboardScreen.kt:137`).
- Per-tab content bodies (`LiveTabBody`, `MoviesTabBody`, `SeriesTabBody`)
  observe `HiddenCategoryStore` to filter the user's hidden categories,
  pull category → stream maps from `PlaylistContentStore` via
  `liveByCategory` / `vodByCategory` / `seriesItemsByCategoryId`, and render
  shelves with `CategoryShelf.kt` + `LiveStreamCard`/`PosterCard` inside a
  `LazyRow`. `ContinueWatchingShelf.kt` and `RecentlyAddedShelf.kt` are
  standalone composables reused at the top of each tab.
- The M3U branch (`apps/.../dashboard/M3uDashboardScreen.kt`) renders a
  flat channel list and bypasses the catalog store entirely.
- Category-detail screens (`ui/dashboard/category/*CategoryDetailScreen.kt`)
  push a back-stack entry and the user pops back to the dashboard; **don't
  rely on the pager state being preserved without the
  `rememberSaveable` workaround above**.

## Player module

Three files you must understand together:

1. `player/MPVLib.kt` — `object` with static-`init { System.loadLibrary(...) }`
   (load order: `mpv` → `mediakitandroidhelper` → `anotheriptv_mpv_jni`)
   and external `native*` declarations backed by `cpp/mpv_jni.cpp`. Public
   surface mirrors `mpv/client.h`. **Handles are opaque `Long`s; never
   dereference them in Kotlin.**
2. `player/MPVPlayer.kt` — high-level controller. Every public method
   either touches only `StateFlow` or bounces onto `mpvQueue`
   (`Executors.newSingleThreadScheduledExecutor { … isDaemon = true }`),
   serializing all mpv API calls. JNI events arrive on a daemon thread and
   are handed to `MPVLib.EventListener` (`MPVLib.kt:103`).
3. `ui/player/PlayerViewModel.kt` — `AndroidViewModel` that owns `MPVPlayer`
   across config changes (rotation, dark-mode flip). Built via a factory
   that takes `(application, playlistId, streamRef, kind)`; `kind` is one
   of `MOVIE / SERIES_EPISODE / LIVE / M3U_CHANNEL`. Resolves
   `playlistId + streamRef` to a URL through `data/PlaybackUrlBuilder.kt`,
   then calls `mpv.load(url, play, startSeconds, liveLowLatency)`. Resumes
   from `WatchHistoryDao.findById` skipping the last 30s. Tickers write a
   fresh history row every 5s. `onCleared` flushes a final history row and
   calls `player.dispose()`.

PiP and brightness plumbing — `MainActivity.kt` is the host; `player/PlayerActivityHooks.kt` is the shared state object; the player screen reads it through `LocalPlayerActivityState`. See `MainActivity.kt:77` for the `onUserLeaveHint` hook that auto-enters PiP. Without `android:supportsPictureInPicture="true"` + `android:configChanges=…` on the Activity (`AndroidManifest.xml:34`), the OS recreates the Activity on PiP and the mpv handle dies mid-transition.

VO selection is `vo=mediacodec_embed` + `hwdec=mediacodec` (not the iOS
`videotoolbox`). Rationale: the comment block at `MPVPlayer.kt:215` explains
why a fallback chain is unsafe (`gpu` and `mediacodec_embed` need *different*
hwdec modes, and one global setting can't satisfy both).

## Data layer (Room)

- Database: `data/local/AppDatabase.kt:38` — version 1, `exportSchema = true`,
  outputs to `app/schemas/`. Entities: `Playlist`, `CategoryEntity`,
  `LiveStreamEntity`, `VodStreamEntity`, `SeriesEntity`, `SeasonEntity`,
  `EpisodeEntity`, `FavoriteEntity`, `WatchHistoryEntity`, `M3uChannelEntity`,
  `M3uFavoriteEntity`, `DownloadedItemEntity`. **Any schema change needs a
  `Migration`** (`schemas/` is the reference).
- DAOs follow `*Dao` naming, return `Flow<List<X>>` for observation and
  `suspend fun` for one-shots. DB writes use `withTransaction { … }` for
  multi-table consistency — see `PlaylistContentStore.syncFromNetworkReplacingLocal`
  (`PlaylistContentStore.kt:389`), which is the canonical example.
- `PlaylistContentStore` (`data/PlaylistContentStore.kt:48`) is the
  load-balanced catalog state machine. Two phases:
  1. **Network sync** — wipe then reinsert in one transaction.
     Animated `progress(String) -> Unit` callback drives
     `_loadingMessage` for the spinner.
  2. **Read phase 1** — categories flip out of the loading state.
  3. **Read phase 2** — `parallelStreams` runs three Room reads concurrently
     with `async { … }.awaitAll()` then publishes them as
     `liveStreamsByCategoryId` etc. (grouped by `categoryId`).
- Single-writer guarantee: every public mutator runs on `scope` (Main +
  `SupervisorJob`) and stamps a fresh `loadToken: UUID`; HTTP completions
  resume on IO and check `loadToken == token` before publishing. Same idea
  as iOS `loadToken: UUID?`.
- Bypass repositories: `appDatabaseForDownloads` from `AnotherIptvPlayerApp`
  is read by `DownloadWorker.kt` (foreground-service worker). Use that for
  any new `CoroutineWorker` so the worker doesn't need the CompositionLocal
  application object.

## Networking

- HTTP client: `XtreamApiClient` (`networking/XtreamApiClient.kt:46`) shares a
  single `defaultClient` `OkHttpClient` (10s connect, 15s read). All response
  decoding goes through the lenient `XtreamJson` (`XtreamApiClient.kt:31`):
  `ignoreUnknownKeys`, `coerceInputValues`, `isLenient`. **Don't enable
  `failOnUnknownKeys`** — providers ship extra fields.
- M3U side: `M3UService.kt` downloads the text and `M3UParser.kt` walks
  `#EXTINF` lines into the entity map. `M3uImporter.kt` replaces the
  whole table, analogous to the Xtream sync transaction.
- `PlaybackUrlBuilder.kt` builds per-kind playback URLs from a `Playlist`
  (live/movie/series). Centralised so we don't sprinkle URL assembly logic
  across screens.

## UI conventions

- Material 3 only. `Scaffold` + `TopAppBar` + `NavigationBar` are the
  standard containers.
- Strings live in `res/values/strings.xml` and `res/values-pt-rBR/strings.xml`
  (Brazilian Portuguese). The Turkish comments scattered through
  `PlaylistDashboardScreen.kt` are author notes — keep them in sync when you
  change behaviour.
- Coil `AsyncImage` for posters/logos (`coil.compose`). Coil's cached model
  is the Android counterpart of iOS `CachedImage`.
- Image kinds: `LiveStreamCard` (square logo + name) for Live channels;
  `PosterCard(kind = ImageKind.Movie/Series)` for everything else. Live
  category-detail uses `LiveChannelList.kt` (two-line row: square logo +
  name + category as description).
- Form components: `ui/components/FormComponents.kt`,
  `ui/components/ModalSlide.kt`, `ui/components/DebouncedQuery.kt`.
  Re-use these before adding new variants.

## Conventions to preserve

- **No comments on new code unless asked.** Existing commentary documents
  iOS counterparts and subtle VO/AO decisions — they predate us; leave them.
- Repository/data classes should never throw into the UI. Wrap the call in
  `try/catch`, surface a `_loadError.value = …` and let the screen render an
  `ErrorState`. Player failures map through `MPVPlayer.handleLogMessage`
  → `_playbackFailureMessage` → string resource.
- New `StateFlow` reads always go through `collectAsStateWithLifecycle`,
  **not** `collectAsState`. This is required to pause collection when the
  Activity is in the background.
- Anything that needs an `Application` context (e.g. `getString`) belongs in
  a repository / VM, not a `@Composable`. Compose should stay pure.
- PI/CS files: tabs, `when` blocks and matching braces use 4-space indent.
  Names use trailing lower-case for type discriminators (e.g. `tabTypeFor`
  returns `"live"`, `"vod"`, `"series"` matching the `type` column on
  `CategoryEntity`).

## Common tasks → file map

| task                                                      | files                                                                                             |
| --------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Add a bottom-bar tab                                      | `PlaylistDashboardScreen.kt` (`TAB_COUNT`, `TAB_TITLE_IDS`, `DashboardBottomBar`, `DashboardPager`), `AppNavigation.kt` if it opens a full-screen route |
| Add a category-detail screen                              | new file under `ui/dashboard/category/`, register `composable(...)` in `AppNavigation.kt`        |
| Add a settings row                                         | `ui/settings/PlaylistSettingsScreen.kt`; new entry in `PlaylistSettingsBody` form                |
| Add a Room entity                                         | `data/local/Entities.kt`, matching `*Dao.kt`, then `AppDatabase.kt` entities array + version + `schemas/<n>.json` |
| Add a repository singleton                                | `data/<Foo>Repository.kt`, `AnotherIptvPlayerApp.kt`, `ui/LocalRepositories.kt`, `MainActivity.kt` |
| Tune libmpv                                               | `MPVPlayer.kt` (`applyDefaultProperties`, `applyStreamBufferPolicy`, init block) — keep changes mirror-compatible with iOS unless platform-specific |
| New workmanager worker                                    | extend `data/DownloadWorker.kt`, declare foreground service in `AndroidManifest.xml` if needed   |
| New external route (deeplinks)                            | `Routes.x(...)` builder + `<intent-filter>` in `AndroidManifest.xml`                              |
| PiP / brightness regressions                              | `MainActivity.kt`, `player/PlayerActivityHooks.kt`, `ui/player/PlayerScreen.kt`, `AndroidManifest.xml` `configChanges` |

## Gotchas

- `rememberPagerState(initialPage = 4)` does **not** survive back-stack
  pops; the screen re-creates and starts on the initial page again.
  Always keep the `rememberSaveable { mutableIntStateOf(...) }` mirror
  (`PlaylistDashboardScreen.kt:137`).
- `PlaylistContentStore.loadPlaylistSuspending` early-returns when
  username/password are blank (`PlaylistContentStore.kt:130`). The
  dashboard likewise branches to render the pager directly so the user
  can fill in credentials from the Settings tab without a loading overlay.
- `AppNavigation` bootstrap was previously driven by a `Flow` re-emission
  that broke navigation; commit `AppNavigation.kt:138-167` documents why the
  one-shot suspend path is mandatory.
- **Do not** call `mpv_*` from the UI thread. Bounce everything through
  `onMpvQueue { … }` in `MPVPlayer.kt:792`.
- MediaCodec-only VO means codecs the device doesn't support (rare for IPTV
  but possible for niche H.266/AV1 streams) will fail outright. If you
  switch back to a `vo=gpu` fallback chain, reread the comment at
  `MPVPlayer.kt:230` about the `hwdec` chicken/egg.
- `PlaylistViewModelFactory` naming: there isn't a general
  `PlaylistViewModel` — most "viewmodel-shaped" orchestration lives in
  `PlaylistContentStore` and `PlayerViewModel`. Don't introduce a new VM
  without confirming there's no existing store doing the job.
- `compileSdk = 36`, `targetSdk = 36` — keep them in sync with `build.gradle.kts`.
- AVD image limit: only `arm64-v8a`, `armeabi-v7a`, `x86_64`. Adding an ABI
  without staging a `libmpv.so` for it crashes the app at load time
  (`app/build.gradle.kts:25` comment).

## Testing on the device

`/tmp/dev-run.sh` does build + push + install + start in one go. For manual
iterations with shorter cycle, the same flow is:

```bash
cd apps/android && \
  JAVA_HOME=/usr/lib/jvm/msopenjdk-17-amd64 ANDROID_HOME=/home/edson/Android/Sdk \
    ./gradlew :app:assembleDebug --no-daemon

adb push app/build/outputs/apk/debug/app-debug.apk /data/local/tmp/app-debug.apk
adb shell pm install -r /data/local/tmp/app-debug.apk
adb shell rm /data/local/tmp/app-debug.apk
adb shell am force-stop app.ehtudo.iptv
adb shell am start -n app.ehtudo.iptv/.MainActivity
adb logcat -s mpv:* MPVPlayer:* AnotherIptvPlayerApp:* AndroidRuntime:E
```

The cellular device (POCO F3 / `alioth_global`, Android 13) currently pairs
over WiFi (mdns). Ports are ephemeral — re-run `adb mdns services` if a
session goes stale; the most recent was `192.168.2.133:37241`.
