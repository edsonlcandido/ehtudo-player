# Android TV — Setup Eh!Iptv

> Roteiro para adicionar suporte a **Android TV** ao app nativo em
> `apps/android/`, reaproveitando a base Compose + libmpv já construída.
>
> Estratégia: **variante única** com `UI_MODE_TYPE_TELEVISION` —
> Activity dedicada `MainTvActivity` que compartilha **toda** a camada
> de dados/repo/player do app mobile, mas usa uma árvore de
> Composables focada em D-pad (sem `NavigationBar` inferior, sem
> `HorizontalPager` scrollável, foco visível, hero fullscreen).

---

## 0. Decisões de arquitetura

| Pergunta | Resposta | Por que |
| --- | --- | --- |
| Leanback (View system) **ou** Compose? | **Compose** com `androidx.tv:tv-foundation` | O app já é 100% Compose; misturar View + Compose duplica ViewModels e quebra o `CompositionLocalProvider` da `MainActivity`. |
| Uma ou duas Activities? | **Duas Activities** (`MainActivity` + `MainTvActivity`) | A `MainActivity` precisa de `configChanges`/`supportsPictureInPicture`, e a TV activity precisa de `category.LEANBACK_LAUNCHER` + `banner`. Compartilhar uma só Activity com `softwareLeanback=required` esconde o app do launcher mobile. |
| Como escolher qual abre? | `<category.LEANBACK_LAUNCHER>` + `<category.LAUNCHER>` no mesmo manifest | A *Play Store* / *Leanback Launcher* escolhe automaticamente. `UiModeManager.getCurrentModeType() & UI_MODE_TYPE_TELEVISION` decide qual Activity é a `start` obrigatória. |
| Build flavors vs sourceSet? | **`sourceSet tv/` + flavors `mobile`/`tv`** | Flavors mantêm o mesmo `applicationId` (`app.ehtudo.iptv`) no mobile, e geram um `app-ehtudo-iptv-tv` com banner próprio. `sourceSet` puro (`src/tv/`) seria mais limpo, mas o app já tem `src/main` com AndroidManifest; misturar `src/main` + `src/tv/AndroidManifest.xml` exige `manifestPlaceholders` cuidadosos. |
| `libmpv` realmente roda em TV? | **Sim** (`arm64-v8a`, `armeabi-v7a`) | 95 % das TVs Android atuais são `arm64`; `x86_64` cobre Android TV em emulador/desktop. Não precisa de ABI nova. |

---

## 1. Pré-requisitos no projeto

| Item | Estado atual | Próximo passo |
| --- | --- | --- |
| `applicationId` | `app.ehtudo.iptv` | Manter (mesmo app); TV é uma *launcher variant*. |
| `compileSdk` | `36` | OK — `tv-foundation` exige ≥ 21. |
| `minSdk` | `26` | OK — TV minSdk recomendado é 21; cobrimos. |
| `targetSdk` | `36` | OK. |
| `ndk.abiFilters` | `arm64-v8a`, `armeabi-v7a`, `x86_64` | OK — manter. |
| `mpv_jni.cpp` | já usa `extern "C"` JNI | OK — reuso direto. |
| Tema | `Theme.AnotherIPTVPlayer` (Material3) | Criar **novo** tema `Theme.AnotherIPTVPlayer.Tv` (sem `windowBackground` claro, sem `enforceStatusBarContrast`). |

---

## 2. Roteiro de implementação (passo a passo)

### Etapa 1 — `git checkout -b tv/ehiptv`

Trabalhe em branch dedicada:

```bash
git checkout -b tv/ehiptv
```

---

### Etapa 2 — Adicionar flavor `tv` no `app/build.gradle.kts`

> **Por que flavor e não sourceSet puro?**
> Precisamos de **banner** próprio, **ícone** próprio e
> `category.LEANBACK_LAUNCHER` no manifest. SourceSet exige
> `manifestPlaceholders`. Flavors é o caminho oficial do AGP.

