# Android TV — APK único + qualificador `-television`

> **Abordagem alternativa** à versão do
> [`android-tv-setup.md`](android-tv-setup.md) (que usa flavors).
> Aqui **não** criamos um APK separado — tudo é resolvido pelos
> qualificadores de recursos do Android: `-television`, `-notouch`,
> `-large`, etc.
>
> Resultado: **um único APK** (`app-ehtudo-iptv.apk`) que aparece
> tanto no launcher mobile quanto no Leanback Launcher, com UI
> adaptada por `Configuration.uiMode`.

---

## 0. Por que essa abordagem?

| Critério | APK único (esta doc) | Flavors ([doc anterior](android-tv-setup.md)) |
| --- | --- | --- |
| Builds gerados | 1 APK | 2 APKs (mobile + tv) |
| `applicationId` | `app.ehtudo.iptv` | `app.ehtudo.iptv` + `app.ehtudo.iptv.tv` |
| Recursos TV | sob `res/drawable-television/`, `res/values-television/` | sob `src/tv/res/` |
| Activity | única `MainActivity` (mesma classe) | `MainActivity` + `MainTvActivity` |
| Compose UI | if/else em `isTelevision` | Composables separados |
| Play Store | 1 listing | 2 listings |
| Complexidade | **baixa** | média |
| Risco de "TV quebra mobile" | algum (precisa testar os 2 modos) | baixo (UI isolada) |

> **Recomendação**: comece com esta abordagem. Se a UI da TV
> crescer muito (search, top shelf, broadcast, etc.), migre para
> flavors.

---

## 1. Como o Android decide "TV ou mobile"

O sistema lê o `Configuration.uiMode` em `runtime`. Ele é um bitmask
combinando `UI_MODE_TYPE_TELEVISION`, `UI_MODE_TYPE_NORMAL`, etc. A
detecção é feita em runtime, não compile-time — o **mesmo APK**
responde diferente em cada device:

```kotlin
// apps/android/app/src/main/java/app/ehtudo/iptv/ui/IsTv.kt
package app.ehtudo.iptv.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

val Configuration.isTelevision: Boolean
    get() = (uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION

@Composable
@ReadOnlyComposable
fun rememberIsTv(): Boolean {
    return LocalConfiguration.current.isTelevision
}
```

Os qualificadores de recursos que o sistema usa para resolver:

| Qualificador | Pasta | Quando vale |
| --- | --- | --- |
| `-television` | `res/drawable-television/`, `res/values-television/`, `res/mipmap-television-anydpi-v26/` | device é TV (Leanback) |
| `-notouch`   | `res/drawable-notouch/`, `res/values-notouch/` | device sem tela touch |
| `-large`     | `res/values-large/` | telas >= 640 dp |
| `-night`     | `res/values-night/` | modo escuro |
| `-pt-rBR`    | `res/values-pt-rBR/` | idioma pt-BR |

`Configuration.uiMode` combina vários bits. A pasta `-television`
também é casada com `-night-television` (TV + noturno).

---

## 2. Recursos a adicionar (somente `res/`)

Tudo fica em `apps/android/app/src/main/res/`. Nada de `src/tv/`.

### 2.1. Banner (obrigatório para o Leanback Launcher)

O Leanback Launcher exibe um banner 320×180. Sem `banner`, o app
**não aparece** na home da TV.

```bash
mkdir -p apps/android/app/src/main/res/drawable-television
```

```xml
<!-- apps/android/app/src/main/res/drawable-television/banner.xml -->
<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/ehtudo_tv_banner_bg" />
    <item
        android:gravity="center"
        android:width="220dp"
        android:height="110dp"
        android:drawable="@drawable/ic_ehiptv_logo" />
</layer-list>
```

Adicione em `res/values/colors.xml`:
```xml
<color name="ehtudo_tv_banner_bg">#0A0A0A</color>
```

### 2.2. Ícone do launcher (TV)

Reuse o mesmo adaptive icon mobile, ou crie um dedicado para TV:

```bash
mkdir -p apps/android/app/src/main/res/mipmap-television-anydpi-v26
```

```xml
<!-- apps/android/app/src/main/res/mipmap-television-anydpi-v26/ic_launcher.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

```xml
<!-- apps/android/app/src/main/res/mipmap-television-anydpi-v26/ic_launcher_round.xml -->
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

> **Por que não usar só `mipmap-anydpi-v26/ic_launcher.xml`** —
> funciona em ambos, mas a versão `-television-` permite um
> foreground diferente para TV (ex.: borda, mais texto, menos
> detalhe) sem duplicar os PNGs `mipmap-hdpi/...png`.

