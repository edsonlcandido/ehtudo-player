# alterar detalhes app.ehtudo.iptv

**Session ID:** ses_142f7392fffeQ0tSCfMCFsDPKc
**Created:** 6/12/2026, 6:12:09 PM
**Updated:** 6/13/2026, 12:15:57 AM

---

## Assistant

**Tool: bash**

---

## Assistant

Now update the adaptive icon to use the new foreground and a solid background color matching the icon's black:

**Tool: write**

---

## Assistant

**Tool: write**

---

## Assistant

**Tool: todowrite**

---

## Assistant

First add the `LocalDeviceIdProvider` to the LocalRepositories file:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

Wire it in `MainActivity`:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

Add the provider to `AnotherIptvPlayerApp`:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

Now the big one — `PlaylistSettingsScreen.kt`. Let me rewrite it carefully:

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

Now I'll rewrite the file completely with the new design:

**Tool: write**

---

## Assistant

Let me clean up unused imports:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

Now `PlaylistDashboardScreen.kt` — `initialPage=3` and remove the back arrow:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

Now the big rewrite of `AppNavigation.kt`. Let me write the new file:

**Tool: write**

---

## Assistant

Now add the `firstOrCreateDefault` method to `PlaylistRepository`:

**Tool: edit**

---

## Assistant

Let me refine the bootstrap logic to handle the edge case where the remembered id no longer exists:

**Tool: edit**

---

## Assistant

Remove unused `scope`/`launch` and unused `settings_card_server` (now stale) — but actually the `settings_card_server` is a string that's no longer used. Let me check what cleanup is needed. First, look at the new AppNavigation:

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

**Tool: grep**

---

## Assistant

Let me check if any unused strings are left now:

**Tool: bash**

---

## Assistant

Now let me verify everything is in place and try a build. First, check no references to removed routes remain:

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

Let me also check that the `bootstrapState` enum is used and the `bootstrapState.value` mutation actually drives the spinner text. Currently the `BootstrapScreen` only takes the state but doesn't render any text based on it. The spinner is shown regardless. That's fine — the enum is used as state but doesn't affect UI yet. Let me make it more useful — add a small text below the spinner:

**Tool: edit**

---

## Assistant

Now build:

**Tool: bash**

---

## Assistant

**Tool: todowrite**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

Need to remove the old `.webp` files so the new `.png` doesn't conflict:

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

Build successful! Two pre-existing deprecation warnings (not from my changes). Now sign and verify:

**Tool: todowrite**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

Strings are in. Now check the HTTP server:

**Tool: bash**

---

## Assistant

**Tool: bash**

---

## Assistant

All resources are in. Let me update the todos and provide the final summary:

**Tool: todowrite**

---

## Assistant

Pronto. Build verde, APK assinado, servidor HTTP servindo.

**APK novo:**
- Arquivo: `apps/android/app/build/outputs/apk/release/app-release-signed.apk` (55.087.504 bytes)
- SHA-256: `ab20f828a25b57e469014cd0035854c5f949b05997c0e16debca5c5d766c3d7a`
- Assinatura: v2 + v3 verificadas, package `app.ehtudo.iptv`, label "Eh!Iptv"
- Download: `https://super-duper-enigma-x9qpvpxq755cgv-8000.app.github.dev/app-release-signed.apk`

**Mudanças aplicadas:**

- `data/AppConfig.kt`, `data/DeviceIdProvider.kt` — novo.
- `data/PlaylistRepository.kt` — adicionado `first()` e `firstOrCreateDefault()`.
- `data/local/PlaylistDao.kt` — query `first()`.
- `ui/AppNavigation.kt` — bootstrap (cria playlist default com `http://dnstv.top` na 1ª abertura), remove rotas ADD_*; start destination = `BOOTSTRAP`.
- `ui/dashboard/PlaylistDashboardScreen.kt` — `initialPage=3`, sem back arrow.
- `ui/settings/PlaylistSettingsScreen.kt` — hero (logo 120dp), credenciais editáveis + Save (verify+re-sync), card Device ID (monoespaçada + Copy), removido bloco server/timezone, About aponta para `https://iptv.ehtudo.app/`.
- `ui/LocalRepositories.kt`, `MainActivity.kt`, `AnotherIptvPlayerApp.kt` — `LocalDeviceIdProvider` + `deviceIdProvider` wired.
- Ícones novos (PNG em 5 densidades + drawable hero + drawable foreground-only); removidos os `.webp` antigos.
- ~13 strings novas em en / pt-rBR / tr.

