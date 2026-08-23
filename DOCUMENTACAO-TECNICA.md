# Documentação Técnica — I See Life (Android)

Documento gerado ao fim da implementação do MVP, conforme a seção 19 do `spec.md`. Cobre os 6
itens exigidos: modelo de dados, relacionamento entre entidades, regras que vivem no banco, mapa
tela → dados, lógica das principais funcionalidades por tela e divergências.

App: câmera de fotografia de rua Android, local-first, 100% offline. **Port nativo do app iOS
I See Life** (paridade de comportamento; bases independentes). Kotlin + Jetpack Compose + CameraX +
Room + Google Play Billing + OpenGL ES 2.0 (filtros). `minSdk 26`, `targetSdk 35`, phone/retrato.
Application ID `com.dablab.iseelife`.

> Fonte de verdade de comportamento: o `spec.md` deste repositório + o app iOS
> (`~/I See Life  app`, spec + doc técnica). Onde a plataforma Android obrigou a divergir, está
> registrado no item 6.

---

## 1. Modelo de dados (explicado)

Duas entidades **Room** independentes, sem relação (sem FK). Nomes de tabela/coluna em português
`snake_case` (via `@ColumnInfo`); as classes/DAO Kotlin em inglês (fronteira de idioma em
`dados/`). Banco SQLite local `iseelife.db` (`AppDatabase`, version 1).

### `configuracao_usuario` — `Entidades.kt`

Linha única (singleton, `id = 1`), criada no 1º uso; atualizada in-place; nunca excluída no MVP.

| Coluna (banco) | Propriedade (Kotlin) | Tipo | Significado |
|---|---|---|---|
| `id` | `id` | Int (pk) | Sempre 1 (singleton). |
| `idioma_preferido` | `language` | `Idioma` (pt/en) | Idioma preferido. Default `pt`. A troca real usa `SharedPreferences` (item 5). |
| `modo_padrao` | `defaultMode` | `ModoCaptura` (rua/pro) | Modo ao abrir o visor. Default `rua`. |
| `ultima_lente_usada` | `lastLens` | `LenteCamera?` | Última lente; nulo até a 1ª captura. |
| `grid_ativado` | `gridEnabled` | Bool | Grade de terços. Default `true`. |
| `nivel_ativado` | `levelEnabled` | Bool | Indicador de nível. Default `true`. |
| `obturador_silencioso` | `silentShutter` | Bool | Captura sem som. Default `true`. |
| `preview_pb_ativado` | `previewBwEnabled` | Bool | Preview P&B no Rua (só preview). Default `false`. |
| `compra_desbloqueada` | `purchaseUnlocked` | Bool | `true` após a compra confirmada (regra 11.4). Default `false`. |
| `data_compra` | `purchaseDate` | Long? (epoch ms) | Preenchida na confirmação; nulo antes. |
| `onboarding_concluido` | `onboardingDone` | Bool | Controla a reaparição do onboarding. Default `false`. |
| `receita_ativa_id` | `activeRecipeId` | String? (uuid) | Última receita escolhida; nulo = usa `ordem_exibicao = 1`. |
| `rua_obturador_denominador` | `streetShutterDenominator` | Int | Piso do obturador no Rua (250/500/1000). Default 250. |

### `receita` — `Entidades.kt`

Catálogo fixo de 6 receitas, populado/sincronizado a cada abertura, somente-leitura no MVP. Cada
campo alimenta o shader GLSL (item 5).