```kotlin
// apps/android/app/build.gradle.kts

android {
    // ...existing code...
    flavorDimensions += "device"

    productFlavors {
        create("mobile") {
            dimension = "device"
            // mesmo applicationId; sobrescreve só o banner
            manifestPlaceholders["appBanner"] = "@mipmap/ic_launcher"
            manifestPlaceholders["launcherCategory"] = "android.intent.category.LAUNCHER"
            manifestPlaceholders["leanbackRequired"] = "false"
        }
        create("tv") {
            dimension = "device"
            // app separado na Play Store, com banner de TV
            applicationIdSuffix = ".tv"
            versionNameSuffix = "-tv"
            manifestPlaceholders["appBanner"] = "@mipmap/ic_launcher_tv"
            manifestPlaceholders["launcherCategory"] = "android.intent.category.LEANBACK_LAUNCHER"
            manifestPlaceholders["leanbackRequired"] = "true"
        }
    }

    buildTypes {
        // ...existing code...
    }
}
```

**Como reproduzir**
1. Em `app/build.gradle.kts`, adicionar `flavorDimensions += "device"`.
2. Definir `mobile` e `tv` com `manifestPlaceholders` para `appBanner`,
   `launcherCategory` e `leanbackRequired`.
3. Variantes geradas:
   - `mobileDebug` → `app/build/outputs/apk/mobile/debug/app-mobile-debug.apk`
   - `tvDebug`     → `app/build/outputs/apk/tv/debug/app-tv-debug.apk`

---

### Etapa 3 — Dependências TV no `gradle/libs.versions.toml`

```toml
[versions]
leanback = "1.0.0"
tvFoundation = "1.0.0-alpha10"

[libraries]
# Android TV Compose foundation (focus carousel, ImmersiveList, etc.)
androidx-tv-foundation = { group = "androidx.tv", name = "tv-foundation", version.ref = "tvFoundation" }
androidx-tv-material =  { group = "androidx.tv", name = "tv-material",     version.ref = "tvFoundation" }

# Leanback (legado, mas ainda usado para AppBanner / SearchFragment se quiser)
androidx-leanback =     { group = "androidx.leanback", name = "leanback", version.ref = "leanback" }
```

```kotlin
// apps/android/app/build.gradle.kts — dentro de dependencies { ... }
dependencies {
    // ...existing code...

    // TV Compose — Material 3 + componentes focados em D-pad
    "tvImplementation"(libs.androidx.tv.foundation)
    "tvImplementation"(libs.androidx.tv.material)

    // Leanback só para banner (opcional; pode-se usar ImageButton na home)
    "tvImplementation"(libs.androidx.leanback)
}
```

> **Nota**: o qualificador `"tvImplementation"` do AGP aplica a
> dependência **apenas** ao flavor `tv`. Resultado: o APK mobile não
> ganha peso extra.

---

### Etapa 4 — `AndroidManifest.xml` com duas Activities

```xml
<!-- apps/android/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions existentes (mantidas) -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />

    <!-- TV: declaração de hardware/feature obrigatório para o launcher -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="${leanbackRequired}" />
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <application
        android:name=".AnotherIptvPlayerApp"
        android:allowBackup="true"
        android:banner="${appBanner}"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.AnotherIPTVPlayer">

        <!-- ============== MOBILE (non-TV) ============== -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:supportsPictureInPicture="true"
            android:resizeableActivity="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|navigation|uiMode"
            android:theme="@style/Theme.AnotherIPTVPlayer">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- ============== TV (Leanback) ============== -->
        <activity
            android:name=".MainTvActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTask"
            android:resizeableActivity="false"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|navigation|uiMode"
            android:theme="@style/Theme.AnotherIPTVPlayer.Tv">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="${launcherCategory}" />
            </intent-filter>
        </activity>

        <!-- WorkManager foreground service (mantido) -->
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
    </application>
</manifest>
```

**Por que placeholders (`${leanbackRequired}`)**
- `mobile`: `leanbackRequired = false` → app mobile aparece em qualquer
  device (TV inclusive, mas sem interface otimizada).
- `tv`: `leanbackRequired = true` → Play Store **esconde** o APK TV de
  devices sem suporte a Leanback.

---

### Etapa 5 — Banner da TV

`**res/drawable-tv/tv_banner.xml**` (320×180 px obrigatório):

```xml
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/ehtudo_tv_banner_bg" />
    <item android:gravity="center">
        <bitmap android:src="@drawable/ic_ehiptv_logo"
                android:gravity="center" />
    </item>
</layer-list>
```

