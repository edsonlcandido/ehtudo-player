# Custom UI — Eh!Iptv (apps/android)

> Linha do tempo e roteiro de customização do app Android nativo
> (`apps/android/`) a partir do commit **f71a552ff158be95639a9afe6dc00dd6fabb43d8**
> (branch `ehiptv/customize-apk`).
>
> Cada item abaixo é um **commit** + o **que mudou** + **por que foi
> necessário** para levar o app do estado genérico
> `dev.android.anotheriptvplayer` para a identidade **Eh!Iptv** com
> fluxo single-tenant, splash e dashboard próprios.

---

## 0. Pré-requisitos observados na base

| Item | Valor |
| --- | --- |
| Stack base | Kotlin + Jetpack Compose + Room + libmpv via JNI |
| `applicationId` original | `dev.android.anotheriptvplayer` |
| `applicationId` final | `app.ehtudo.iptv` |
| `versionCode` / `versionName` | `1` / `1.0` |
| `compileSdk` / `targetSdk` / `minSdk` | `36` / `36` / `26` |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Java target | 11 (build JDK 17) |
| Estado dos arquivos legados | removidos (FavoritesScreen, M3uDashboardScreen simplificado) |

---

## 1. Linha do tempo dos commits (cronológica)

```
d7f1abd  d7f1abdb2e67f75e45764049a45064e612a05ae1  instruções para compilar os apk
60401be  60401bec8b9136e854de947d95e5dab31d4b279f  primeira build do apk
acc4122  acc412258a1b7ca30729a6a557cc4db989b9e0a1  suporte a idioma portugues br
1e3402d  1e3402d01ef274efefc4605ff60546e49922e78d  Alterado identificação para app.ehtudo.iptv
dbf8e1f  dbf8e1f52378193ed54708ecfb68edd9c763153f  nova pagina de configuração e fluxo de inicialização, icones e mais personalização
a315b41  a315b41a58d96eb167b0f3555fb5b30583a51fd3  atualizado e corrigido carregar em configuracoes para inputar usuario e senha
da537ba  da537ba957c471e1370a13f4b76bde0ce2553d43  fix: update JNI package name from dev.android.anotheriptvplayer to app.ehtudo.iptv in mpv_jni.cpp
848a3ef  848a3efede159af4a1a8cdba263ca0defd61ebae  fix: clear authError on save, reactive playlist observation, observeById DAO/repo
3d7e118  3d7e118436f20b9740af17d4a83042cb7d712235  atualizado BUILD_APK para essa instancia do dev container
b46f317  b46f3176fa255a09633a0391ca2814dcffabdeb3  feat: ajuste na navegação inicial e simplificação de textos de configurações
e9f0380  e9f0380b518befd0314a33b0593b325dccc3cd55  customizando o apk
bf21661  bf2166101ef61d8d05060dbb39cfe752e9e68c2d  feat: aba de favoritos no dashboard, nova splash screen e melhoria na seleção da aba inicial
```

> Use `git log f71a552ff158be95639a9afe6dc00dd6fabb43d8..HEAD --reverse`
> para reproduzir a lista em qualquer clone.

---

## 2. Roteiro de customização (passo a passo)

### Etapa 1 — `d7f1abd` — Instruções de build

**O que foi feito**
- Criado `apps/android/BUILD_APK.md` (414 linhas) documentando pré-requisitos
  (JDK 17, Android SDK 36, NDK 26.3.11579264, CMake 3.22.1), comandos
  `./gradlew assembleRelease` / `assembleDebug`, fluxo de assinatura
  (`apksigner`), estrutura de saída e variáveis de ambiente.

**Por que**
- A base não tinha documentação de build; qualquer nova pessoa
  precisava descobrir via tentativa e erro que o projeto exige NDK +
  CMake 3.22.1 e JDK 17 (NÃO 25).

**Arquivos**
- `apps/android/BUILD_APK.md` (novo)

---

### Etapa 2 — `60401be` — Primeira build do APK

**O que foi feito**
- Adicionado `apps/android/.gitignore` cobrindo `build/`, `.gradle/`,
  `local.properties`, `*.iml`.