| Coluna | Propriedade | Tipo | Significado |
|---|---|---|---|
| `id` | `id` | String uuid (pk) | Identificador estável (referenciado por valor em `receita_ativa_id`). |
| `nome_exibicao` | `displayName` | String (unique) | Rótulo e identidade estável ("Classic"…). |
| `inspiracao` | `inspiration` | String | Referência interna (não exibida como marca). |
| `highlight` | `highlight` | Int (meio-passo ×2) | Curva de tom nas altas luzes. **Guardado ×2.** |
| `shadow` | `shadow` | Int (meio-passo ×2) | Curva de tom nas sombras. **Guardado ×2.** |
| `saturacao` | `saturation` | Int (meio-passo ×2) | Saturação (ignorada em mono). **Guardado ×2.** |
| `temperatura_wb_kelvin` | `whiteBalanceKelvin` | Int? | Kelvin assumido da cena; nulo = segue o sensor. |
| `tinta_wb` | `tint` | Int (-9…+9) | Eixo verde/magenta (NÃO vermelho/azul). |
| `intensidade_grao` | `grainIntensity` | `IntensidadeGrao` | Força do grão (nenhum/fraco/medio/forte). |
| `nitidez` | `sharpness` | Int (-4…+4) | + sharpen / − desfoque leve. |
| `monocromatico` | `monochrome` | Bool | Remove toda a cor (preview e arquivo). |
| `filtro_mono` | `monoFilter` | `FiltroMono?` | Filtro colorido simulado; só com mono. |
| `ordem_exibicao` | `displayOrder` | Int | Ordem fixa no seletor; ordem 1 = padrão. |
| `ajuste_vermelho` | `redAdjust` | Int | WB shift eixo vermelho (ganho canal R). |
| `ajuste_azul` | `blueAdjust` | Int | WB shift eixo azul (ganho canal B). |
| `efeito_color_chrome` | `colorChrome` | `EfeitoColorChrome` | Compressão de saturação (eixo quente). |
| `efeito_color_chrome_azul` | `colorChromeBlue` | `EfeitoColorChrome` | Idem, eixo azul. |
| `clareza` | `clarity` | Int (-4…+4) | Contraste local (≠ nitidez). |
| `tamanho_grao` | `grainSize` | `TamanhoGrao` | Tamanho do grão (pequeno/grande). |
| `criada_pelo_usuario` | `createdByUser` | Bool | false no catálogo fixo; prepara custom (roadmap). |

> **Meio-passo ×2 (crítico):** `highlight`/`shadow`/`saturacao` guardam o valor **dobrado** (ex.:
> real -1,5 → -3). `RecipeUniforms.de()` divide por 2 ao montar os uniforms do shader. Mantém
> paridade de cor com o iOS e evita migração de tipo. **NÃO** ler esses campos como valor real.

**Enums** (`Enums.kt`, valores idênticos ao iOS; guardados como string `valor` via `Conversores`):
`idioma` (pt/en), `modo_captura` (rua/pro), `lente_camera` (wide/ultra_wide), `intensidade_grao`
(nenhum/fraco/medio/forte), `filtro_mono` (nenhum/amarelo/vermelho/verde), `efeito_color_chrome`
(desligado/fraco/forte), `tamanho_grao` (pequeno/grande).

**Valores de seed** (valor REAL; no banco `hl/sh/sat` ficam ×2) — `RecipeSeed.receitasPadrao()`:

| Ordem | Receita | hl | sh | sat | temp WB | tinta | R/B | grão | nitidez | clareza | chrome | mono |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Neutra | 0 | 0 | 0 | nulo | 0 | 0/0 | nenhum | 0 | 0 | – | não |
| 2 | Classic | −1 | +1 | +2 | 7000K | 0 | 0/0 | fraco | +2 | 0 | – | não |
| 3 | Soft | −2 | 0 | −2 | 5600K | +1 | 0/0 | nenhum | −1 | 0 | – | não |
| 4 | Mono | −1 | +4 | 0 | nulo | −1 | 0/0 | forte | −2 | 0 | – | sim/vermelho |
| 5 | Positivo | −3 | +3 | +3 | 5000K | +3 | 0/0 | nenhum | 0 | 0 | – | não |
| 6 | Vívida | 0 | +0,5 | +2 | 5500K | 0 | +2/−5 | fraco | +1 | +3 | forte | não |

**Migração:** aditiva. Como esta é a 1ª release, o schema já nasce completo. Regra permanente:
coluna nova entra com default; **nunca** trocar o tipo de uma coluna existente.

---

## 2. Relacionamento entre as entidades

**Não há relação** entre `configuracao_usuario` e `receita` (sem FK, sem `@Relation`). A ligação
"qual receita está ativa" é feita **por valor**: `configuracao_usuario.receita_ativa_id` guarda o
`id` da receita escolhida. É idêntico ao modelo do iOS (SwiftData sem `@Relationship`).

```
configuracao_usuario (1 linha)      receita (catálogo fixo, 6 linhas)
   receita_ativa_id  ......(valor, não FK)......>  receita.id
```