Adicione em `res/values/colors.xml`:
```xml
<color name="ehtudo_tv_banner_bg">#0A0A0A</color>
```

Renderer (rodar local antes do build):
```bash
# gerar PNG 320x180 do banner.psd → res/drawable-tv/tv_banner.png
# ou usar tv_banner.xml como acima (vector + cor)
```

---

### Etapa 6 — Tema `Theme.AnotherIPTVPlayer.Tv`

```xml
<!-- apps/android/app/src/main/res/values/themes.xml -->
<resources>
    <!-- tema mobile existente -->
    <style name="Theme.AnotherIPTVPlayer" parent="Theme.Material3.DayNight.NoActionBar">
        ...
    </style>

    <!-- novo: tema TV -->
    <style name="Theme.AnotherIPTVPlayer.Tv" parent="Theme.Leanback">
        <item name="android:windowBackground">@color/ehtudo_tv_bg</item>
        <item name="android:windowShowWallpaper">false</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:windowFullscreen">true</item>
    </style>
</resources>
```

`values-tv/themes.xml` (override para TVs com `uiMode=tv`):
```xml
<resources>
    <style name="Theme.AnotherIPTVPlayer.Tv" parent="Theme.Leanback">
        <item name="android:windowBackground">@color/ehtudo_tv_bg</item>
    </style>
</resources>
```

> **Atenção**: `Theme.Leanback` força `BROWSE`, mas como usamos
> Compose inteiro, o tema afeta só o `windowBackground` até o
> `setContent { … }` rodar. Mantenha só o essencial:
> `windowBackground`, `windowFullscreen`.

---

### Etapa 7 — `MainTvActivity` (co-host da TV)

```kotlin
// apps/android/app/src/main/java/app/ehtudo/iptv/MainTvActivity.kt
package app.ehtudo.iptv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.ehtudo.iptv.player.LocalPlayerActivityState
import app.ehtudo.iptv.player.PlayerActivityState
import app.ehtudo.iptv.tv.TvDashboardScreen
import app.ehtudo.iptv.ui.LocalDeviceIdProvider
import app.ehtudo.iptv.ui.LocalFavoriteRepository
import app.ehtudo.iptv.ui.LocalHiddenCategoryStore
import app.ehtudo.iptv.ui.LocalLastPlaylistStore
import app.ehtudo.iptv.ui.LocalPlayerPreferences
import app.ehtudo.iptv.ui.LocalPlaylistContentStore
import app.ehtudo.iptv.ui.LocalPlaylistRepository
import app.ehtudo.iptv.ui.LocalSeriesRepository
import app.ehtudo.iptv.ui.LocalVodRepository

class MainTvActivity : ComponentActivity() {

    private val playerState = PlayerActivityState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AnotherIptvPlayerApp
        setContent {
            // Mesmas CompositionLocals do MainActivity — reuso 100% do
            // data layer / Compose state.
            CompositionLocalProvider(
                LocalPlaylistRepository provides app.playlistRepository,
                LocalPlaylistContentStore provides app.playlistContentStore,
                LocalVodRepository provides app.vodRepository,
                LocalSeriesRepository provides app.seriesRepository,
                LocalFavoriteRepository provides app.favoriteRepository,
                LocalHiddenCategoryStore provides app.hiddenCategoryStore,
                LocalLastPlaylistStore provides app.lastPlaylistStore,
                LocalPlayerActivityState provides playerState,
                LocalPlayerPreferences provides app.playerPreferences,
                LocalDeviceIdProvider provides app.deviceIdProvider,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TvDashboardScreen()
                }
            }
        }
    }
}
```

**O que muda em relação a `MainActivity`**
- Sem `enableEdgeToEdge()` (TVs já são fullscreen).
- Sem `LaunchedEffect` para `screenBrightness` (não há brightness
  hardware controlável em TV).
- Sem `onUserLeaveHint` para PiP (TV não entra em PiP).
- Sem `supportsPictureInPicture`, sem `configChanges` de orientação.
- Carrega `TvDashboardScreen()` em vez de `AppNavigation()`.

---

### Etapa 8 — Pacote `ui/tv/` (Composables TV-specific)

Estrutura nova:

