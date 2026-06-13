# Deploy do APK — Another IPTV Player (Android nativo)

Guia completo para gerar o APK do app Android nativo que vive em
`apps/android/` (Kotlin + Jetpack Compose + JNI/libmpv). O app Flutter
legado em `apps/flutter/` está deprecated e tem um processo separado.

---

## Visão geral

| Item | Valor |
| --- | --- |
| `applicationId` | `app.ehtudo.iptv` |
| `versionCode` / `versionName` | `1` / `1.0` (definidos em `apps/android/app/build.gradle.kts`) |
| `compileSdk` / `targetSdk` | `36` |
| `minSdk` | `26` (Android 8.0) |
| ABIs do APK | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Linguagem nativa | C++ via CMake 3.22.1 (linka contra `libmpv.so`) |
| Java target | 11 (mas o **JDK de build** precisa ser **17**) |
| Saída release | `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Saída debug | `app/build/outputs/apk/debug/app-debug.apk` |

> Aviso: o bloco `signingConfigs` não está configurado em
> `apps/android/app/build.gradle.kts`. O `assembleRelease` produz um APK
> **não-assinado**, que **não instala** em devices de produção sem
> `apksigner`. Veja a seção [Assinatura](#assinatura) para resolver isso.

---

## Pré-requisitos

Tudo abaixo é necessário no Linux/macOS (no Windows troque `./gradlew`
por `.\gradlew.bat`):

1. **JDK 17** (Temurin/Zulu/MS Open). O projeto usa AGP 8.12 + Gradle
   8.13 + KSP 2.0.21, que **não** suportam oficialmente JDK 25.
2. **Android SDK** com:
   - `platforms;android-36`
   - `build-tools;36.0.0`
   - `platform-tools`
   - `ndk;26.3.11579264` (o app tem código C++ em
     `app/src/main/cpp/CMakeLists.txt`)
   - `cmake;3.22.1`
3. **Ferramentas de sistema**: `curl`, `unzip`, `shasum`/`sha256sum`,
   `make`. Todas usadas pelo bootstrap do `libmpv`.
4. ~3 GB livres em disco para SDK + NDK + Gradle cache.
5. Acesso de rede para `dl.google.com`, `services.gradle.org`,
   `repo.maven.apache.org`, `github.com` (download dos JARs do
   `libmpv-android`).

---

## 1. Instalar JDK 17

A forma mais simples no Codespace/Ubuntu é via SDKMAN, que já costuma
estar em `/usr/local/sdkman`:

```bash
source /usr/local/sdkman/bin/sdkman-init.sh
sdk install java 17.0.13-tem < /dev/null
sdk use java 17.0.13-tem
java -version    # deve mostrar 17
```

Em outras distros:

```bash
# Debian/Ubuntu
sudo apt-get update && sudo apt-get install -y openjdk-17-jdk

# macOS (Homebrew)
brew install --cask temurin@17
```

Garanta que `JAVA_HOME` aponta para o JDK 17 antes do build:

```bash
export JAVA_HOME="$(sdkman root)/candidates/java/17.0.13-tem"   # se SDKMAN
# ou
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64              # Debian/Ubuntu
```

---

## 2. Instalar Android SDK (cmdline-tools)

```bash
export ANDROID_HOME="$HOME/android-sdk"
mkdir -p "$ANDROID_HOME/cmdline-tools"
cd /tmp
curl -L -o cmdline-tools.zip \
  https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools"
mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Aceitar licenças e instalar componentes:

```bash
yes | sdkmanager --licenses >/dev/null
sdkmanager \
  "platforms;android-36" \
  "build-tools;36.0.0" \
  "platform-tools" \
  "ndk;26.3.11579264" \
  "cmake;3.22.1"
```

> Para tornar as variáveis persistentes, adicione ao `~/.bashrc` ou
> `~/.zshrc`:
> ```bash
> export ANDROID_HOME="$HOME/android-sdk"
> export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
> export JAVA_HOME="$(sdkman root)/candidates/java/17.0.13-tem"
> ```

---

## 3. Criar `local.properties` do Android

O `local.properties` não é commitado (`apps/android/.gitignore`) e diz
ao Gradle onde está o SDK. Crie em `apps/android/`:

