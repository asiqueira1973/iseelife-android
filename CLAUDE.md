# CLAUDE.md — I See Life (Android)

Guia para o agente de código. Leia antes de mexer. Este é o **port Android** do app iOS
`I See Life` (câmera de fotografia de rua). O `spec.md` é a fonte de verdade; a paridade de
comportamento é com o app iOS.

## O que é

Câmera de fotografia de rua, **local-first, 100% offline**. Aplica uma "receita" de cor na
captura; o preview já mostra o resultado final (WYSIWYG). Kotlin + Jetpack Compose + CameraX +
Room + Google Play Billing. Filtros em **OpenGL ES 2.0 (shader GLSL)**. iPhone-only no iOS →
**phone-only, retrato apenas**. `minSdk 26`, `targetSdk 35`.

## Regra de ouro

**Não alterar o projeto iOS.** Fica em `~/I See Life  app`, é uma base separada, publicada e
estável. Bases independentes; a paridade é de comportamento, não de código.

## Comandos

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21           # JDK 21 (AGP não aceita o JDK 25 do sistema)
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
./gradlew :app:assembleDebug        # compila o APK debug
./gradlew test                      # testes unitários (regras numéricas, sincronizarCatalogo)
./gradlew lint                      # lint Android
```
O SDK/JDK foram instalados via Homebrew; `local.properties` aponta o `sdk.dir` (não versionado).
Build e teste visual de câmera exigem device/emulador (Android Studio). O agente compila pela CLI,
mas **não roda câmera** aqui.

## Arquitetura (pastas em `app/src/main/java/com/dablab/iseelife/`)

- `dados/` — Room: entidades, DAOs, `AppDatabase`, `RecipeSeed` (catálogo + `sincronizarCatalogo`),
  `Repositorio` (bootstrap na abertura). **Feito (épico 8.1).**
- `filtros/` — GL: `ShaderSources` (GLSL ES 2.0), `RecipeUniforms` (Receita→uniforms, desfaz o ×2),
  `RecipeProgram` (compila/liga, envia uniforms, desenha), `GlUtil`, `OffscreenRecipeRenderer`
  (aplica a receita a um Bitmap via EGL PBuffer+FBO = caminho da captura). **Feito (épico 8.2).**
  Falta: zebra/peaking (roadmap, atrás de flag). WB é aproximação por ganho de canal — calibrar no device.
- `camera/` — `LensDiscovery`, `CameraGlRenderer` (OES→pipeline), `GlPreviewView`, `CameraController`
  (CameraX↔SurfaceTexture + foco por toque + prioridade de obturador + controles manuais),
  `LumaAnalyzer`, `ExposureController` (obturador fixo + auto-ISO), `ManualControls` (ISO/obturador/
  EV/foco/WB, Camera2Interop), `PhotoSaver` (JPEG→MediaStore). **Preview (8.3) + Rua (8.4) + Pro
  (8.5) + captura (8.6) feitos.** Falta: botão de volume (8.10).
- `telas/viewfinder/` — `ViewfinderScreen` (visor + disparo), `ProControlsPanel` (sliders do Pro).
- `telas/viewfinder/` — `ViewfinderScreen` (visor Compose: permissão, troca de receita, lente).
- `designsystem/Estilo` — cores (accent âmbar).
- `compras/` — Play Billing. **(épico 8.7)**
- `telas/` — onboarding, visor (rua/pro), seletor, paywall, configurações, permissão. **(8.8–8.9)**
- `plataforma/` — App Shortcut, Quick Settings Tile, teclas de volume. **(épico 8.10)**
- `localizacao/`, `designsystem/`.

## Pegadinhas que NÃO podem ser quebradas

1. **Meio-passo ×2 em `highlight`/`shadow`/`saturacao`.** No banco (`receita`) esses campos guardam
   o valor **dobrado** (ex.: real -1,5 → -3). A camada de uniforms do shader **divide por 2**.
   Mexer nesses números sem saber disso dobra/divide o efeito. Ver `RecipeSeed.kt` e regra 11.2/11.6.
2. **`RecipeSeed.sincronizarCatalogo` roda a cada abertura.** Faz upsert por `nome_exibicao`
   (preservando o `id`, pra `receita_ativa_id` continuar válido) e **DELETA** receitas fixas fora
   do catálogo (respeitando `criada_pelo_usuario`). Não dá pra adicionar/editar receita só no
   banco — tem que ser no código. Ver regra 11.5.
3. **Mesmo shader no preview E na captura (WYSIWYG, princípio 0.2).** O que se vê é o que se salva.
   Não criar um caminho de captura com filtro diferente do preview.
4. **Migração Room ADITIVA.** Campo novo entra com default; **nunca** trocar o tipo de um campo
   existente (quebra update). Foi por isso que o ×2 existe (evita Int→Double).
5. **Matemática do shader = tradução 1:1 do `RecipePipeline.swift` do iOS** (regra 11.6). Ajuste de
   cor é por comparação visual no device; registrar divergência na doc técnica.
6. **Local-first.** Sem permissão de rede, sem analytics, sem backend. Só a compra fala com a Play.

## Convenções

- **Banco em português `snake_case`** (colunas via `@ColumnInfo`); **código Kotlin em inglês**. A
  fronteira de idioma fica nas entidades `dados/`.
- **Comentário de cabeçalho em português** em toda classe/função/etapa de shader, dizendo o que faz
  e qual regra do spec implementa (com número).
- Manter `CLAUDE.md`, `DOCUMENTACAO-TECNICA.md` (ao fim) e `README.md` atualizados **no mesmo
  commit** da mudança. Para mudanças incrementais, seguir o protocolo da seção 29 do `spec.md`.

## Estado atual

Feitos: **8.0** (esqueleto), **8.1** (Room + seed/sync), **8.2** (pipeline GLSL 2 passes) e **8.3**
(câmera CameraX + preview filtrado ao vivo — WYSIWYG). Verificado no emulador (câmera virtualscene):
preview ao vivo filtrado, troca de receita em tempo real, sem crash. 10 testes unitários + 3
instrumentados verdes. Cores validadas pelo Alexandre (contact sheet). **8.4 (Modo Rua) e 8.5 (Modo
Pro) e 8.6 (captura + salvar na galeria) feitos** — captura verificada no emulador (foto retrato,
orientação e filtro certos, salva no MediaStore). O app já TIRA E SALVA FOTO. Comportamento de
câmera (exposição/foco manuais) valida no device. Próximo: **8.7 (Play Billing — paywall no 1º
disparo)**, depois 8.8 onboarding/permissões, 8.9 configurações + seletor de receitas, 8.10
integrações (volume/App Shortcut/Quick Tile), 8.11 i18n.

**Rodar no emulador (sem Android Studio):**
```bash
export ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
emulator -avd iseelife_test -no-window -gpu swiftshader_indirect -camera-back virtualscene &
adb wait-for-device && ./gradlew installDebug
adb shell pm grant com.dablab.iseelife.debug android.permission.CAMERA
adb shell am start -n com.dablab.iseelife.debug/com.dablab.iseelife.MainActivity
```
O AVD "iseelife_test" precisa de `hw.camera.back=virtualscene` (ou emulated) no config.ini.