- Ajustado `apps/android/app/src/main/AndroidManifest.xml` com permissões
  e bloco `<application>` necessários para PiP, downloads e cleartext
  HTTP.

**Por que**
- Limpar artefatos locais antes de commitar e validar que o app
  empacota `libmpv.so` por ABI sem quebrar.

**Arquivos**
- `apps/android/.gitignore` (novo)
- `apps/android/app/src/main/AndroidManifest.xml`

---

### Etapa 3 — `acc4122` — Suporte a português (pt-BR)

**O que foi feito**
- Criado `apps/android/app/src/main/res/values-pt-rBR/strings.xml`
  (310 novas linhas) com tradução completa da UI.
- Adicionadas 87 novas entradas em `values/strings.xml` (en — base) para
  alimentar o locale pt-BR.
- Reescritas de comentários e labels hard-coded em:
  - `M3UParser.kt`
  - `CategoryPickerSheet.kt`, `PlaylistDashboardScreen.kt`,
    `CategoryGrid.kt`, `LiveCategoryDetailScreen.kt`,
    `MovieCategoryDetailScreen.kt`, `SeriesCategoryDetailScreen.kt`
  - `DetailComponents.kt`, `MovieDetailScreen.kt`,
    `SeriesDetailComponents.kt`, `SeriesDetailScreen.kt`
  - `AddM3UPlaylistScreen.kt`, `AddXtreamPlaylistScreen.kt`,
    `PlaylistScreen.kt`
  - `SearchBody.kt`, `PlaylistSettingsScreen.kt`

**Por que**
- O app Eh!Iptv é voltado ao mercado BR; precisava de localização
  completa (não apenas placeholder) e de i18n limpo em todos os
  Composables hard-coded.

**Como reproduzir**
1. Adicionar `values-pt-rBR/strings.xml`.
2. Substituir literais em Composables por `stringResource(R.string.*)`.
3. Adicionar entradas em `values/strings.xml` (inglês) — sempre manter
   duas línguas.

**Arquivos** — 18 arquivos alterados, +569 / −144 linhas.

---

### Etapa 4 — `1e3402d` — Rebranding: `app.ehtudo.iptv`

**O que foi feito**
- Renomeação em massa do pacote Java `dev.android.anotheriptvplayer` →
  `app.ehtudo.iptv` em **95 arquivos** (Kotlin + XML + schema Room).
- `apps/android/app/build.gradle.kts`: `namespace` e `applicationId`
  atualizados.
- `apps/android/app/schemas/app.ehtudo.iptv.data.local.AppDatabase/1.json`
  regenerado (Room schema acompanha o novo package).
- Pacotes de teste (`androidTest`/`test`) renomeados.

**Por que**
- Identidade do produto Eh!Iptv. Sem rename, o `applicationId` original
  colide na Play Store e o JNI não consegue localizar as classes Java
  via `JNI_OnLoad`.

**Como reproduzir**
1. Atualizar `namespace` e `applicationId` em `app/build.gradle.kts`.
2. `git mv` da árvore `dev/android/anotheriptvplayer/` para
   `app/ehtudo/iptv/`.
3. `Find/Replace` em todos os `.kt` e `.xml`:
   `dev.android.anotheriptvplayer` → `app.ehtudo.iptv`.
4. Deletar `app/schemas/dev.android.anotheriptvplayer.data.local.AppDatabase/`
   e rodar `assembleDebug` para regenerar com o novo namespace.
5. `BUILD_APK.md`: atualizar referências de pacote.

**Arquivos** — 95 arquivos, +1650 / −393 linhas.

---

### Etapa 5 — `dbf8e1f` — Fluxo single-tenant + ícones

**O que foi feito**
- **Config automática** — novos arquivos:
  - `data/AppConfig.kt` — define `DEFAULT_PLAYLIST_NAME`,
    `DEFAULT_SERVER_URL`, `DEFAULT_USER_AGENT`.
  - `data/DeviceIdProvider.kt` — gera e persiste um ID único por
    instalação (Settings → "device id").
- **Bootstrap** — `AnotherIptvPlayerApp.kt` e `MainActivity.kt`
  injetam `AppConfig` + `DeviceIdProvider` via `CompositionLocal`.