---

## User

aconteu algum problema, quando eu atualizei a versão que já tinha um usuario e senha cadastrado o app carregou porém quando eu desinstalei e instalei de novo o mesmo não inicia então acredito que tenha algum problema quando inicia sem nenhuma informação de usuario e senha, eu quero que quando for uma instalação nova abra a aplicação sem verifcar usuario e senha na pagina de configuração

---

## Assistant

Vou investigar onde está falhando.

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

Plano pronto. Resumo do problema e da correção:

## Diagnóstico

A causa raiz do "app não inicia" é uma armadilha de UX, não uma exception:

1. **Fresh install** → bootstrap cria playlist com `username=""` e `password=""`.
2. Dashboard monta com `pagerState(3)` → renderiza o body do Scaffold.
3. `PlaylistDashboardScreen` chama `store.loadPlaylistSuspending(playlist)`.
4. O store detecta catálogo vazio e dispara `syncFromNetworkReplacingLocal(...)`.
5. O servidor responde com erro (parâmetros faltando) → JSON decode lança `XtreamException.Decoding`.
6. O `try/catch` no `loadPlaylistSuspending` pega a exception e seta `_loadError.value = ...`.
7. O `when` do dashboard:
   ```kotlin
   loadError != null && liveCats.isEmpty() && vodCats.isEmpty() && seriesCats.isEmpty() ->
       ErrorState(...)  // <-- TELA CHEIA, sem o HorizontalPager
   ```
8. **O body vira `ErrorState` ocupando a tela inteira**. O `HorizontalPager` deixa de ser composto.
9. O `BottomBar` (com o tab Config) ainda é desenhado pelo Scaffold, mas o tab Config chama `pagerState.animateScrollToPage(3)` num pager que não existe mais — o usuário fica preso.
10. Pior: a string `settings_server`/etc e o network timeout (60s) podem fazer a tela ficar em `LoadingState` por até 2 minutos antes de virar `ErrorState`.

**Por que atualizar do APK anterior funcionou:** o DB persistiu, então a playlist já tinha user/pass válidos. A rede respondeu OK → `ErrorState` nunca foi ativado.

## Correção

### 1. `PlaylistContentStore.loadPlaylistSuspending` — pular sync se creds vazias

Adicionar um early-return no começo, depois do `clearLists()` e do setup de `_activePlaylistId`, antes do `needsNetworkBootstrap`:

```kotlin
// No-credentials short-circuit. The Eh!Iptv build auto-creates a row
// on first launch with empty user/pass; calling the Xtream API in
// that state always fails. Skip the network sync so the content
// tabs simply show their empty state, and the user can fill in
// credentials from the settings tab.
if (playlist.username.isBlank() || playlist.password.isBlank()) {
    _isLoading.value = false
    _loadingMessage.value = null
    return
}
```

Resultado: nada de `ErrorState`, nada de `LoadingState` na primeira abertura. O `_streamsLoaded` continua `false`, mas o pager é mostrado.

### 2. `PlaylistDashboardScreen` — sempre mostrar pager quando creds vazias

Adicionar um derived state e ajustar o `when`:

```kotlin
val hasCredentials = playlist?.let { 
    it.username.isNotBlank() && it.password.isNotBlank() 
} ?: false

// ... no `when`:
when {
    !hasCredentials ->
        HorizontalPager(state = pagerState, ...) { page -> ... }
    loadError != null && liveCats.isEmpty() && ... ->
        ErrorState(...)
    isLoading && liveCats.isEmpty() && ... ->
        LoadingState(...)
    else ->
        HorizontalPager(state = pagerState, ...) { page -> ... }
}
```

Resultado: fresh install → o pager é sempre renderizado → o usuário cai na aba Config (page 3) e pode digitar.

### 3. Empty states dos content tabs com CTA

Quando o usuário tocar em Live/Filmes/Séries com creds vazias, hoje aparece só `"Nenhuma categoria ainda"`. Vou melhorar passando um `hasCredentials: Boolean` para os 3 `*TabBody` e mostrar uma mensagem específica com ícone de "Vá para Configurações":

```kotlin
if (visibleCategories.isEmpty()) {
    val msg = when {
        categories.isEmpty() && !hasCredentials -> 
            "Configure seu usuário e senha na aba Configurações."
        categories.isEmpty() -> stringResource(R.string.empty_no_categories)
        else -> stringResource(R.string.empty_categories_hidden)
    }
    EmptyTab(message = msg)
    return
}
```