```
apps/android/app/src/main/java/app/ehtudo/iptv/ui/tv/
├── TvDashboardScreen.kt          # top-level: HeroFeatured + CarouselRows
├── TvHeroCard.kt                 # Card com foco e zoom-on-focus
├── TvCategoryCarousel.kt         # ImmersiveList da tv-foundation
├── TvDetailScreen.kt             # detalhe (filme/série) fullscreen
├── TvPlayerScreen.kt             # wrapper do MPVPlayer (reusa PlayerViewModel)
├── TvSettingsScreen.kt           # colunas de credenciais (IME opcional)
├── TvNavGraph.kt                 # subgrafo de rotas TV
├── TvFocusHelpers.kt             # rememberTvFocus() + onFocusChanged helpers
└── Theme.kt                      # override simples do MaterialTheme (TvColors)
```

#### `TvDashboardScreen.kt` (esqueleto)

```kotlin
package app.ehtudo.iptv.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.*
import app.ehtudo.iptv.ui.LocalPlaylistContentStore

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDashboardScreen() {
    val store = LocalPlaylistContentStore.current
    val live       by store.liveByCategory.collectAsStateWithLifecycle()
    val vod        by store.vodByCategory.collectAsStateWithLifecycle()
    val series     by store.seriesItemsByCategoryId.collectAsStateWithLifecycle()

    TvLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TvHeroBanner() }                   // banner fullscreen com "Continue Watching"
        item { TvCategoryCarousel("Ao vivo", live) }
        item { TvCategoryCarousel("Filmes", vod) }
        item { TvCategoryCarousel("Séries", series) }
    }
}
```

#### `TvCategoryCarousel.kt` (carrossel horizontal)

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvCategoryCarousel(title: String, items: List<MediaItem>) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Carousel(
            itemCount = items.size,
            itemSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) { index ->
            val item = items[index]
            TvHeroCard(
                item = item,
                onClick = { /* navegar para TvDetailScreen */ },
            )
        }
    }
}
```

#### `TvHeroCard.kt` (Card com foco)

```kotlin
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvHeroCard(item: MediaItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .size(width = 220.dp, height = 140.dp)
            .focusable()                    // habilita D-pad
            .onFocusChanged { state ->
                // escala leve quando recebe foco — efeito "tv zoom"
                scaleX = if (state.isFocused) 1.05f else 1f
                scaleY = if (state.isFocused) 1.05f else 1f
            },
    ) {
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.name,
        )
    }
}
```

**Por que `androidx.tv.material3`**
- `Carousel`: já implementa snap, focus e paginação por D-pad.
- `Card` com `onClick` + `focusable` cuida do ripple e do highlight.
- `TvLazyColumn` difere do `LazyColumn` por **não** trancar foco no
  scroll e tratar overflow com setas ↑/↓.

---

### Etapa 9 — `TvPlayerScreen.kt` (reusa o player mobile)

```kotlin
package app.ehtudo.iptv.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ehtudo.iptv.player.MPVSurfaceView
import app.ehtudo.iptv.ui.player.PlayerViewModel