### 2.3. Strings TV (opcional)

```bash
mkdir -p apps/android/app/src/main/res/values-television
```

```xml
<!-- apps/android/app/src/main/res/values-television/strings.xml -->
<resources>
    <!-- rótulos simplificados para TV (D-pad, foco maior) -->
    <string name="tv_play_label">Pressione OK para assistir</string>
    <string name="tv_open_settings">Configurações (OK)</string>
    <string name="tv_dashboard_title">EH! IPTV</string>

    <!-- tamanhos maiores -->
    <integer name="card_width">100</integer>
    <integer name="card_height">140</integer>
</resources>
```

Replicar em outros idiomas:
```bash
mkdir -p apps/android/app/src/main/res/values-television-pt-rBR
mkdir -p apps/android/app/src/main/res/values-television-tr
```

```xml
<!-- res/values-television-pt-rBR/strings.xml -->
<resources>
    <string name="tv_play_label">Pressione OK para assistir</string>
    <string name="tv_open_settings">Configurações (OK)</string>
    <string name="tv_dashboard_title">EH! IPTV</string>
</resources>
```

```xml
<!-- res/values-television-tr/strings.xml -->
<resources>
    <string name="tv_play_label">Izlemek için OK\'a basın</string>
    <string name="tv_open_settings">Ayarlar (OK)</string>
    <string name="tv_dashboard_title">EH! IPTV</string>
</resources>
```

### 2.4. Tema TV (opcional)

```bash
mkdir -p apps/android/app/src/main/res/values-television
```

```xml
<!-- res/values-television/themes.xml -->
<resources>
    <style name="Theme.AnotherIPTVPlayer" parent="android:Theme.Material.NoActionBar">
        <item name="android:windowBackground">@color/ehtudo_tv_banner_bg</item>
        <item name="android:windowFullscreen">true</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

> O tema `Material` (não `Material3`) só vale até o `setContent { }`
> rodar; depois o Compose assume. Mantenha-o simples — só
> `windowBackground` para evitar flash branco.

### 2.5. Dimens TV (opcional)

```xml
<!-- res/values-television/dimens.xml -->
<resources>
    <dimen name="card_width">220dp</dimen>
    <dimen name="card_height">140dp</dimen>
    <dimen name="tv_title">36sp</dimen>
    <dimen name="tv_subtitle">20sp</dimen>
</resources>
```

Em Compose:
```kotlin
val cardWidth = dimensionResource(R.dimen.card_width)
```

---

## 3. Manifest (mínimo)

```xml
<!-- apps/android/app/src/main/AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- ...existing permissions... -->

    <!-- Suporte a TV (opcional, não bloqueia mobile) -->
    <uses-feature
        android:name="android.software.leanback"
        android:required="false" />
    <uses-feature
        android:name="android.hardware.touchscreen"
        android:required="false" />

    <application
        android:name=".AnotherIptvPlayerApp"
        android:banner="@drawable/banner"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.AnotherIPTVPlayer">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:supportsPictureInPicture="true"
            android:resizeableActivity="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboard|keyboardHidden|navigation|uiMode"
            android:theme="@style/Theme.AnotherIPTVPlayer">

            <!-- A MESMA activity é o launcher mobile e o launcher TV -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- WorkManager service (existente) -->
        <service
            android:name="androidx.work.impl.foreground.SystemForegroundService"
            android:foregroundServiceType="dataSync"
            tools:node="merge" />
    </application>
</manifest>
```

**Mudanças vs. manifest atual**
1. ➕ `android:banner="@drawable/banner"` no `<application>`.
2. ➕ `<uses-feature android:name="android.software.leanback" android:required="false" />`.
3. ➕ `<uses-feature android:name="android.hardware.touchscreen" android:required="false" />`.
4. ➕ `<category android:name="android.intent.category.LEANBACK_LAUNCHER" />` no intent-filter.

> `required="false"` em ambos `uses-feature` faz o app **continuar
> visível** em qualquer device. Quem é TV, abre com Leanback
> launcher; quem não é, abre com launcher mobile.

---

## 4. Compose — branch em `isTelevision`

### 4.1. Root: `MainActivity`

Detectar modo e escolher a tela certa:

```kotlin
// apps/android/app/src/main/java/app/ehtudo/iptv/MainActivity.kt
class MainActivity : ComponentActivity() {

