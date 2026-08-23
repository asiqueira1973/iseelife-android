# I See Life — Android

Port Android nativo do app iOS **I See Life** — câmera de fotografia de rua, local-first e
offline, que aplica uma "receita" de cor (simulação de filme) na captura, com preview WYSIWYG.

- **Stack:** Kotlin · Jetpack Compose · CameraX · Room · Google Play Billing · OpenGL ES 2.0 (filtros).
- **Alvo:** phone, retrato apenas. `minSdk 26`, `targetSdk 35`. `applicationId com.dablab.iseelife`.
- **Fonte de verdade:** [`spec.md`](spec.md) (+ `modelo-i-see-life-android.dbml`). Paridade de
  comportamento com o app iOS (`~/I See Life  app`), que **não** é tocado por este projeto.

## Setup

Requer **Android Studio** (SDK + emulador) e **JDK 17/21**. Nesta máquina foram instalados via
Homebrew:

```bash
brew install openjdk@21
brew install --cask android-commandlinetools android-studio
# SDK: platform-tools, platforms;android-35, build-tools;35.0.0 (licenças aceitas)
```

`local.properties` aponta o SDK (não versionado):
```
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

## Build

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew :app:assembleDebug     # gera app/build/outputs/apk/debug/app-debug.apk
```

Ou abra a pasta no **Android Studio** (ele resolve o SDK/JDK e oferece rodar no emulador/aparelho).
No 1º sync, aceite eventuais upgrades de AGP/Kotlin/Compose que a IDE sugerir.

## Estado

Em construção, por épicos (ver `spec.md` seção 8). Feitos: **8.0** (esqueleto) e **8.1** (modelo
Room + seed/sincronização das 6 receitas). Próximo: **8.2** (pipeline de filtro GLSL).

Convenções e pegadinhas de manutenção: ver [`CLAUDE.md`](CLAUDE.md).