Também **não há lógica no banco**: sem triggers, sem funções, sem RLS (item 3).

---

## 3. Regras de negócio que vivem no banco

**Nenhuma.** O Room é só armazenamento local; toda a lógica vive no código Kotlin. Onde cada
regra do spec foi implementada:

- **11.1 (detecção de lentes):** `camera/LensDiscovery.kt` — enumera as traseiras via CameraX +
  `Camera2CameraInfo`, distingue wide/ultra pela menor distância focal. `ViewfinderScreen` só mostra
  o botão de lente quando há > 1.
- **11.2 (parâmetros das receitas):** seed em `RecipeSeed.receitasPadrao()`; aplicação no shader
  (item 5 / `filtros/`).
- **11.3 (pipeline de captura):** o MESMO shader do preview é aplicado no frame de captura
  (`CameraGlRenderer.onDrawFrame` → FBO → Bitmap → JPEG). Sem cópia sem filtro.
- **11.4 (paywall):** gate no 1º disparo em `ViewfinderScreen.disparar`; posse em `BillingManager`
  + persistência em `configuracao_usuario`.
- **11.5 (sincronização do catálogo):** `RecipeSeed.sincronizarCatalogo` — roda a cada abertura
  (via `Repositorio.prepararNaAbertura`, chamado no `MainActivity`): upsert por `nome_exibicao`
  (preserva `id`) + **deleta** receitas fixas fora do catálogo, respeitando `criada_pelo_usuario`.
- **11.6 (matemática do shader):** `filtros/ShaderSources.kt` + `RecipeUniforms.kt`.
- **11.7 (prioridade de obturador):** `camera/ExposureController.kt` + `LumaAnalyzer.kt`.

---

## 4. Mapa tela → entidades

| Tela | Arquivo | Lê | Escreve |
|---|---|---|---|
| Roteamento | `telas/RootView.kt` | `configuracao_usuario` | — |
| Onboarding | `telas/onboarding/OnboardingScreen.kt` | permissão do sistema | `onboarding_concluido` |
| Permissão negada | `telas/permissoes/PermissionDeniedScreen.kt` | — | — |
| Visor (Rua/Pro) | `telas/viewfinder/ViewfinderScreen.kt` | `configuracao_usuario`, `receita` | `receita_ativa_id`, `compra_desbloqueada`, `data_compra` |
| Seletor de receita | `telas/viewfinder/RecipeSelectorSheet.kt` | `receita` | `receita_ativa_id` (via callback) |
| Modo Pro (painel) | `telas/viewfinder/ProControlsPanel.kt` | faixas da câmera | — (aplica na câmera, não no banco) |
| Paywall | `telas/paywall/PaywallScreen.kt` | produto Play Billing | `compra_desbloqueada`, `data_compra` (via VM) |
| Configurações | `telas/configuracoes/SettingsScreen.kt` | `configuracao_usuario` | grid, nível, silencioso, preview P&B, idioma, `rua_obturador_denominador`, compra |

---

## 5. Lógica das principais funcionalidades por tela

### Roteamento — `RootView.kt`
Observa `configuracao_usuario`. Roteia: `!onboarding_concluido` → Onboarding; senão, permissão
concedida → Visor; negada → Permissão negada. Rechecagem em `ON_RESUME` (retorno das configurações
do sistema). `MainActivity` chama `Repositorio.prepararNaAbertura` (cria o singleton + sincroniza o
catálogo — regra 11.5).

### Preview ao vivo (WYSIWYG) — `camera/` + `filtros/`
CameraX vincula **Preview** + **ImageAnalysis** (`CameraController.ligar`). O Preview escreve numa
`SurfaceTexture` (textura externa OES); `CameraGlRenderer` (numa `GLSurfaceView`) desenha os frames
pelo **RecipeGlPipeline** (dois passes, item abaixo) em aspect-fill sobre fundo preto. O preview P&B
do Rua força desaturação só no preview (uniform `forceMono`).

### Pipeline de filtro (GLSL, dois passes) — `filtros/`
Tradução do `RecipePipeline.swift` (Core Image) do iOS para OpenGL ES 2.0 (regra 11.6):
- `RecipeUniforms.de(receita, forceMono)` converte a `Receita` em uniforms, **desfazendo o ×2**.
- **Passe A (cor):** WB (temp/tinta) → shift R/B → curva de tom → saturação/mono+filtro → color
  chrome. Amostra a textura de entrada (externa da câmera OU 2D do bitmap) e grava num FBO.