```bash
cat > apps/android/local.properties <<EOF
sdk.dir=$ANDROID_HOME
ndk.dir=$ANDROID_HOME/ndk/26.3.11579264
EOF
```

Alternativa Linux:

```bash
echo "sdk.dir=$ANDROID_HOME" > apps/android/local.properties
echo "ndk.dir=$ANDROID_HOME/ndk/26.3.11579264" >> apps/android/local.properties
```

---

## 4. Popular `jniLibs/<abi>/libmpv.so`

O `app/build.gradle.kts` filtra ABIs para
`arm64-v8a`, `armeabi-v7a`, `x86_64`, e o `jniLibs/.gitignore` está
vazio por design. O Makefile em
`apps/android/Vendor/libmpv-android/` baixa os JARs pré-buildados do
`media-kit/libmpv-android-video-build` (pinado em `v1.1.11` com SHA256)
e extrai as `.so` para `app/src/main/jniLibs/<abi>/`.

```bash
cd apps/android/Vendor/libmpv-android
make
ls ../../app/src/main/jniLibs/*/libmpv.so
# esperado:
#   ../../app/src/main/jniLibs/arm64-v8a/libmpv.so
#   ../../app/src/main/jniLibs/armeabi-v7a/libmpv.so
#   ../../app/src/main/jniLibs/x86_64/libmpv.so
```

Para limpar (força re-download no próximo `make`):

```bash
cd apps/android/Vendor/libmpv-android
make clean
```

---

## 5. Buildar o APK

A partir de `apps/android/`:

```bash
cd apps/android
chmod +x ./gradlew
./gradlew :app:assembleRelease :app:assembleDebug --no-daemon --stacktrace
```

> Sem `--no-daemon` o build fica mais rápido em chamadas sucessivas
> (o daemon é reusado), mas em CI/Codespaces efêmeros o `--no-daemon`
> evita sobras. Remova se estiver buildando localmente e em
> desenvolvimento iterativo.

### Localizar os APKs gerados

```bash
ls -lh app/build/outputs/apk/release/
ls -lh app/build/outputs/apk/debug/
```

- **Release (não-assinado)**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **Debug (assinado com debug key)**: `app/build/outputs/apk/debug/app-debug.apk`

Validar conteúdo de um APK:

```bash
"$ANDROID_HOME"/build-tools/36.0.0/aapt dump badging \
  app/build/outputs/apk/release/app-release-unsigned.apk | head -20
```

---

## 6. Assinatura (opcional)

O `assembleRelease` padrão gera um APK **não-assinado**. Para instalar
em um device real, ou publicar, você precisa assinar com `apksigner`.

### 6.1 Gerar um keystore (apenas uma vez)

```bash
keytool -genkey -v \
  -keystore release.jks \
  -alias anotheriptvplayer \
  -keyalg RSA -keysize 2048 -validity 10000
```

> Guarde `release.jks` e as senhas em local seguro. **Perder o keystore
> significa não conseguir atualizar o app** na Play Store.

### 6.2 Adicionar `signingConfigs` em `app/build.gradle.kts`

Edite `apps/android/app/build.gradle.kts` e adicione dentro do bloco
`android { ... }`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("RELEASE_KEYSTORE") ?: "release.jks")
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "anotheriptvplayer"
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
    }
}

buildTypes {
    release {
        // ...
        signingConfig = signingConfigs.getByName("release")
    }
}
```

Recomendado: passar as senhas via **variáveis de ambiente**, nunca
commitar no repositório.

### 6.3 Assinar um APK já gerado (alternativa rápida)

```bash
"$ANDROID_HOME"/build-tools/36.0.0/apksigner sign \
  --ks release.jks \
  --ks-key-alias anotheriptvplayer \
  --out app-release.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```

### 6.4 Validar assinatura

```bash
"$ANDROID_HOME"/build-tools/36.0.0/apksigner verify --verbose app-release.apk
```

---

## 7. Instalar e testar

Com um device/emulador conectado (`adb devices` deve listar algo):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
# ou
adb install -r app-release.apk    # se você assinou
adb shell am start -n app.ehtudo.iptv/.MainActivity
```

Logs do app:

```bash
adb logcat --pid="$(adb shell pidof -s app.ehtudo.iptv)"
```

---

## 8. Pipeline automatizado (CI)