- **Navegação** — `AppNavigation.kt` agora tem rota `splash`; ao
  iniciar, sem credenciais → `Settings`; com credenciais → `Live TV`.
- **Settings reescrito** — `PlaylistSettingsScreen.kt` perdeu o card
  de server-timezone e ganhou bloco de credenciais editáveis +
  device id (com snackbar "copiado").
- **Tela de playlist** — `PlaylistDashboardScreen.kt` e
  `LocalRepositories.kt` ajustadas para o novo contrato.
- **Ícones do launcher** — gerados para todas as densidades
  (`mdpi`/`hdpi`/`xhdpi`/`xxhdpi`/`xxxhdpi`) em `.png` e removidos os
  `.webp` legados; também foram trocados os vetores
  `ic_launcher_background.xml` e `ic_launcher_foreground.xml`.
- Adicionados `ic_ehiptv_logo.png` e `ic_ehiptv_logo_fg.png` em
  `res/drawable/`.
- `shared/design/ehiptv-icon.png` adicionado (fonte única dos ícones).
- `session-ses_142f.md` anexado para registro das decisões da sessão.

**Por que**
- Eh!Iptv só tem 1 servidor; o usuário não deveria gerenciar várias
  playlists. Auto-criar a playlist padrão simplifica o onboarding.
- O ícone do app precisava refletir a marca Eh!Iptv em vez do
  placeholder original.

**Arquivos** — 39 arquivos, +7175 / −402 linhas.

**Como reproduzir**
1. Adicionar `AppConfig.kt` com `companion object` de constantes.
2. Adicionar `DeviceIdProvider.kt` (UUID em `SharedPreferences`).
3. Em `MainActivity.kt`, prover via `LocalDeviceIdProvider` /
   `LocalAppConfig`.
4. Em `AnotherIptvPlayerApp.kt`, registrar `Worker`/singleton para
   que o `RoomDatabase` aceite o `databaseName` fixo.
5. Em `AppNavigation.kt`, criar `Routes.SPLASH` que decide a aba
   inicial com base em `playlist.username.isBlank()`.
6. Rebuild dos ícones:
   ```bash
   python3 regenerate_launcher_icons.py
   ```
   (commit `e9f0380` adiciona o script — Etapa 11).

---

### Etapa 6 — `a315b41` — Corrigir fluxo de credenciais no Settings

**O que foi feito**
- `PlaylistDashboardScreen.kt` reescrito (251 linhas modificadas) para
  mostrar um card de **input de usuário/senha** na primeira execução
  (substituindo o antigo "open settings to configure").
- `AppNavigation.kt` simplificado — redireciona para o dashboard com
  bottom-sheet "Adicionar credenciais" quando a playlist está vazia.
- `PlaylistContentStore.kt` ganhou helpers para teste de credenciais
  sem precisar do `XtreamApiClient.verify()` completo.
- Strings novas em `values/`, `values-pt-rBR/`, `values-tr/`.

**Por que**
- O fluxo da Etapa 5 levava o usuário para Settings, mas o usuário
  novo nem sabia onde clicar. Solução: input inline no Dashboard.

**Arquivos** — 7 arquivos, +878 / −6206 linhas (redução de
  `session-ses_142f.md`).

---

### Etapa 7 — `da537ba` — Conserto do JNI package name

**O que foi feito**
- `apps/android/app/src/main/cpp/mpv_jni.cpp`: trocadas 22 ocorrências
  de `dev/android/anotheriptvplayer` → `app/ehtudo/iptv` nos
  `FindClass`/`GetMethodID` para o player mpv.

**Por que**
- O rename do Kotlin (Etapa 4) não cobriu o C++; sem isso, o mpv
  carrega, mas o `JNI_OnLoad` falha em encontrar `MainActivity` /
  `MPVLib` e o player não inicializa.

**Como reproduzir**
- Após qualquer rename de pacote, varrer `**/*.cpp` e `**/*.h` por
  `dev/android/<…>` e atualizar `FindClass("…")`.

**Arquivos** — 1 arquivo, +22 / −22 linhas.

---

### Etapa 8 — `848a3ef` — Fixes reativos do DAO/Repo