Vou adicionar 3 strings (en/pt-rBR/tr): `empty_configure_credentials_prompt`.

### 4. (Bônus) Banner na aba Config para fresh install

Quando o usuário entra na aba Config com creds vazias, mostrar uma `ListItem`/`Card` no topo do card de Credenciais: "Bem-vindo! Insira seu usuário e senha para começar." — apenas se user/pass estão em branco.

## Arquivos a modificar

| Arquivo | O que muda |
|---|---|
| `data/PlaylistContentStore.kt` | Early-return no `loadPlaylistSuspending` quando creds vazias (~6 linhas + comentário). |
| `ui/dashboard/PlaylistDashboardScreen.kt` | `val hasCredentials`; novo branch `!hasCredentials -> HorizontalPager` no `when`; passar `hasCredentials` para `LiveTabBody`/`MoviesTabBody`/`SeriesTabBody`; mensagem do `EmptyTab` ciente de creds. |
| `res/values/strings.xml` + `values-pt-rBR/` + `values-tr/` | `empty_configure_credentials_prompt` + `config_welcome_banner`. |
| `ui/settings/PlaylistSettingsScreen.kt` | (opcional) Mostrar card de boas-vindas se `savedUsername.isEmpty() && savedPassword.isEmpty()`. |

## Não vou tocar

- `AppConfig.kt`, `DeviceIdProvider.kt`, `AppNavigation.kt`, `MainActivity.kt`, ícones, strings já adicionadas.
- Telas mortas (`AddXtreamPlaylistScreen.kt`, etc.) — não interferem.

## Verificação pós-build

- `assembleRelease --no-daemon` (Java 21).
- `apksigner verify` mostra v2+v3.
- `aapt2 dump resources` confirma as 2 strings novas.
- HTTP server já está servindo o diretório `app/build/outputs/apk/release/`, só sobrescrever o APK assinado.

## Risco

Mínimo. A única mudança comportamental é: o body do dashboard passa a renderizar o pager quando creds estão vazias (antes: ErrorState/LoadingState). Todas as outras transições (com creds válidas) permanecem idênticas. O early-return no `loadPlaylistSuspending` também é seguro porque se creds ficarem vazias depois (impossível pela UI atual, mas por segurança), a próxima chamada simplesmente não faz I/O.

Posso aplicar?

---

## User

apenas corrija os arquivos e me mostre os comandos para eu buildar o novo apk e depois iniciar o servidor http do python para eu fazer o download, vc não precisa executar o comando de build nem verificar apenas altere os arquivos e me mostre os comandos que eu vou fazer o build

---

## Assistant

Estou em **plan mode** (read-only), então não posso aplicar as edições agora. Vou te entregar o plano completo com o **diff exato** de cada arquivo e os **comandos prontos** para você rodar. Se quiser que eu mesmo aplique, é só sair do plan mode (responder com "aplique" / "execute" / etc.) que eu faço as edições e te entrego os comandos.

---

## 1. Diagnóstico

Fresh install → bootstrap cria playlist com `username=""` e `password=""` → `PlaylistDashboardScreen` chama `loadPlaylistSuspending(playlist)` → store tenta `syncFromNetworkReplacingLocal(...)` → servidor retorna erro (parâmetros faltando) → `XtreamException.Decoding` é captada e seta `_loadError` → o `when` do dashboard decide por `ErrorState` (tela cheia) → o `HorizontalPager` deixa de ser composto → o `BottomBar` (com o tab Config) ainda existe mas `pagerState.animateScrollToPage(3)` roda num pager que não existe → usuário fica preso. Antes, a atualização preservava o DB com creds válidas, então o `ErrorState` nunca era atingido.

## 2. Correção em 2 arquivos (mínima)

### Arquivo A — `apps/android/app/src/main/java/app/ehtudo/iptv/data/PlaylistContentStore.kt`

Localizar o início de `loadPlaylistSuspending` (linha ~117). O trecho atual começa:

```kotlin
suspend fun loadPlaylistSuspending(playlist: Playlist) {
    val token = UUID.randomUUID()
    loadToken = token

    _loadError.value = null
    _loadingMessage.value = null

    if (_activePlaylistId.value != playlist.id) {
        clearLists()
        _activePlaylistId.value = playlist.id
        _isLoading.value = true
    } else if (_liveCategories.value.isEmpty()) {
        _isLoading.value = true
    }

    try {
```