@Composable
fun TvPlayerScreen(
    playlistId: String,
    streamRef: String,
    kind: PlayerKind,
) {
    val vm: PlayerViewModel = viewModel(
        factory = PlayerViewModel.factory(
            application = LocalContext.current.applicationContext as AnotherIptvPlayerApp,
            playlistId = playlistId,
            streamRef = streamRef,
            kind = kind,
        )
    )
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> MPVSurfaceView(ctx).also { vm.attachSurface(it) } },
    )
}
```

> **Reuso**: o `PlayerViewModel` e `MPVPlayer` (`apps/android/app/src/main/java/app/ehtudo/iptv/player/`)
> **não** são tocados. A TV só troca o *shell* (`MpvSurfaceView`
> fullscreen, sem bottom toolbar do mobile).

---

### Etapa 10 — `TvNavGraph.kt` (sub-navegação TV)

```kotlin
@Composable
fun TvNavGraph() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "tv/dashboard") {
        composable("tv/dashboard") { TvDashboardScreen() }
        composable(
            "tv/category/{catId}",
            arguments = listOf(navArgument("catId") { type = NavType.StringType })
        ) { TvCategoryGridScreen() }
        composable(
            "tv/detail/{playlistId}/{streamId}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("streamId")   { type = NavType.IntType },
            )
        ) { TvDetailScreen() }
        composable(
            "tv/player/{playlistId}/{streamId}/{kind}",
            arguments = listOf(
                navArgument("playlistId") { type = NavType.StringType },
                navArgument("streamId")   { type = NavType.IntType },
                navArgument("kind")       { type = NavType.StringType },
            )
        ) { TvPlayerScreen() }
        composable("tv/settings") { TvSettingsScreen() }
    }
}
```

---

### Etapa 11 — `TvSettingsScreen.kt` (credenciais + device id)

Mesmo conteúdo do `PlaylistSettingsBody` (mobile), mas em uma
**única coluna** centralizada, com `OutlinedTextField` em `focusable`:

```kotlin
@Composable
fun TvSettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(48.dp)) {
        Text("Configurações", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuário") },
            modifier = Modifier.focusable(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.focusable(),
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { save() }, modifier = Modifier.focusable()) {
            Text("Salvar e conectar")
        }
    }
}
```

> **IME em TV**: muitas TVs não têm teclado virtual. Prever um
> `Actions.add(Intent.ACTION_VIEW).addCategory(LEANBACK_LAUNCHER)`
> para o usuário conectar um teclado Bluetooth, OU abrir um
> `DialogWrapper` com `EditText` + `setShowSoftInputOnFocus(true)`.

---

### Etapa 12 — Suporte a D-pad em telas compartilhadas

Quando uma Composable do **mobile** precisa rodar em TV (ex.: o
player fullscreen), já funciona com `MPVVK` e `Hardware.kt`, mas
adicione suporte explícito:

```kotlin
// apps/android/app/src/main/java/app/ehtudo/iptv/ui/tv/TvFocusHelpers.kt

@Composable
fun rememberTvFocus(onEnter: () -> Unit = {}, onLeave: () -> Unit = {}): Modifier {
    return Modifier
        .focusable()
        .onFocusChanged { state ->
            if (state.isFocused) onEnter() else onLeave()
        }
}
```

Uso:
```kotlin
val tabModifier = rememberTvFocus()
Box(modifier = tabModifier) { ... }
```

---

### Etapa 13 — Ícones TV

Adicionar em `apps/android/app/src/main/res/mipmap-anydpi-v26/`:

```xml
<!-- ic_launcher_tv.xml (TV adaptive icon) -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_ehiptv_logo_fg" />
</adaptive-icon>
```

E referenciar em `app/build.gradle.kts`:
```kotlin
productFlavors {
    create("tv") {
        manifestPlaceholders["appBanner"] = "@mipmap/ic_launcher_tv"
    }
}
```

---

### Etapa 14 — Build & verificação

```bash
cd apps/android
./gradlew tasks | grep -E "(mobile|tv)"

# debug TV
./gradlew :app:assembleTvDebug
# saída: app/build/outputs/apk/tv/debug/app-tv-debug.apk

# release TV
./gradlew :app:assembleTvRelease
# saída: app/build/outputs/apk/tv/release/app-tv-release-unsigned.apk
```

Testar no emulador TV:
```bash
# criar AVD TV (AVD image: "Android TV 1080p")
$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager create avd \
    -n ehIptvTv \
    -k "system-images;android-34;google_apis;x86_64" \
    -d "tv_1080p"

# iniciar
emulator -avd ehIptvTv -no-window -no-audio &