**O que foi feito**
- `PlaylistRepository.kt`: novo `observeById(id)` que devolve
  `Flow<Playlist?>`.
- `PlaylistDao.kt`: `observeById(id)` em SQL.
- `PlaylistSettingsScreen.kt`: limpa `authError` ao salvar
  credenciais.
- `PlaylistDashboardScreen.kt`: observa a playlist de forma reativa
  (não mais `LaunchedEffect` único).

**Por que**
- Bug: ao salvar credenciais, o `authError` antigo continuava
  exibido. E a playlist sumia do dashboard após `save` porque o
  snapshot era one-shot.

**Arquivos** — 4 arquivos, +21 / −4 linhas.

---

### Etapa 9 — `3d7e118` — BUILD_APK atualizado para dev container

**O que foi feito**
- `apps/android/BUILD_APK.md` (+241 linhas) com:
  - Caminho do `ANDROID_HOME` no dev container.
  - Comando `sdkmanager --install` específico.
  - Workarounds para `aapt2`/`gradle-plugin` indisponíveis.

**Por que**
- A doc original assumiu host Linux local; o dev container tem
  `ANDROID_HOME=/opt/android-sdk` e exige `apt install` diferente.

**Arquivos** — 1 arquivo, +241 linhas.

---

### Etapa 10 — `b46f317` — Simplificação de textos e nav inicial

**O que foi feito**
- `PlaylistDashboardScreen.kt`: ajusta `HorizontalPager` para que a
  aba inicial seja **Settings** quando não há credenciais, e **Live
  TV** quando há, sem flash de re-render.
- Strings encurtadas em `values/`, `values-pt-rBR/`,
  `apps/flutter/lib/l10n/app_localizations_pt.dart` e `app_pt.arb`.

**Por que**
- i18n limpo: rótulos genéricos ("Settings") substituídos por
  "Configurações" / "Settings" consistentes entre as duas línguas;
  evitar render intermediário na primeira abertura.

**Arquivos** — 5 arquivos, +19 / −4 linhas.

---

### Etapa 11 — `e9f0380` — Customização pesada do APK

**O que foi feito**
- **Novo componente** — `ui/components/DebouncedQuery.kt` (34 linhas)
  com `rememberDebouncedQuery(initial: String, delay: Long)` para
  buscas com debounce.
- **`CategoryPickerSheet.kt`** — toggle de "adulto" simplificado,
  filtro de busca integrado.
- **`M3uDashboardScreen.kt`** — drasticamente simplificado (59
  linhas a menos); apenas placeholder com CTA "Adicionar
  playlist M3U" porque o foco agora é Xtream.
- **`PlaylistDashboardScreen.kt`** — +93 linhas: novo layout com
  `HorizontalPager` (5 tabs: Live / Filmes / Séries / Favoritos /
  Configurações), `NavigationBar` inferior, splash antes da 1ª
  composição.
- **`LiveCategoryDetailScreen.kt`** — reescrito para listar canais
  com paginação lazy.
- **Novo arquivo** — `ui/dashboard/category/LiveChannelList.kt`
  (150 linhas) com `LazyColumn` de canais, item com `IconButton`
  favorito.
- **`MovieCategoryDetailScreen.kt`** / `SeriesCategoryDetailScreen.kt`
  — simplificados (23 linhas a menos cada).
- **`DetailComponents.kt`** — +86 linhas: novos blocos
  `HeroHeader`, `RatingBar`, `ActionButtonsRow` reutilizáveis.
- **`MovieDetailScreen.kt`** — +39 linhas: novo layout com o
  `HeroHeader`.
- **`SeriesDetailScreen.kt`** — +44 linhas: seasons list mais
  polida.
- **`PreviewPlaylistRepository.kt`** — adiciona helper
  `previewXtreamCredentials(url, user, pass)`.
- **`SearchScreen.kt`** — +106 linhas: usa `DebouncedQuery`,
  agrupa resultados por tipo.
- **`PlaylistSettingsScreen.kt`** — −43 linhas: removido card
  "Playlist Info" (que era read-only).
