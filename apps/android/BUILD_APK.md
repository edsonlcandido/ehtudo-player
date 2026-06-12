# Deploy do APK — Another IPTV Player (Android nativo)

Guia completo para gerar o APK do app Android nativo que vive em
`apps/android/` (Kotlin + Jetpack Compose + JNI/libmpv). O app Flutter
legado em `apps/flutter/` está deprecated e tem um processo separado.

---

## Visão geral

| Item | Valor |
| --- | --- |
| `applicationId` | `dev.android.anotheriptvplayer` |
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
adb shell am start -n dev.android.anotheriptvplayer/.MainActivity
```

Logs do app:

```bash
adb logcat --pid="$(adb shell pidof -s dev.android.anotheriptvplayer)"
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