**Inserir este bloco entre `_loadingMessage.value = null` e o `if (_activePlaylistId.value != playlist.id)`:**

```kotlin
    // No-credentials short-circuit. The Eh!Iptv build auto-creates a
    // row on first launch with empty user/pass; calling the Xtream API
    // in that state always fails with a missing-parameter response.
    // Skip the network sync so the content tabs simply show their empty
    // state, and the user can fill in credentials from the settings tab.
    if (playlist.username.isBlank() || playlist.password.isBlank()) {
        _isLoading.value = false
        return
    }
```

### Arquivo B — `apps/android/app/src/main/java/app/ehtudo/iptv/ui/dashboard/PlaylistDashboardScreen.kt`

**B.1** Dentro de `PlaylistDashboardScreen` (perto da linha ~120, depois de `val seriesByCategory by ...`), adicionar o derived state:

```kotlin
    // The Eh!Iptv build auto-creates a default playlist with empty
    // credentials; until the user fills them in we don't try to load
    // the catalog. Surfacing `hasCredentials` here lets the rest of
    // the screen skip the ErrorState/LoadingState fallbacks and show
    // the pager so the user lands on the Settings tab.
    val hasCredentials = playlist?.let {
        it.username.isNotBlank() && it.password.isNotBlank()
    } ?: false
```

**B.2** Trocar o `when` (linhas ~192-204) por:

```kotlin
    when {
        // Fresh-install or wiped-credentials: skip the loading/error
        // fallbacks and let the user land on the Settings tab.
        !hasCredentials ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                userScrollEnabled = false,
            ) { page ->
                when (page) {
                    0 -> LiveTabBody(
                        playlistId = playlistId,
                        categories = liveCats,
                        byCategoryId = liveByCategory,
                        streamsLoading = !streamsLoaded,
                        hasCredentials = hasCredentials,
                        onOpenCategory = onOpenLiveCategory,
                        onPlayChannel = onPlayLive,
                        onResumeMovie = onOpenMovie,
                        onResumeEpisode = onResumeEpisode,
                    )
                    1 -> MoviesTabBody(
                        playlistId = playlistId,
                        categories = vodCats,
                        byCategoryId = vodByCategory,
                        streamsLoading = !streamsLoaded,
                        hasCredentials = hasCredentials,
                        onOpenMovie = onOpenMovie,
                        onOpenCategory = onOpenVodCategory,
                        onResumeMovie = onOpenMovie,
                        onResumeEpisode = onResumeEpisode,
                        onPlayLive = onPlayLive,
                    )
                    2 -> SeriesTabBody(
                        playlistId = playlistId,
                        categories = seriesCats,
                        byCategoryId = seriesByCategory,
                        streamsLoading = !streamsLoaded,
                        hasCredentials = hasCredentials,
                        onOpenSeries = onOpenSeries,
                        onOpenCategory = onOpenSeriesCategory,
                        onResumeMovie = onOpenMovie,
                        onResumeEpisode = onResumeEpisode,
                        onPlayLive = onPlayLive,
                    )
                    3 -> app.ehtudo.iptv.ui.settings.PlaylistSettingsBody(
                        playlistId = playlistId,
                        appVersion = appVersion,
                        onOpenDownloads = onOpenDownloads,
                        onOpenHistory = onOpenHistory,
                        modifier = Modifier.fillMaxSize(),
                    )
                    4 -> app.ehtudo.iptv.ui.search.SearchBody(
                        playlistId = playlistId,
                        onOpenMovie = onOpenMovie,
                        onOpenSeries = onOpenSeries,
                        onPlayLive = onPlayLive,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        loadError != null && liveCats.isEmpty() && vodCats.isEmpty() && seriesCats.isEmpty() ->
            ErrorState(...)
        isLoading && liveCats.isEmpty() && vodCats.isEmpty() && seriesCats.isEmpty() ->
            LoadingState(...)
        else ->
            HorizontalPager(...) { page -> ... }   // mesmo pager acima
    }
```

Para evitar duplicar o pager 2x, a forma mais limpa é **extrair o pager para um `@Composable` privado** `DashboardPager(...)` e usá-lo nos 2 branches. Pseudocódigo:

```kotlin
when {
    !hasCredentials -> DashboardPager(...)
    loadError != null && cats.isEmpty() -> ErrorState(...)
    isLoading && cats.isEmpty() -> LoadingState(...)
    else -> DashboardPager(...)
}
```

E o `@Composable` privado `DashboardPager(hasCredentials, ..., onXxx)` recebe `hasCredentials` e passa para os 3 `*TabBody`.