- **Ícones** — regenerados todos os mipmaps PNGs com o ícone final
  (menor e mais limpo); o `ic_ehiptv_logo_fg.png` foi recomprimido
  (~268 KB → 153 KB).
- **`Vendor/libmpv-android/Makefile`** — pequeno ajuste +2 linhas.
- **Script** — `regenerate_launcher_icons.py` (43 linhas) na raiz
  do repo, regenera todos os mipmaps a partir de
  `shared/design/ehiptv-icon.png`.
- **Skill** — `.opencode/skill/ehtudo-android/SKILL.md` (388 linhas)
  documentando comandos úteis do projeto.

**Por que**
- Era a "big bang" de UX: bottom navigation, splash, busca com
  debounce, e o rebrand visual completo dos ícones.

**Arquivos** — 31 arquivos, +982 / −246 linhas.

**Como reproduzir**
1. Adicionar `DebouncedQuery.kt` em `ui/components/`.
2. Em `SearchScreen.kt`, embrulhar `var query by remember { mutableStateOf("") }`
   com `val debounced by rememberDebouncedQuery(query, 300)`.
3. Em `PlaylistDashboardScreen.kt`, usar `HorizontalPager` + `NavigationBar`
   com 5 destinos.
4. Criar `LiveChannelList.kt` (extração do `LiveCategoryDetailScreen`).
5. Regenerar ícones:
   ```bash
   python3 regenerate_launcher_icons.py
   ```
   (lê `shared/design/ehiptv-icon.png` e regrava cada `mipmap-*`).

---

### Etapa 12 — `bf21661` — Aba de favoritos no Dashboard + Splash

**O que foi feito**
- **Favoritos movidos para o Dashboard** — `ui/favorites/FavoritesScreen.kt`
  (225 linhas) **REMOVIDO**; substituído por uma `tab` dentro do
  `HorizontalPager` do `PlaylistDashboardScreen.kt`.
- **`AppNavigation.kt`** — removida rota `favorites`; `LiveChannelList.kt`
  e `LiveCategoryDetailScreen.kt` ganharam hooks de
  add/remove favorito.
- **Splash screen** — `AppNavigation.kt` ganhou rota `SPLASH` que
  exibe o ícone do app (`ic_ehiptv_logo`) e decide entre ir para
  `Live TV` (com credenciais) ou `Configurações` (sem).
- **Seleção de aba inicial** — eliminada a "render → scroll" via
  `pagerState.scrollToPage`; o `currentPage` agora é decidido no
  momento da navegação.
- Strings removidas em `values/`, `values-pt-rBR/`.

**Por que**
- Favoritos eram uma rota dedicada redundante; agora fazem sentido
  como aba sempre acessível na bottom bar.
- Splash elimina o flash branco inicial e dá uma experiência de
  carregamento consistente com a identidade visual.

**Arquivos** — 7 arquivos, +181 / −404 linhas.

**Como reproduzir**
1. Mover lógica de `FavoritesScreen.kt` para uma `tab` do
   `PlaylistDashboardScreen.kt`.
2. Em `AppNavigation.kt`, registrar `Routes.SPLASH`:
   ```kotlin
   composable(Routes.SPLASH) {
       SplashScreen(onTimeout = { navController.navigate(...) })
   }
   ```
3. Pré-selecionar a aba via `pagerState`:
   ```kotlin
   val pagerState = rememberPagerState(initialPage = startTab) { 5 }
   ```
4. Limpar rotas mortas (`favorites`) e seus imports.

---

## 3. Resumo por área de impacto