    private val playerState = PlayerActivityState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AnotherIptvPlayerApp
        setContent {
            AnotherIptvPlayerTheme {
                LaunchedEffect(Unit) {
                    snapshotFlow { playerState.brightnessOverride.value }
                        .collect { override ->
                            val lp = window.attributes
                            lp.screenBrightness = override ?: -1f
                            window.attributes = lp
                        }
                }
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
                        val isTv = rememberIsTv()
                        if (isTv) {
                            TvDashboardScreen()      // nova tela (Etapa 4.3)
                        } else {
                            AppNavigation()          // mobile (existente)
                        }
                    }
                }
            }
        }
    }

    // onUserLeaveHint / onPictureInPictureModeChanged ...
}
```

> **Importante**: `enableEdgeToEdge()` é mantido, mas em TV ele
> apenas não faz nada (TV já é edge-to-edge). Sem `setSystemBars`
> explícito — TVs não têm `StatusBar` visível.

### 4.2. Activity manifest também muda

```xml
<activity
    android:name=".MainActivity"
    ...
    android:supportsPictureInPicture="true"   <!-- mobile only -->
    android:configChanges="...|uiMode"        <!-- CRÍTICO: uiMode permite
                                                  detecta TV em runtime -->
    ...>
```

`configChanges="uiMode"` é a chave — quando o usuário conecta um
teclado/controla via `uiMode` muda, a Activity não é recriada.

### 4.3. `TvDashboardScreen` (mínimo viável)

```kotlin
// apps/android/app/src/main/java/app/ehtudo/iptv/ui/tv/TvDashboardScreen.kt
package app.ehtudo.iptv.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.*
import app.ehtudo.iptv.ui.LocalPlaylistContentStore

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDashboardScreen() {
    val store = LocalPlaylistContentStore.current
    val live by store.liveByCategory.collectAsStateWithLifecycle()
    val vod  by store.vodByCategory.collectAsStateWithLifecycle()
    val series by store.seriesItemsByCategoryId.collectAsStateWithLifecycle()

    TvLazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TvHeroBanner() }
        item { TvCategoryRow(title = "Ao vivo", items = live) }
        item { TvCategoryRow(title = "Filmes", items = vod) }
        item { TvCategoryRow(title = "Séries", items = series) }
    }
}
```

> O pacote `ui/tv/` é **compartilhado** com o app mobile — não
> precisa de sourceSet `src/tv/`. As classes compilam no mesmo
> APK; apenas o `MainActivity` decide qual exibir.

### 4.4. Dependência TV (mínima)

```toml
# apps/android/gradle/libs.versions.toml
[versions]
tvFoundation = "1.0.0-alpha10"

[libraries]
androidx-tv-foundation = { group = "androidx.tv", name = "tv-foundation", version.ref = "tvFoundation" }
androidx-tv-material =  { group = "androidx.tv", name = "tv-material",     version.ref = "tvFoundation" }
```

```kotlin
// apps/android/app/build.gradle.kts — dependencies
dependencies {
    // ...existing code...

    // TV Compose — soma ~ 1.5 MB ao APK e dá Carousel/TvLazyColumn.
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
}
```

> Sem isso, `TvLazyColumn` e `Carousel` não compilam. **Não há
> como usar `-television` qualifiers com `androidx.tv` sem essa
> dep** — ela é compile-time, não runtime.

---

## 5. Recursos que **não** precisam ser duplicados

Os qualificadores `-television` herdam automaticamente dos
`res/` raiz se não houver sobrescrita:

| Recurso | Usado por TV? | Como é resolvido |
| --- | --- | --- |
| `mipmap-hdpi/ic_launcher.png`      | ✅ | mesmo PNG |
| `mipmap-anydpi/ic_launcher.xml`    | ✅ | adaptive icon (a menos que `-television-anydpi-v26` exista) |
| `drawable/ic_launcher_background.xml` | ✅ | mesmo vetor |
| `drawable/ic_launcher_foreground.xml` | ✅ | mesmo vetor |
| `values/strings.xml`               | ✅ | mesmas strings |
| `values/colors.xml`                | ✅ | mesmas cores |
| `values/themes.xml`                | ✅ | a menos que `values-television/themes.xml` exista |
| `values-pt-rBR/strings.xml`        | ✅ | funciona em TV com locale pt-BR |
| `values-tr/strings.xml`            | ✅ | mesmo |

> **Princípio**: só adicione a pasta `-television-*` para o que
> **precisar** mudar. O resto é compartilhado.

---

## 6. Mudanças no `AndroidManifest.xml` (resumo)

Antes:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
</intent-filter>
```

Depois:
```xml
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>
```