**B.3** Modificar `LiveTabBody`, `MoviesTabBody`, `SeriesTabBody` — adicionar parâmetro `hasCredentials: Boolean` e ajustar o `EmptyTab`:

```kotlin
// assinatura:
private fun LiveTabBody(
    playlistId: String,
    categories: List<CategoryEntity>,
    byCategoryId: Map<String, List<LiveStreamWithCategory>>,
    streamsLoading: Boolean,
    hasCredentials: Boolean,            // <-- novo
    onOpenCategory: (String) -> Unit,
    ...
)

// dentro, onde está:
if (visibleCategories.isEmpty()) {
    EmptyTab(message = if (categories.isEmpty()) 
        stringResource(app.ehtudo.iptv.R.string.empty_no_categories) 
    else 
        stringResource(app.ehtudo.iptv.R.string.empty_categories_hidden))
    return
}

// trocar por:
if (visibleCategories.isEmpty()) {
    val msg = when {
        categories.isEmpty() && !hasCredentials ->
            stringResource(app.ehtudo.iptv.R.string.empty_configure_credentials_prompt)
        categories.isEmpty() ->
            stringResource(app.ehtudo.iptv.R.string.empty_no_categories)
        else ->
            stringResource(app.ehtudo.iptv.R.string.empty_categories_hidden)
    }
    EmptyTab(message = msg)
    return
}
```

Aplicar o mesmo em `MoviesTabBody` e `SeriesTabBody`.

### Arquivo C — strings (3 locales)

`apps/android/app/src/main/res/values/strings.xml`, `values-pt-rBR/strings.xml`, `values-tr/strings.xml` — adicionar **uma linha** em cada um, no fim (antes de `</resources>`):

```xml
<!-- EN -->
<string name="empty_configure_credentials_prompt">Configure your username and password in the Settings tab to start watching.</string>
```

```xml
<!-- PT-BR -->
<string name="empty_configure_credentials_prompt">Configure seu usuário e senha na aba Configurações para começar a assistir.</string>
```

```xml
<!-- TR -->
<string name="empty_configure_credentials_prompt">İzlemeye başlamak için Ayarlar sekmesinden kullanıcı adınızı ve şifrenizi yapılandırın.</string>
```

---

## 3. Comandos para você rodar

### 3.1 Build (Java 21, sem daemon)

```bash
cd /workspaces/ehtudo-player/apps/android
env -i HOME="$HOME" PATH="/usr/local/sdkman/candidates/java/21.0.10-ms/bin:$PATH" JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.10-ms" ./gradlew :app:assembleRelease --no-daemon
```

### 3.2 Zipalign + assinar + verificar

```bash
SDK=/home/codespace/android-sdk
cd /workspaces/ehtudo-player

$SDK/build-tools/36.0.0/zipalign -v -p 4 \
  apps/android/app/build/outputs/apk/release/app-release-unsigned.apk \
  apps/android/app/build/outputs/apk/release/app-release-aligned.apk

$SDK/build-tools/36.0.0/apksigner sign \
  --ks /home/codespace/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out apps/android/app/build/outputs/apk/release/app-release-signed.apk \
  apps/android/app/build/outputs/apk/release/app-release-aligned.apk

$SDK/build-tools/36.0.0/apksigner verify --verbose \
  apps/android/app/build/outputs/apk/release/app-release-signed.apk

sha256sum apps/android/app/build/outputs/apk/release/app-release-signed.apk
```

### 3.3 Reiniciar servidor HTTP

```bash
pkill -f "http.server 8000" 2>/dev/null || true
setsid python3 -m http.server 8000 --bind 0.0.0.0 \
  --directory /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/ \
  < /dev/null > /tmp/httpd.log 2>&1 & disown

curl -sI http://127.0.0.1:8000/app-release-signed.apk
```

### 3.4 URL de download

```
https://super-duper-enigma-x9qpvpxq755cgv-8000.app.github.dev/app-release-signed.apk
```

(porta 8000 precisa estar como **Public** na aba **Ports** do VS Code)

---

## 4. Risco

Mínimo. A única mudança comportamental é: o body do dashboard passa a renderizar o pager (com a aba Config) quando creds estão vazias, em vez de `ErrorState`/`LoadingState`. Com creds válidas, o fluxo é idêntico. O early-return no `loadPlaylistSuspending` também é seguro: se as creds ficarem vazias em algum momento (não acontece pela UI atual), a próxima chamada simplesmente não faz I/O.