- **Passe B (espacial + grão):** clareza → nitidez → grão, amostrando o FBO do passe A (sempre 2D).
- O MESMO pipeline roda no preview (`CameraGlRenderer`) e na captura (`OffscreenRecipeRenderer`).

### Toque para travar foco (Modo Rua) — `CameraController.focarNoPonto`
Toque no visor → `startFocusAndMetering` no ponto (sob prioridade de obturador, só AF, não AE) +
`IndicadorFoco` âmbar que some em ~1s.

### Prioridade de obturador (Modo Rua) — `ExposureController` + `LumaAnalyzer`
Obturador FIXO (piso `rua_obturador_denominador`) + ISO AUTOMÁTICO: `LumaAnalyzer` mede a luminância
média do frame (ImageAnalysis, ~8 Hz) e o `ExposureController` ajusta `SENSOR_SENSITIVITY` num loop
amortecido (AE off, `SENSOR_EXPOSURE_TIME` fixo, via `Camera2CameraControl`). Ao (re)aplicar,
também restaura AF contínuo + AWB auto (entrar no Rua zera ajustes manuais do Pro).

### Modo Pro — `ManualControls` + `ProControlsPanel`
Sliders (Foco, ISO, Obturador, EV, WB temp/tinta) escrevem via `Camera2CameraControl` (manual pleno,
AE/AWB/AF off). Botão **Auto** = `restaurarAutomatico` (volta contínuo + zera EV). Sem abertura
(hardware). Os `Slider` do Material arrastam na horizontal e não conflitam com o scroll vertical.

### Captura + salvamento — `CameraGlRenderer` + `PhotoSaver`
Disparo no **touch-down** do botão (anti-tremor) OU pelos botões de volume (item abaixo). O renderer
captura o próximo frame com a MESMA matriz do preview (orientação + aspect-fill) → FBO no tamanho da
view → `Bitmap` → JPEG → `PhotoSaver` (MediaStore, `Pictures/I See Life`; fallback diretório público
no Android 9). Feedback: vibração + clarão branco. Sem cópia sem filtro (regra 11.3).

### Paywall + compra — `BillingManager` + `PaywallScreen`
Todas as telas navegáveis sem compra. Gate no 1º disparo (`ViewfinderScreen.disparar`): se não
desbloqueado, a foto fica **pendente** em memória e o paywall aparece (não salva). `BillingManager`
(Play Billing 7): produto não-consumível `iseelife_desbloqueio_completo`, preço da Play
(`formattedPrice`), acknowledge, posse via `queryPurchasesAsync` (offline após a 1ª confirmação),
restaurar. Ao confirmar, persiste `compra_desbloqueada`/`data_compra` e salva a pendente sem novo
toque. `desbloqueado = compra_desbloqueada OU billing.possui`.

### Integrações de plataforma — `MainActivity` + `plataforma/`
- **Botões de volume:** `MainActivity.dispatchKeyEvent` intercepta VOLUME_UP/DOWN e chama a ação de
  disparo compartilhada (`App.disparador`, registrada pelo visor); consome o evento com o visor
  ativo. No onboarding/config o volume age normal.
- **App Shortcut** estático "Disparar" (`res/xml/shortcuts.xml`).
- **Quick Settings Tile** (`plataforma/DispararTileService`): abre o app no visor (equivalente
  confiável ao Control do iOS 18).

### Troca de idioma em runtime — `plataforma/Idiomas` + `MainActivity.attachBaseContext`
A escolha vai para `SharedPreferences` (leitura síncrona) e para `configuracao_usuario.idioma_preferido`.
`attachBaseContext` envolve o contexto com o locale; a troca aplica com `recreate()` da Activity.
Strings em `res/values` (pt-BR) e `res/values-en`.

---

## 6. Divergências em relação ao spec e ao comportamento iOS

Registradas porque a plataforma Android obrigou, ou porque o Core Image não tem equivalente exato
em GLSL ES 2.0. **Alvo de fidelidade: equivalência VISUAL validada no aparelho, não igualdade de
bits** (o próprio color chrome já era aproximação no iOS).