E adicionar:
```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

No `<application>`:
```xml
android:banner="@drawable/banner"
```

---

## 7. Àrvore final de `res/`

```
res/
├── drawable/
│   ├── ic_launcher_background.xml        (existente — reusado)
│   ├── ic_launcher_foreground.xml        (existente — reusado)
│   └── ic_ehiptv_logo.png                (existente — reusado)
├── drawable-television/                  ← NOVO
│   └── banner.xml                        (banner 320×180 com logo)
├── mipmap-anydpi/                        (existente — reusado em mobile)
│   ├── ic_launcher.xml
│   └── ic_launcher_round.xml
├── mipmap-television-anydpi-v26/         ← NOVO (opcional)
│   ├── ic_launcher.xml
│   └── ic_launcher_round.xml
├── mipmap-mdpi/ … mipmap-xxxhdpi/        (existente — PNGs reusados)
├── values/                               (existente — reusado)
│   ├── colors.xml
│   ├── strings.xml
│   └── themes.xml
├── values-night/themes.xml               (existente — reusado)
├── values-pt-rBR/strings.xml             (existente — reusado)
├── values-television/                    ← NOVO
│   ├── strings.xml                       (rótulos TV)
│   ├── themes.xml                        (tema TV)
│   └── dimens.xml                        (tamanhos TV)
├── values-television-pt-rBR/             ← NOVO
│   └── strings.xml
├── values-television-tr/                 ← NOVO
│   └── strings.xml
├── values-tr/strings.xml                 (existente — reusado)
└── xml/                                  (existente)
    ├── backup_rules.xml
    └── data_extraction_rules.xml
```

---

## 8. Build & verificação

```bash
cd apps/android
./gradlew :app:assembleDebug

# saída única:
# app/build/outputs/apk/debug/app-debug.apk (mobile + TV)
```

Mesma APK roda em ambos:
```bash
# mobile
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n app.ehtudo.iptv/.MainActivity

# TV (no emulador Android TV)
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n app.ehtudo.iptv/.MainActivity
```

---

## 9. Smoke test (10 itens)

1. ✅ APK instala em **mobile** (sem D-pad) e abre em `AppNavigation()`.
2. ✅ APK instala em **TV** (Leanback Launcher) e abre em `TvDashboardScreen()`.
3. ✅ Recursos `drawable-television/banner.xml` aparecem no
   Leanback Launcher.
4. ✅ Recurso `mipmap-television-anydpi-v26/ic_launcher.xml` é
   resolvido em TV (verifique com `aapt2 dump`).
5. ✅ D-pad navega entre `Card`s com foco visível.
6. ✅ `Carousel` rola com setas e dá snap.
7. ✅ Back fecha detail → dashboard → launcher.
8. ✅ Credenciais persistem entre execuções (mesmo `applicationId`).
9. ✅ `Configuration.uiMode` muda em runtime ao alternar o AVD
   (ajuda a verificar o branch em `MainActivity`).
10. ✅ `adb logcat` sem `UnsatisfiedLinkError` / `ClassNotFoundException`.

---

## 10. Quando migrar para flavors

Sinais de que a abordagem de APK único ficou complexa:

- [ ] `TvDashboardScreen` tem > 500 linhas.
- [ ] Tema/strings de TV conflitam com mobile (ex.: música de
  boot, voz, etc.).
- [ ] TV precisa de permissões que mobile não tem (ex.:
  `android.permission.MEDIA_CONTENT_CONTROL`).
- [ ] Play Store rejeita o listing único (já vi acontecer com
  apps que declaram `LEANBACK_LAUNCHER` mas não entregam TV UI).
- [ ] Você quer publicar como **app Android TV** no Play (precisa
  de banner + ajustes de listagem próprios).

Quando algum desses for `true`, migre para
[`android-tv-setup.md`](android-tv-setup.md) (flavors).

---

## 11. Referências

- [`android-tv-setup.md`](android-tv-setup.md) — abordagem com
  flavors (comparação).
- `apps/android/docs/custom-ui-ehiptv.md` — base de customização
  Eh!Iptv.
- `apps/android/app/src/main/AndroidManifest.xml` — ponto de
  partida.
- `apps/android/app/src/main/res/values/themes.xml` — tema base
  a ser sobrescrito em `values-television/`.
- `apps/android/app/src/main/res/mipmap-anydpi/ic_launcher.xml` —
  adaptive icon base.
- [Documentação oficial — Provide
  resources](https://developer.android.com/guide/topics/resources/providing-resources)
- [Documentação oficial — TV apps
  checklist](https://developer.android.com/training/tv/start/start)