---

## User

pode aplicar o plano

---

## Assistant

Aplicando o plano.

**Tool: todowrite**

---

## Assistant

### 1. Early-return em `PlaylistContentStore.loadPlaylistSuspending`

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

### 2. Strings nos 3 locales

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

### 3. `PlaylistDashboardScreen.kt` — derived state + extrair pager + ajustar `when`

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

Now update the `when` body to handle the no-credentials branch and extract the pager to a private composable. First, replace the entire body:

**Tool: edit**

---

## Assistant

Now add the `DashboardPager` private composable. Let me find the right place:

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

Insert the `DashboardPager` composable right after the closing `}` of `PlaylistDashboardScreen` (line 293):

**Tool: edit**

---

## Assistant

I need to add `onResumeEpisode` to the `DashboardPager` signature:

**Tool: edit**

---

## Assistant

Now update the two `DashboardPager` call sites in the parent to pass `onResumeEpisode`:

**Tool: edit**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

### 4. Atualizar `LiveTabBody`, `MoviesTabBody`, `SeriesTabBody`

**Tool: read**

---

## Assistant

**Tool: read**

---

## Assistant

Update `LiveTabBody`:

**Tool: edit**

---

## Assistant

Now `MoviesTabBody`:

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

Now `SeriesTabBody`:

**Tool: read**

---

## Assistant

**Tool: edit**

---

## Assistant

**Tool: todowrite**

---

## Assistant

Edições aplicadas. Resumo:

## Arquivos editados

| Arquivo | Mudança |
|---|---|
| `data/PlaylistContentStore.kt` | Early-return no `loadPlaylistSuspending` quando `username.isBlank() \|\| password.isBlank()`. Reseta `_isLoading=false` e sai sem chamar a rede. |
| `ui/dashboard/PlaylistDashboardScreen.kt` | (1) Novo derived state `hasCredentials`. (2) `when` body: novo branch `!hasCredentials -> DashboardPager(...)` que pula `ErrorState`/`LoadingState`. (3) Extraído `DashboardPager` private composable para evitar duplicar ~60 linhas. (4) Os 3 `*TabBody` agora aceitam `hasCredentials: Boolean` e mostram `empty_configure_credentials_prompt` quando categorias vazias + sem creds. |
| `res/values/strings.xml` + `values-pt-rBR/strings.xml` + `values-tr/strings.xml` | Nova string `empty_configure_credentials_prompt` em 3 idiomas. |

## Comandos para você rodar

### Build
```bash
cd /workspaces/ehtudo-player/apps/android
env -i HOME="$HOME" PATH="/usr/local/sdkman/candidates/java/21.0.10-ms/bin:$PATH" JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.10-ms" ./gradlew :app:assembleRelease --no-daemon
```

### Zipalign + assinar
```bash
SDK=/home/codespace/android-sdk
cd /workspaces/ehtudo-player

$SDK/build-tools/36.0.0/zipalign -v -p 4 \
  apps/android/app/build/outputs/apk/release/app-release-unsigned.apk \
  apps/android/app/build/outputs/apk/release/app-release-aligned.apk

$SDK/build-tools/36.0.0/apksigner sign \
  --ks /home/codespace/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out apps/android/app/build/outputs/apk/release/app-release-signed.apk \
  apps/android/app/build/outputs/apk/release/app-release-aligned.apk
```

### Servidor HTTP
```bash
pkill -f "http.server 8000" 2>/dev/null || true
setsid python3 -m http.server 8000 --bind 0.0.0.0 \
  --directory /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/ \
  < /dev/null > /tmp/httpd.log 2>&1 & disown

curl -sI http://127.0.0.1:8000/app-release-signed.apk
```

### URL
```
https://super-duper-enigma-x9qpvpxq755cgv-8000.app.github.dev/app-release-signed.apk
```

## Comportamento esperado após a correção

- **Fresh install** (sem user/pass): app abre direto na aba Config, sem spinner de loading, sem tela de erro. Tabs Live/Filmes/Séries mostram "Configure seu usuário e senha na aba Configurações para começar a assistir." Usuário preenche, toca Salvar → verify + re-sync → tabs populam.
- **Atualização** (DB já tem user/pass válidos): mesmo comportamento de antes — dashboard carrega catálogo, `initialPage=3` continua abrindo na Config.
- **Atualização** (DB tem playlist com user/pass vazios, ex: alguém limpou o app parcialmente): tratado como fresh install — abre na Config.

---