O workflow oficial do GitHub Actions
(`.github/workflows/build-android.yml`) ainda mira o app Flutter
legado. Para CI do nativo, copie esse arquivo e ajuste:

```yaml
name: Android · Build Native APK
on:
  push: { branches: [main], paths: ['apps/android/**', '.github/workflows/build-android-native.yml'] }
  pull_request: { branches: [main], paths: ['apps/android/**', '.github/workflows/build-android-native.yml'] }
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run: { working-directory: apps/android }
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: zulu, java-version: '17' }
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
        with:
          api-level: '36'
          build-tools-version: '36.0.0'
          ndk-version: '26.3.11579264'
          cmake-version: '3.22.1'
      - name: Bootstrap libmpv
        run: make -C Vendor/libmpv-android
      - name: Build release APK
        run: ./gradlew :app:assembleRelease --stacktrace
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: android-release-apk
          path: apps/android/app/build/outputs/apk/release/app-release-unsigned.apk
```

Para um CI que assine, adicione passos com `android-actions/setup-android`
(inclui `apksigner`) e exporte o keystore base64 como secret.

---

## 9. Solução de problemas

### `Unsupported class file major version 65/67`
JDK errado. Rode `java -version` e confirme **17.x**. AGP 8.12 não
suporta JDK 21+ estável; fuja do JDK 25.