# instalar
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
adb shell am start -n app.ehtudo.iptv.tv/app.ehtudo.iptv.MainTvActivity
```

Testar em TV física (ex.: Mi TV, Nvidia Shield):
```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/tv/debug/app-tv-debug.apk
adb shell am start -n app.ehtudo.iptv.tv/.MainTvActivity
```

---

### Etapa 15 — Smoke test mínimo

Checklist de QA manual:

1. ✅ App aparece na seção **"Aplicativos"** do Leanback Launcher.
2. ✅ Sem `touchscreen` — navegação por **D-pad** (setas + OK).
3. ✅ Foco visível em cada `Card` (borda / scale).
4. ✅ `Carousel` rola com seta ←/→ e dá snap alinhado.
5. ✅ Player MPV abre fullscreen, sem `bottom bar` mobile.
6. ✅ `Carousel` de "Continue Watching" mantém posição ao voltar
   (use `rememberSaveable` no `TvLazyColumn`).
7. ✅ `Back` no controle remoto fecha: detail → dashboard → launcher.
8. ✅ Credenciais persistem na mesma `SharedPreferences` (mesmo
   `applicationId` base; TV usa `app.ehtudo.iptv.tv` mas vê o mesmo
   `dataStore` se `applicationIdSuffix` for `.tv`).
9. ✅ Sleep/wake não destrói o player (libmpv retém o handle).
10. ✅ `adb logcat | grep -E "(mpv|MPVPlayer|TvDashboard)"` — sem
    `UnsatisfiedLinkError` / `ClassNotFoundException`.

---

## 3. Resumo por área de impacto

| Área | Arquivos | Tipo |
| --- | --- | --- |
| Build / flavors | `app/build.gradle.kts`, `gradle/libs.versions.toml` | modificação |
| Manifest | `app/src/main/AndroidManifest.xml` | modificação |
| Theme | `res/values/themes.xml`, `res/values-tv/themes.xml` | novo |
| Banner | `res/drawable-tv/tv_banner.xml` | novo |
| ícones TV | `res/mipmap-anydpi-v26/ic_launcher_tv.xml` | novo |
| Activity TV | `MainTvActivity.kt` | novo |
| UI TV | `ui/tv/Tv*.kt` (8 arquivos) | novo |
| Player | `player/MPVPlayer.kt`, `player/MPVLib.kt` | **sem mudança** |
| Data / Repo | `data/**/*.kt` | **sem mudança** |
| Networking | `networking/**/*.kt` | **sem mudança** |

---

## 4. Checklist de publicação na Play Store

> A Play Store exige **dois listings separados** para `mobile` e `tv`:

1. **Console Play** → *Criar app* → "App Android TV":
   - Tipo: **App para TV**.
   - Manifestar `uses-feature leanback` + `banner`.
   - APK de upload: `app-tv-release-unsigned.apk` (assinar via
     `apksigner`).
2. Checklist:
   - [ ] Banner 320×180.
   - [ ] `android:resizeableActivity="false"` na TV activity.
   - [ ] Sem `touchscreen` required.
   - [ ] D-pad navegação funcional no emulador.
   - [ ] Sem `IllegalStateException` em TV física.
   - [ ] Search/Detail screens legíveis a 3 m de distância.

---

## 5. Próximos passos (após o MVP)

| # | Tarefa | Justificativa |
| --- | --- | --- |
| 1 | `SearchFragment` do Leanback (legado) | TV precisa de busca global via lado direito da home. |
| 2 | `BrowseSupportFragment` com `HeadersSupportFragment` | Top-shelves / categorias se o usuário quiser. |
| 3 | Suporte a `MediaSession` + `MediaController` | Faz aparecer controles na notificação e na tela de lock. |
| 4 | `GameController` mapping (Sn30 / ipega) | Brasileiros usam muito controle bluetooth genérico. |
| 5 | `DynamicSoundSpeed` para legendas | Ajustar legendas em tempo real (D-pad ↑/↓). |
| 6 | CI: `assembleTvDebug` e `assembleTvRelease` em jobs separadas | Build matrix no GitHub Actions. |
| 7 | `topshelf` extension (Android 14+) | Recomendações na home do Google TV. |

---

## 6. Referências no repositório

- `apps/android/app/src/main/AndroidManifest.xml` — manifest base.
- `apps/android/app/build.gradle.kts` — adicionar flavors.
- `apps/android/app/src/main/java/app/ehtudo/iptv/MainActivity.kt` —
  modelo para `MainTvActivity`.
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/AppNavigation.kt`
  — modelo para `TvNavGraph`.
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/dashboard/PlaylistDashboardScreen.kt`
  — referência de `HorizontalPager` / `LazyRow` (replicar em
  `Carousel`).
- `apps/android/app/src/main/java/app/ehtudo/iptv/ui/settings/PlaylistSettingsScreen.kt`
  — base para `TvSettingsScreen`.
- `apps/android/app/src/main/java/app/ehtudo/iptv/player/MPVPlayer.kt` /
  `MPVLib.kt` — **sem mudança**.
- `apps/android/docs/custom-ui-ehiptv.md` — base de customização
  deste branch (mesmas decisões de identidade).
- `apps/tvos/` — referência paralela em Swift/SwiftUI (mesma
  identidade de produto).