1. **Pipeline em DOIS passes (não passe único).** Estágios que amostram pixels vizinhos
   (clareza/nitidez) precisam ler a cor JÁ processada; num passe único, `texture2D` sempre lê a
   entrada crua e reintroduz cor após a conversão P&B. Corrigido com passe A (cor) → FBO → passe B
   (espacial+grão). Bug encontrado ao rodar no emulador.

2. **White balance por Kelvin é APROXIMAÇÃO por ganho de canal.** O `temperatureAndTint` do Core
   Image faz adaptação cromática; aqui é ganho perceptual (quente = R↑/B↓; tinta = G). A separação
   Classic/Positivo ficou suave — **calibrar no aparelho** (`RecipeUniforms.de`). Shift R/B, curva de
   tom, saturação, color chrome e grão são traduções exatas.

3. **EV do Modo Pro é trim em STOPS sobre o ISO.** O Camera2 não combina EV-compensation com sensor
   manual; com AE off, o EV é aplicado como `ISO × 2^EV`. Divergência de mecanismo (mesma intenção:
   escurecer/clarear). WB manual usa `COLOR_CORRECTION_GAINS` rggb aproximados.

4. **Captura no enquadramento do preview (retrato), não no frame cru do sensor.** Para garantir
   WYSIWYG e orientação correta, a captura usa a mesma matriz do preview (aspect-fill + rotação) no
   tamanho da view. O teto de detalhe é a resolução do frame do Preview (~1280×960); captura em
   resolução maior é otimização de roadmap. (No iOS a captura silenciosa também era frame-grab.)
   *Bug corrigido:* a 1ª versão salvava o frame do sensor (paisagem) → foto girada 90°.

5. **Captura silenciosa por frame-grab (padrão).** O caminho nítido com `ImageCapture` quando
   `obturador_silencioso = false` ainda NÃO foi implementado (refinamento pós-MVP); hoje a captura é
   sempre frame-grab do preview (silenciosa). Paridade parcial com o iOS.

6. **Zebra/Peaking não portados.** Estavam atrás de flag no iOS (a calibrar); ficaram fora do MVP
   Android (roadmap).

7. **Comportamento de câmera depende de MANUAL_SENSOR.** Prioridade de obturador e controles
   manuais do Pro checam `REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR`; sem suporte, caem no
   automático do CameraX (degradação graciosa — fragmentação de câmera Android).

8. **Troca de idioma via `recreate()`** (não swap de bundle como no iOS). Resultado equivalente
   (UI troca na hora). Sem AppCompat (contexto envolvido no `attachBaseContext`).

### Itens que dependem de device / loja (não verificáveis no emulador)
- Validação visual das 6 receitas lado a lado com o iOS e ajuste fino do WB.
- Comportamento de exposição/foco manuais e da prioridade de obturador (o emulador não expõe sensor
  manual real).
- Compra real (Google Play Services): produto `iseelife_desbloqueio_completo` ativo no Play Console
  + testador de licença. `simularCompra()` (só debug) valida o fluxo do gate sem a Play.
- Ícone: derivado do ícone do iOS (adaptativo). Ícone 512×512 da loja em `play-assets/`.

---

## Estrutura do repositório (resumo)

```
app/src/main/java/com/dablab/iseelife/
  App.kt, MainActivity.kt
  dados/        (Room: Entidades, Enums, Conversores, Daos, AppDatabase, RecipeSeed, Repositorio)
  filtros/      (ShaderSources, RecipeUniforms, RecipeGlPipeline, GlUtil, OffscreenRecipeRenderer)
  camera/       (LensDiscovery, CameraGlRenderer, GlPreviewView, CameraController, LumaAnalyzer,
                 ExposureController, ManualControls, PhotoSaver)
  compras/      (BillingManager)
  telas/        (RootView; onboarding/, permissoes/, viewfinder/, configuracoes/, paywall/)
  designsystem/ (Estilo)
  plataforma/   (DispararTileService, Idiomas)
  res/values, res/values-en, res/drawable*, res/mipmap-anydpi-v26, res/xml/shortcuts.xml
```

Verificação: `assembleDebug` + testes unitários (10) + instrumentados (3, GPU) verdes; fluxos
principais verificados no emulador (AVD `iseelife_test`). Comandos em `CLAUDE.md`.