### `SDK location not found`
`local.properties` ausente ou com `sdk.dir` errado. Refaça a
[Etapa 3](#3-criar-localproperties-do-android). Alternativamente,
exporte `ANDROID_HOME` e `ANDROID_SDK_ROOT`.

### `CMake '3.22.1' not found` / erro de toolchain NDK
Você pulou o `sdkmanager "cmake;3.22.1"` ou
`"ndk;26.3.11579264"`. Rode a [Etapa 2](#2-instalar-android-sdk-cmdline-tools)
completa. Em Linux, confirme que o binário do NDK está em
`$ANDROID_HOME/ndk/26.3.11579264/build/cmake/android.toolchain.cmake`.

### `UnsatisfiedLinkError: dlopen failed: library "libmpv.so" not found` em runtime
O `make` em `apps/android/Vendor/libmpv-android/` não rodou (ou rodou
parcial). Verifique:

```bash
ls apps/android/app/src/main/jniLibs/*/libmpv.so
```

Se estiver faltando alguma ABI, rode de novo:

```bash
cd apps/android/Vendor/libmpv-android
make clean && make
```

### `java.lang.OutOfMemoryError: Java heap space` durante o build
Aumente a heap do Gradle em `apps/android/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
```

### Build muito lento na primeira vez
Normal: Gradle baixa Compose BoM, Room, Coil, OkHttp, etc. (algumas
centenas de MB) e o NDK compila o `mpv_jni.cpp` para 3 ABIs em
paralelo. Builds subsequentes são ordens de magnitude mais rápidos
graças ao cache de `~/.gradle/caches` e
`apps/android/.cxx/`.

### `./gradlew: Permission denied`
```bash
chmod +x ./gradlew
```

### Quer mudar a `versionCode` / `versionName`?
Edite `apps/android/app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2
    versionName = "1.1"
}
```

---

## 10. Comandos rápidos (cola)

```bash
# Setup completo (Linux/macOS, SDKMAN + SDK em $HOME/android-sdk)
source /usr/local/sdkman/bin/sdkman-init.sh
sdk use java 17.0.13-tem
export ANDROID_HOME="$HOME/android-sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
sdkmanager --licenses >/dev/null
sdkmanager "platforms;android-36" "build-tools;36.0.0" "platform-tools" "ndk;26.3.11579264" "cmake;3.22.1"

# Configurar projeto
( cd apps/android && \
  echo "sdk.dir=$ANDROID_HOME" > local.properties && \
  echo "ndk.dir=$ANDROID_HOME/ndk/26.3.11579264" >> local.properties )

# Bootstrap libmpv
( cd apps/android/Vendor/libmpv-android && make )

# Build
( cd apps/android && ./gradlew :app:assembleRelease :app:assembleDebug )

# Localizar APKs
ls -lh apps/android/app/build/outputs/apk/{release,debug}/*.apk
```

---

## 11. Pipeline rápido do dev-container (Java 21 + debug keystore)

Esta seção descreve o fluxo usado em Codespaces / dev-containers Ubuntu
24.04 para buildar o APK do `app.ehtudo.iptv` com o **JDK 21 do
SDKMAN** (em vez do JDK 17) e assiná-lo com a **debug keystore**
(`~/.android/debug.keystore`, alias `androiddebugkey`, senha `android`),
servindo o resultado via `python3 -m http.server` na porta 8000.

> Esse atalho existe porque o app não tem `signingConfigs` configurado
> em `apps/android/app/build.gradle.kts`. O `assembleRelease` produz
> um APK **não-assinado**; para instalar em device real é obrigatório
> passar pelo `zipalign` + `apksigner` antes.

### 11.1 Build (Java 21, sem daemon)

```bash
cd /workspaces/ehtudo-player/apps/android
env -i HOME="$HOME" PATH="/usr/local/sdkman/candidates/java/21.0.10-ms/bin:$PATH" \
  JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.10-ms" \
  ./gradlew :app:assembleRelease --no-daemon
```

> O `env -i` isola variáveis herdadas do shell (em especial
> `ANDROID_HOME`/`ANDROID_SDK_ROOT` apontando para outro SDK) e força
> o Gradle a reler o `local.properties` do diretório atual. Sem isso o
> build pode falhar com `SDK location not found` apontando para um
> caminho antigo.

Saída esperada (alguns minutos na primeira vez, segundos em build
incremental):

```
apps/android/app/build/outputs/apk/release/app-release-unsigned.apk
```

### 11.2 Zipalign

O `app-release-unsigned.apk` precisa ser zipaligned antes de assinar
para que o `apksigner` aceite a entrada:

```bash
SDK=/home/codespace/android-sdk

$SDK/build-tools/36.0.0/zipalign -v -p 4 \
  /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-unsigned.apk \
  /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-aligned.apk
```

- `-p 4` alinha páginas nativas (`.so`) em 4 bytes.
- `-v` mostra cada arquivo processado.

### 11.3 Assinar com a debug keystore

```bash
SDK=/home/codespace/android-sdk

$SDK/build-tools/36.0.0/apksigner sign \
  --ks /home/codespace/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-signed.apk \
  /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-aligned.apk
```

> A debug keystore é gerada automaticamente pelo Android Studio / Gradle
> no primeiro build debug e fica em `~/.android/debug.keystore`. Senhas
> sempre `android`. Serve para **install sideload** (download direto),
> mas **não serve para Play Store** — para publicar, gere uma keystore
> dedicada e use a Seção [6](#6-assinatura-opcional).

### 11.4 Verificar assinatura

```bash
SDK=/home/codespace/android-sdk

$SDK/build-tools/36.0.0/apksigner verify --verbose \
  /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-signed.apk

sha256sum /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/app-release-signed.apk
```

Saída esperada:

```
Verifies
Verified using v1 scheme (JAR signing): true
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
```

E o `sha256sum` muda a cada build (conteúdo do APK muda), mas o
`apksigner verify` sempre deve terminar com `Verifies`.

### 11.5 Servir via HTTP (python3)

Para baixar o APK do Codespace/dev-container via URL pública, suba um
servidor HTTP simples servindo o diretório de saída:

```bash
pkill -f "http.server 8000" 2>/dev/null || true
setsid python3 -m http.server 8000 --bind 0.0.0.0 \
  --directory /workspaces/ehtudo-player/apps/android/app/build/outputs/apk/release/ \
  < /dev/null > /tmp/httpd.log 2>&1 & disown
```

Verificar que está respondendo:

```bash
curl -sI http://127.0.0.1:8000/app-release-signed.apk
# esperado: HTTP/1.0 200 OK, Content-Length: ~55M
```

A URL pública do Codespace (formato `https://<host>-<port>.app.github.dev/`)
é construída a partir do **hostname do codespace** + **porta pública**.
Exemplo:

```
https://super-duper-enigma-x9qpvpxq755cgv-8000.app.github.dev/app-release-signed.apk
```

> **Importante:** a porta 8000 precisa estar marcada como **Public** (e
> não **Private**) na aba **Ports** do VS Code, senão o proxy do
> GitHub dev-container bloqueia o acesso externo. Para conferir:
> aba **Ports** → porta `8000` → botão direito → **Change Port
> Visibility** → **Public**.

Logs do servidor:

```bash
tail -f /tmp/httpd.log
```

### 11.6 Build + sign + serve em um único script

Para iterar rapidamente, salve isto como `apps/android/release.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
ROOT=/workspaces/ehtudo-player
SDK=/home/codespace/android-sdk

cd "$ROOT/apps/android"
env -i HOME="$HOME" \
  PATH="/usr/local/sdkman/candidates/java/21.0.10-ms/bin:$PATH" \
  JAVA_HOME="/usr/local/sdkman/candidates/java/21.0.10-ms" \
  ./gradlew :app:assembleRelease --no-daemon

OUT="$ROOT/apps/android/app/build/outputs/apk/release"

$SDK/build-tools/36.0.0/zipalign -v -p 4 \
  "$OUT/app-release-unsigned.apk" "$OUT/app-release-aligned.apk"

$SDK/build-tools/36.0.0/apksigner sign \
  --ks /home/codespace/.android/debug.keystore \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android --key-pass pass:android \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --out "$OUT/app-release-signed.apk" \
  "$OUT/app-release-aligned.apk"

$SDK/build-tools/36.0.0/apksigner verify --verbose "$OUT/app-release-signed.apk"
sha256sum "$OUT/app-release-signed.apk"

pkill -f "http.server 8000" 2>/dev/null || true
setsid python3 -m http.server 8000 --bind 0.0.0.0 \
  --directory "$OUT" < /dev/null > /tmp/httpd.log 2>&1 & disown

echo
echo "APK pronto em: $OUT/app-release-signed.apk"
echo "URL:           https://<codespace-host>-8000.app.github.dev/app-release-signed.apk"
```

Uso:

```bash
chmod +x apps/android/release.sh
apps/android/release.sh
```

### 11.7 Strings/locales

Ao adicionar uma string nova (ex: a `empty_configure_credentials_prompt`
introduzida no fix de "fresh install"), ela precisa ser replicada nos
**três** arquivos de strings:

```bash
# en  → apps/android/app/src/main/res/values/strings.xml
# pt   → apps/android/app/src/main/res/values-pt-rBR/strings.xml
# tr   → apps/android/app/src/main/res/values-tr/strings.xml
```

Padrão da entrada (mantenha o `name` idêntico e traduza o conteúdo):

```xml
<string name="empty_configure_credentials_prompt">Configure your username and password in the Settings tab to start watching.</string>
```

```xml
<string name="empty_configure_credentials_prompt">Configure seu usuário e senha na aba Configurações para começar a assistir.</string>
```

```xml
<string name="empty_configure_credentials_prompt">İzlemeye başlamak için Ayarlar sekmesinden kullanıcı adınızı ve şifrenizi yapılandırın.</string>
```

Verificar depois do build (deve listar o nome novo em todos os
locales):

```bash
$SDK/build-tools/36.0.0/aapt2 dump resources \
  $ROOT/apps/android/app/build/outputs/apk/release/app-release-unsigned.apk \
  | grep -A1 empty_configure_credentials_prompt
```

### 11.8 Comportamento "fresh install"

Pós-fix de fresh install (commit da sessão `ses_142f`), o app deve:

| Cenário | O que acontece |
| --- | --- |
| **Fresh install** (sem user/pass) | App abre direto na aba **Config** (pager `initialPage=3`), sem spinner, sem tela de erro. Tabs Live/Filmes/Séries mostram `"Configure seu usuário e senha na aba Configurações para começar a assistir."` |
| **Atualização** (DB já tem user/pass válidos) | Dashboard carrega catálogo normalmente; `initialPage=3` continua abrindo na aba Config. |
| **Atualização** (DB com playlist de user/pass vazios) | Tratado como fresh install — abre na aba Config. |

A correção tem 3 partes, todas já aplicadas nos fontes:

1. `data/PlaylistContentStore.kt` — early-return em
   `loadPlaylistSuspending` quando `username.isBlank() || password.isBlank()`
   (evita chamada de rede com parâmetros faltando).
2. `ui/dashboard/PlaylistDashboardScreen.kt` — novo branch
   `!hasCredentials -> DashboardPager(...)` no `when` body, e o
   `DashboardPager` extraído em composable privado para evitar
   duplicar ~60 linhas.
3. `res/values{,-pt-rBR,-tr}/strings.xml` — string
   `empty_configure_credentials_prompt` em 3 idiomas.