| Área | Commits principais | Arquivos (final) |
| --- | --- | --- |
| Identificação / rebranding | `1e3402d`, `da537ba`, `e9f0380` | ~95 Kotlin + schema Room + ícone |
| Localização (pt-BR) | `acc4122`, `b46f317` | `values/`, `values-pt-rBR/`, `values-tr/`, ~17 Composables |
| Fluxo single-tenant | `dbf8e1f`, `a315b41`, `b46f317`, `bf21661` | `AppNavigation.kt`, `PlaylistDashboardScreen.kt`, `AppConfig.kt`, `DeviceIdProvider.kt` |
| Configurações (Settings) | `dbf8e1f`, `a315b41`, `848a3ef`, `e9f0380` | `PlaylistSettingsScreen.kt`, `LocalRepositories.kt` |
| Player (mpv) | `da537ba` | `cpp/mpv_jni.cpp` |
| Persistência reativa | `848a3ef` | `PlaylistRepository.kt`, `PlaylistDao.kt`, `PlaylistContentStore.kt` |
| UI / Compose (componentes) | `e9f0380`, `bf21661` | `DebouncedQuery.kt`, `LiveChannelList.kt`, `DetailComponents.kt`, `M3uDashboardScreen.kt` |
| Ícones / launcher | `dbf8e1f`, `e9f0380` | 10 mipmaps, 2 drawables, `regenerate_launcher_icons.py` |
| Build / docs | `d7f1abd`, `60401be`, `3d7e118` | `BUILD_APK.md`, `.gitignore`, `AndroidManifest.xml` |
| Skill / contexto | `e9f0380` | `.opencode/skill/ehtudo-android/SKILL.md` |

---

## 4. Checklist de atualização para futuras versões

> Use ao sincronizar com `upstream/main` (bsogulcan/another-iptv-player):

1. **Identidade** — re-aplicar rename `ApplicationId` se a upstream
   trocar.
2. **Localização** — rodar `git diff upstream/main -- '*.kt' | grep -E "'[^']{20,}'"`
   para encontrar strings novas não traduzidas.
3. **Fluxo single-tenant** — manter `AppConfig`/`DeviceIdProvider` se
   a upstream mudar o modelo de playlists.
4. **Favoritos** — se a upstream recriar `FavoritesScreen.kt`, mover
   a lógica novamente para a `tab` do Dashboard.
5. **Splash** — verificar se a rota `SPLASH` continua existindo em
   `AppNavigation.kt`.
6. **Ícones** — após qualquer rebrand visual, rodar
   `python3 regenerate_launcher_icons.py`.
7. **JNI** — após qualquer rename, varrer `apps/android/app/src/main/cpp/`
   por `FindClass` com o pacote antigo.
8. **Strings EN/TR** — sempre que tocar `values-pt-rBR/strings.xml`,
   replicar em `values/strings.xml` e `values-tr/strings.xml`.

---

## 5. Como reproduzir do zero (resumo)

```bash
# 1. Branch + rename de pacote
git checkout -b ehiptv/customize-apk
# atualizar namespace/applicationId em app/build.gradle.kts
# find/replace dev.android.anotheriptvplayer -> app.ehtudo.iptv
# regenerar schema Room

# 2. Localização
cp -r apps/android/app/src/main/res/values apps/android/app/src/main/res/values-pt-rBR
# traduzir todas as strings; revisar Composables hard-coded

# 3. Single-tenant
# adicionar AppConfig.kt, DeviceIdProvider.kt
# ProvisionComposition em MainActivity.kt
# reescrever AppNavigation.kt com rota SPLASH

# 4. UI/UX
# adicionar DebouncedQuery.kt
# reescrever PlaylistDashboardScreen.kt com HorizontalPager 5 abas
# mover M3uDashboard → card "Adicionar M3U"
# mover FavoritesScreen → tab Favoritos no Dashboard

# 5. Ícones
python3 regenerate_launcher_icons.py
# commitar mipmaps PNGs + drawables

# 6. JNI
sed -i 's|dev/android/anotheriptvplayer|app/ehtudo/iptv|g' \
    apps/android/app/src/main/cpp/mpv_jni.cpp

# 7. Build
cd apps/android
./gradlew assembleDebug
./gradlew assembleRelease
```

---

## 6. Referências no repositório

- `apps/android/BUILD_APK.md` — guia completo de build.
- `apps/android/app/src/main/AndroidManifest.xml` — permissões e
  tema.
- `apps/android/app/build.gradle.kts` — `applicationId`, ABIs,
  `buildConfig`.
- `shared/design/ehiptv-icon.png` — fonte do ícone.
- `regenerate_launcher_icons.py` — regenerador de mipmaps.
- `.opencode/skill/ehtudo-android/SKILL.md` — comandos úteis.
- `session-ses_142f.md` — diário de decisões da sessão.
