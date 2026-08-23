# Spec — I See Life para Android (port nativo do app iOS)

**Versão:** 1.0 (rascunho do port)
**Responsável de produto:** Alexandre Siqueira
**Status:** rascunho — aguardando revisão do Alexandre antes de gerar código
**Data:** 2026-08-23

## Como usar este documento

Este é o spec do **port Android** do app **I See Life**, já publicado na App Store (iOS). Ele
é a fonte única de verdade **da versão Android** e vai alimentar o Claude Code na implementação.

**Relação com o app iOS.** O comportamento de **produto** (princípios, receitas, regras de
captura, paywall, telas) é o mesmo do iOS e **não** é redefinido aqui: a fonte de verdade de
produto continua sendo o `spec.md` e a `DOCUMENTACAO-TECNICA.md` do projeto iOS
(`~/I See Life  app`). Este documento especifica o que **muda** por ser Android: a arquitetura,
a stack, o modelo de dados na tecnologia Android, o **pipeline de filtros reescrito como shader
GLSL**, a câmera em CameraX, a compra em Google Play Billing e as integrações de plataforma.

> ⚠️ **O projeto iOS é intocável.** É um repositório/pasta separado, estável e publicado. Nada
> neste port abre, edita ou depende do código iOS. As duas bases são independentes; a paridade é
> **de comportamento**, garantida por este spec, não por código compartilhado.

A **seção 24 (Assunções)** precisa ser revisada **antes** de gerar código — assunção não revisada
vira verdade no minuto em que o agente começa a implementar. A **seção 25 (Perguntas abertas)**
lista o que só o Alexandre decide.

---

## 0. Princípios não-negociáveis

Herdados 1:1 do iOS (regras de desempate para qualquer dúvida de UX/copy/trade-off):

- **0.1 Pronto para disparar.** Abrir o app cai direto no visor do Modo Rua, sem tela
  intermediária depois do onboarding. O caminho até a foto é o mais curto possível.
- **0.2 Preview é verdade (WYSIWYG).** O que se vê no visor com a receita ativa é exatamente o
  que sai no arquivo salvo. O **mesmo** pipeline roda no preview ao vivo e na captura final.
- **0.3 Local-first, 100% offline.** Nenhuma dependência de rede para o app funcionar. Sem
  backend, sem conta, sem telemetria de rede. A única troca com a internet é a compra (Play
  Billing) e o salvamento é na galeria local.
- **0.4 Fundo preto, cor só com significado.** Visor sempre preto; a cor de destaque (âmbar
  `#FFB000`) só marca foco travado, receita ativa e toggles ligados — nunca decorativa.
- **0.5 Paridade de comportamento com o iOS.** Diante de qualquer ambiguidade de
  implementação, o comportamento correto é o que o app iOS já faz (ver spec/doc técnica do iOS).
  Divergências só quando a plataforma Android obriga — e sempre registradas na seção 6 da doc
  técnica.

---

## 1. Visão e problema

App de câmera de fotografia de rua para **Android**, local-first e offline, que aplica uma
"receita" de cor (simulação de filme) **na captura** — o preview já mostra o resultado final.
Mesmo produto do app iOS homônimo, portado para o público Android que já pediu a versão.

**Anti-escopo (o que NÃO é):** não é editor de fotos (não edita imagens da galeria, não há
pós-processamento depois do disparo); não é rede social (não compartilha, não tem feed, não tem
conta); não é câmera "profissional" de RAW/vídeo. É uma câmera de disparo rápido com um punhado
de receitas curadas.

---

## 3. Escopo do MVP (primeira versão Android)

Meta: **paridade com o iOS v1.0 + v1.1** (decisão do Alexandre). O "fora" abaixo é o que o
próprio iOS deixou de fora ou tem como roadmap.

### Dentro
- Onboarding (boas-vindas + permissão de câmera).
- Visor com **Modo Rua** (toque para travar foco/exposição; prioridade de obturador) e **Modo
  Pro** (ISO, obturador, exposição/EV, foco, WB manuais).
- **6 receitas** (catálogo fixo): Neutra (raw, padrão), Classic, Soft, Mono, Positivo, Vívida —
  com todos os campos de fidelidade da v1.1.
- Pipeline de filtro **WYSIWYG** (preview = arquivo) em GLSL.
- Captura silenciosa (padrão) e salvamento na galeria (MediaStore).
- Disparo pelo botão da tela, pelos **botões de volume** e por **atalhos** de plataforma.
- Seletor de receitas, Configurações, troca de idioma pt-BR/en em tempo real.
- **Compra única** (não-consumível) via Google Play Billing, com gate no primeiro disparo.
- Grid de terços, indicador de nível, preview P&B no Rua.

### Fora (com destino)
- **Zebra/Peaking** — no iOS estão escondidos atrás de flag até calibrar. No Android: **portar
  já escondidos** pela mesma flag (código presente, toggles ocultos). Roadmap (seção 28).
- **Receitas criadas pelo usuário** — só o campo `criada_pelo_usuario` existe, preparando o
  terreno. Fora do MVP (roadmap), igual iOS.
- **Controle de abertura** — hardware não permite (abertura fixa). Fora, igual iOS.
- **Zone focus por distância em metros** — sem calibração; fora (roadmap), igual iOS.
- **Widget nativo da tela de bloqueio** — no iOS ficou como backlog (Control do iOS 18). No
  Android o equivalente é o **Quick Settings Tile** (ver 8.10); entra no MVP por ser barato e
  confiável. Widget de home screen customizado fica fora (roadmap).

---

## 4. Arquitetura

Stack **nativa Android**, escolhida para casar com o caminho local-first/nativo do Alexandre e
por ser onde o controle de câmera e a fidelidade de cor são melhores (o mesmo motivo de o iOS ser
nativo). App **iPhone-only → Android phone-only, retrato apenas** (paridade).

| Camada | Tecnologia | Por quê | Alternativa descartada |
|---|---|---|---|
| UI | **Kotlin + Jetpack Compose** | Declarativa (espelha SwiftUI), padrão atual Android. | XML/Views: verboso, distante do modelo SwiftUI do iOS. |
| Câmera | **CameraX** (`Preview`, `ImageAnalysis`, `ImageCapture`) | API de alto nível estável, lida com ciclo de vida e compatibilidade de aparelhos; expõe Camera2 p/ controles manuais via `Camera2Interop`. | Camera2 puro: muito boilerplate e frágil entre fabricantes. |
| Filtros | **OpenGL ES 2.0 + shader GLSL** (preview e captura) | Único jeito de rodar o **mesmo** pipeline no preview ao vivo e no JPEG (princípio 0.2) com performance. | RenderScript: **depreciado**. `RenderEffect`/AGSL: só Android 13+ e ruim p/ stream de câmera. Filtro em Bitmap na CPU: lento demais p/ preview. |
| Persistência | **Room** (2 entidades) + **DataStore** só se necessário | Espelha o SwiftData do iOS (catálogo de receitas + singleton de config no mesmo store); suporta o "×2 meio-passo", o upsert e a deleção de não-canônicas com migração aditiva. | DataStore sozinho: sem consulta relacional p/ o catálogo de receitas. |
| Compra | **Google Play Billing Library** (produto único não-consumível) | Equivalente ao StoreKit; posse verificada localmente após 1ª confirmação (offline). | — |
| Localização | **Recursos Android** (`res/values`, `res/values-en`) + troca em runtime | Padrão da plataforma; troca de locale por `AppCompatDelegate`/`configurationOverride`. | Bundle manual (o que o iOS faz) é desnecessário no Android. |
| Salvamento | **MediaStore** (`Pictures/`) | API padrão de galeria com escopo de armazenamento (Android 10+), sem permissão de storage no Android 10+. | `WRITE_EXTERNAL_STORAGE`: obsoleto/negado em Android novo. |

- **Onde o código vive:** repositório/pasta **`~/I See Life Android`**, novo repositório git,
  independente do iOS. Sugestão de repo remoto: `github.com/asiqueira1973/iseelife-android`
  (privado), a confirmar (seção 25).
- **Onde roda:** device/emulador Android. **Build e teste 100% no Android Studio na máquina do
  Alexandre** — o ambiente do agente não tem SDK/emulador Android (premissa a validar: instalar
  Android Studio, que traz SDK + emulador + JDK 17/21; o JDK 25 presente é novo demais p/ o AGP).
- **Application ID:** `com.dablab.iseelife` (mesmo namespace de marca do iOS; app separado na
  Play). A confirmar se o Alexandre prefere sufixo (seção 25).
- **Build system:** Gradle (Kotlin DSL, `build.gradle.kts`), **Version Catalog** (`libs.versions.toml`).
- **Versões:** `minSdk 26` (Android 8.0 — cobre TileService/App Shortcuts e GL ES 2.0),
  `targetSdk`/`compileSdk` = a versão que a Play exige no momento do build (hoje ≥ 35). **Premissa
  a validar no build:** fixar as versões estáveis mais recentes de Compose (BOM), CameraX, Room e
  **Play Billing na versão mínima que a Play aceitar no upload** — verificar no console antes de
  publicar (a Play sobe esse piso periodicamente).

> **Premissa técnica a validar (seção 23):** o agente não consegue compilar aqui. Todas as
> versões de dependência e a compilação são confirmadas no primeiro build no Android Studio; o
> código é escrito idiomático e comentado para minimizar correções de compilação.

---

## 7. Modelo de dados

Duas entidades **Room** independentes (sem chave estrangeira, sem relação — igual iOS). Nomes de
tabela/coluna em **português `snake_case`** (convenção 18); as classes/DAO Kotlin em inglês
idiomático, com os campos espelhando os nomes do banco. Banco local SQLite via Room.

### `configuracao_usuario` — linha única (singleton)

Criada no primeiro uso; atualizada in-place; nunca excluída no MVP.

| Coluna | Tipo | Nulo? | Default | Significado |
|---|---|---|---|---|
| `id` | INTEGER (pk) | não | 1 | Sempre 1 (singleton; check `id = 1`). |
| `idioma_preferido` | TEXT (enum `idioma`) | não | `pt` | Idioma da interface (pt/en). Troca em tempo real. |
| `modo_padrao` | TEXT (enum `modo_captura`) | não | `rua` | Modo ao abrir o visor (princípio 0.1). |
| `ultima_lente_usada` | TEXT (enum `lente_camera`) | sim | null | Última lente; nulo até a 1ª captura. Nulo = escolher a wide. |
| `grid_ativado` | INTEGER (bool) | não | 1 | Grid de terços. |
| `nivel_ativado` | INTEGER (bool) | não | 1 | Indicador de nível. |
| `obturador_silencioso` | INTEGER (bool) | não | 1 | Captura sem som. |
| `preview_pb_ativado` | INTEGER (bool) | não | 0 | Preview P&B no Rua (só preview). |
| `compra_desbloqueada` | INTEGER (bool) | não | 0 | `true` após compra confirmada (regra 11.4). |
| `data_compra` | INTEGER (epoch ms) | sim | null | Preenchida na confirmação; nulo antes. |
| `onboarding_concluido` | INTEGER (bool) | não | 0 | Controla reaparição do onboarding. |
| `receita_ativa_id` | TEXT (uuid) | sim | null | Última receita escolhida; nulo = usa `ordem_exibicao = 1`. |
| `rua_obturador_denominador` | INTEGER | não | 250 | Piso do obturador no Rua (250/500/1000). Enhancement do iOS. |

### `receita` — catálogo fixo (somente-leitura no MVP)

Populada por seed/sincronização; cada campo alimenta o shader. **Paridade exata de valores com o
iOS** (tabela em 11.2). `id` é UUID (paridade com o iOS, onde `receita_ativa_id` guarda o UUID).

| Coluna | Tipo | Nulo? | Default | Significado |
|---|---|---|---|---|
| `id` | TEXT (uuid, pk) | não | — | Identificador estável. |
| `nome_exibicao` | TEXT (unique) | não | — | Rótulo/identidade estável ("Classic"…). |
| `inspiracao` | TEXT | não | — | Referência interna (não exibida como marca). |
| `highlight` | INTEGER (meio-passo ×2) | não | 0 | Curva de tom nas altas luzes. **Guardado ×2.** |
| `shadow` | INTEGER (meio-passo ×2) | não | 0 | Curva de tom nas sombras. **Guardado ×2.** |
| `saturacao` | INTEGER (meio-passo ×2) | não | 0 | Saturação (ignorada em mono). **Guardado ×2.** |
| `temperatura_wb_kelvin` | INTEGER | sim | null | Kelvin assumido da cena; nulo = segue o sensor. |
| `tinta_wb` | INTEGER (-9…+9) | não | 0 | Eixo **verde/magenta** (não vermelho/azul). |
| `intensidade_grao` | TEXT (enum `intensidade_grao`) | não | `nenhum` | Força do grão. |
| `nitidez` | INTEGER (-4…+4) | não | 0 | +sharpen / −desfoque leve. |
| `monocromatico` | INTEGER (bool) | não | 0 | Remove toda a cor (preview e arquivo). |
| `filtro_mono` | TEXT (enum `filtro_mono`) | sim | null | Filtro colorido simulado; só com mono. |
| `ordem_exibicao` | INTEGER | não | — | Ordem fixa no seletor; ordem 1 = padrão. |
| `ajuste_vermelho` | INTEGER | não | 0 | WB shift eixo **vermelho** (ganho canal R). v1.1. |
| `ajuste_azul` | INTEGER | não | 0 | WB shift eixo **azul** (ganho canal B). v1.1. |
| `efeito_color_chrome` | TEXT (enum `efeito_color_chrome`) | não | `desligado` | Compressão de saturação (eixo quente). v1.1. |
| `efeito_color_chrome_azul` | TEXT (enum `efeito_color_chrome`) | não | `desligado` | Idem, eixo azul. v1.1. |
| `clareza` | INTEGER (-4…+4) | não | 0 | Contraste local (≠ nitidez). v1.1. |
| `tamanho_grao` | TEXT (enum `tamanho_grao`) | não | `pequeno` | Tamanho do grão. v1.1. |
| `criada_pelo_usuario` | INTEGER (bool) | não | 0 | false no catálogo fixo; prepara custom (roadmap). |

> **Meio-passo ×2 (crítico — não quebrar):** `highlight`/`shadow`/`saturacao` guardam o valor
> **dobrado** (ex.: -1,5 → -3). A camada que monta os uniforms do shader **divide por 2**. É o
> mesmo esquema do iOS; manter idêntico garante paridade de cor e evita migração de tipo.

**Enums** (valores idênticos ao iOS):
- `idioma`: `pt`, `en`
- `modo_captura`: `rua`, `pro`
- `lente_camera`: `wide`, `ultra_wide`
- `intensidade_grao`: `nenhum`, `fraco`, `medio`, `forte`
- `filtro_mono`: `nenhum`, `amarelo`, `vermelho`, `verde`
- `efeito_color_chrome`: `desligado`, `fraco`, `forte`
- `tamanho_grao`: `pequeno`, `grande`

**Diagrama (texto):** duas ilhas, sem relação.
```
configuracao_usuario (1 linha)      receita (catálogo fixo, N linhas)
   receita_ativa_id  ......(valor, não FK)......>  receita.id
```
A ligação "receita ativa" é **por valor** (`receita_ativa_id` guarda o `id`), não por FK — igual
iOS. Sem triggers, sem lógica no banco.

**Constraints/índices:** `receita.nome_exibicao` unique; `configuracao_usuario` com check
`id = 1`; índice em `receita.ordem_exibicao`.

**Exclusão:** `configuracao_usuario` nunca é excluída (singleton). `receita`: **hard delete** só
para receitas fixas que saíram do catálogo (ver `sincronizarCatalogo`, regra 11.5), respeitando
`criada_pelo_usuario = 0`.

**Migração:** aditiva. Colunas novas entram com default; **nunca** trocar o tipo de uma coluna
existente. Room `Migration` versionada; a v1.1 do iOS provou que campos novos com default e
`hl/sh/sat` em ×2 evitam migração de tipo. Como esta é a **primeira** release Android, o schema já
nasce com todos os campos v1.1 — mas a regra de migração aditiva vale para as próximas.

**Seed:** valores em 11.2, inseridos/sincronizados na abertura (regra 11.5).

---

## 8. Épicos e critérios de aceite (ordem de implementação)

Ordem pensada para o port: **fundação de dados → pipeline (risco maior) → câmera → telas →
compra → integrações de plataforma → i18n**. Cada épico tem critérios binários.

### 8.0 — Esqueleto do projeto e build
- Projeto Gradle (Kotlin DSL) com Compose, CameraX, Room, Play Billing declarados no version
  catalog; `minSdk 26`; `applicationId com.dablab.iseelife`; retrato-only; single-activity.
- **Aceite:** abre no Android Studio, `assembleDebug` compila, app sobe numa tela vazia preta em
  retrato no emulador.

### 8.1 — Modelo de dados (Room) + seed/sincronização
- Entidades `configuracao_usuario` e `receita`, DAOs, banco Room, enums (7).
- `RecipeSeed.sincronizarCatalogo` (regra 11.5): upsert por `nome_exibicao`, copia **todos** os
  campos (inclusive v1.1), e **deleta** fixas fora do catálogo respeitando `criada_pelo_usuario`.
- Singleton de config criado no 1º uso.
- **Aceite:** 1ª execução cria 6 receitas na ordem certa + a linha de config; reabrir não
  duplica; remover uma receita do código e reabrir a apaga do banco; `receita_ativa_id` continua
  válido após mudança de ordem.

### 8.2 — Pipeline de filtro GLSL (o coração do port) — regra 11.2/11.3
- Um **shader de fragmento GLSL** que aplica, na ordem exata do iOS: WB (temp+tinta) → shift R/B
  → curva de tom (hl/sh) → saturação/mono+filtro → color chrome → clareza → nitidez → grão. Ver
  regra 11.6 para a matemática de cada estágio (traduzida 1:1 do `RecipePipeline.swift`).
- Estágios que precisam de vizinhança (clareza = unsharp raio grande, nitidez, grão) usam
  passes/samples adequados; os que são por-pixel vão no mesmo pass.
- Os parâmetros da receita ativa viram **uniforms** (com `hl/sh/sat` divididos por 2).
- **Early-return "Neutra":** receita neutra devolve o frame cru (paridade com `ehNeutra`).
- **Aceite:** renderizando uma imagem de teste fixa com cada receita, o resultado é
  visualmente equivalente ao iOS (validação lado a lado no device pelo Alexandre); a Neutra sai
  idêntica à entrada; trocar de receita troca o resultado sem recriar contexto GL.

### 8.3 — Câmera CameraX + preview filtrado (WYSIWYG) — princípio 0.2
- CameraX ligada ao ciclo de vida; `Preview` renderizado através do shader (8.2) numa
  `GLSurfaceView`/`SurfaceTexture` sobre fundo preto, **aspect-fill**, retrato.
- Detecção de lentes (wide/ultra-wide) via `CameraSelector`/`Camera2Interop`; botão de lente só
  aparece com >1 lente (regra 11.1).
- Preview P&B do Rua força desaturação **só no preview** (uniform), sem afetar o arquivo.
- **Aceite:** preview ao vivo mostra a receita ativa; alternar lente funciona; com 1 lente o
  botão some; P&B do Rua não altera o JPEG salvo.

### 8.4 — Modo Rua (toque p/ foco + prioridade de obturador) — regra 11.7
- Toque no visor trava foco/exposição no ponto (indicador âmbar some ~1s).
- **Prioridade de obturador:** obturador fixo (piso configurável 1/250·1/500·1/1000) + ISO
  automático via loop amortecido (Camera2Interop: `SENSOR_EXPOSURE_TIME` fixo, ISO auto), igual
  ao enhancement do iOS.
- **Aceite:** tocar trava foco com indicador; foto de cena em movimento não borra no piso
  1/1000; trocar o piso em Configurações muda o comportamento.

### 8.5 — Modo Pro (controles manuais) — épico paridade
- Painel com sliders: ISO, obturador, exposição/EV, foco (lens position 0…1), WB (temp+tinta),
  via `Camera2Interop` (`CaptureRequest`). Sem abertura (hardware).
- Botão **"Auto"** que recalibra (zera EV, volta foco/exposição/WB a contínuo, relê valores) —
  paridade com o `restaurarAutomatico()` do iOS.
- Painel com altura limitada + rolagem; sliders só arrastam na horizontal (não conflitam com o
  scroll) — paridade com os ajustes de UX do iOS.
- **Aceite:** cada slider muda a captura; "Auto" recalibra de fato e reflete os valores; o painel
  cabe e rola sem mexer nos valores ao rolar.

### 8.6 — Captura + salvamento — regra 11.3
- Disparo no **toque (down)** do botão. Captura silenciosa (padrão): frame do preview em full-res
  com o shader já aplicado, codificado em JPEG. Com `obturador_silencioso = false`: usar
  `ImageCapture` (nítido) e aplicar o shader ao bitmap capturado antes de salvar.
- Salva no **MediaStore** (`Pictures/I See Life/`). Sem cópia sem filtro (paridade 11.3).
- Feedback de disparo: pop do botão + vibração + piscar do visor.
- **Aceite:** dispara sem som no modo silencioso; a foto salva na galeria tem exatamente a
  receita do preview; sem permissão de escrita (Android <10) o fluxo de exceção aparece.

### 8.7 — Paywall + compra (Google Play Billing) — regra 11.4
- Todas as telas navegáveis sem compra; **gate só no 1º disparo**: se `compra_desbloqueada =
  false`, captura o frame para memória (`foto_pendente`), mostra o paywall e **não salva** ainda.
- Compra do produto não-consumível `iseelife_desbloqueio_completo`; ao confirmar, marca
  `compra_desbloqueada`/`data_compra` e **salva a `foto_pendente` sem novo toque**.
- Posse verificada localmente via `queryPurchasesAsync` (offline após 1ª confirmação);
  **"Restaurar compra"** reconsulta. Preço vem da Play (`formattedPrice`), nunca hardcoded.
- **Aceite:** disparar sem compra abre o paywall e a foto fica pendente; comprar salva a pendente
  automaticamente; reabrir offline continua desbloqueado; "Restaurar" recupera em outro aparelho.

### 8.8 — Onboarding + permissões
- Boas-vindas + pedido de permissão de câmera; ao concluir grava `onboarding_concluido`.
- Estados: permissão concedida → visor; negada → tela de permissão negada (com atalho p/
  Configurações do sistema).
- **Aceite:** onboarding aparece só uma vez; negar permissão leva à tela dedicada; conceder cai
  no visor do Rua.

### 8.9 — Configurações + seletor de receitas
- Configurações lê/escreve `configuracao_usuario` (idioma, grid, nível, obturador silencioso,
  preview P&B, piso do obturador do Rua, restaurar compra). Acessível **também no Rua**
  (engrenagem no topo) — paridade iOS.
- Seletor de receitas lê `receita`, escreve `receita_ativa_id`; ao trocar receita no Rua,
  **refresh** de foco/WB (paridade `resetarFocoWBContinuo`).
- **Aceite:** cada toggle persiste e afeta o comportamento; trocar receita atualiza o preview e
  relê foco/WB no Rua.

### 8.10 — Integrações de plataforma (atalhos de disparo)
- **Botões de volume** disparam (paridade iOS 17.2+): captar `KeyEvent` VOLUME_UP/DOWN na
  Activity do visor e chamar o mesmo disparo; ignorar com sheet/paywall aberto.
- **App Shortcut** estático "Disparar" (long-press no ícone) que abre o app no visor do Rua.
- **Quick Settings Tile** ("I See Life") que abre o app no Rua — equivalente Android ao Control
  do iOS 18 (este funciona de forma confiável no Android, diferente do bug do iOS).
- **Aceite:** os dois botões de volume disparam no visor; long-press no ícone mostra "Disparar";
  a Tile aparece na lista de blocos rápidos e abre o app pronto para disparar.

### 8.11 — Localização pt-BR / en em tempo real — paridade
- Todas as strings em `res/values/strings.xml` (pt-BR default) e `res/values-en/strings.xml`.
  Troca de idioma em runtime aplica o locale e redesenha; persiste `idioma_preferido`.
- **Aceite:** nenhuma string hardcoded na UI; trocar idioma em Configurações muda tudo na hora e
  persiste ao reabrir.

---

## 10. Inventário de telas

Paridade 1:1 com o iOS (as telas **replicam** os designs existentes em `~/I See Life  app/telas`
e o design system do iOS; não há novo trabalho de design — por isso este spec **não** gera um
`prompt-design`). App consumer mobile, **sem** menu lateral.

| Tela | Mostra | Lê | Escreve |
|---|---|---|---|
| Onboarding — Boas-vindas | Marca + proposta | — | — |
| Onboarding — Permissão | Pedido de câmera | permissão do sistema | `onboarding_concluido` |
| Visor — Modo Rua | Preview filtrado, disparo, receita ativa, lente, grid/nível | `configuracao_usuario`, `receita` | `ultima_lente_usada`, `receita_ativa_id` |
| Visor — Modo Pro | Idem + painel de sliders manuais | `configuracao_usuario`, `receita` | `ultima_lente_usada`, `grid_ativado`, `nivel_ativado` |
| Seletor de receita | Lista das 6 receitas | `receita` | `receita_ativa_id` |
| Paywall | Preço da Play, benefício, comprar/restaurar | produto Play Billing | `compra_desbloqueada`, `data_compra` |
| Configurações | Toggles + idioma + piso obturador + restaurar | `configuracao_usuario` | idioma, grid, nível, silencioso, preview P&B, `rua_obturador_denominador`, compra |
| Permissão negada | Aviso + botão p/ Configurações do sistema | — | — |

---

## 11. Regras de negócio

### 11.1 — Detecção de lentes
CameraX `CameraSelector` + `Camera2Interop` para enumerar wide/ultra-wide traseiras. Só as
fisicamente presentes. Fallback: sem enumeração, usa a câmera traseira padrão como wide. O botão
de lente **oculta** (não desabilita) quando há só 1 lente.

### 11.2 — Parâmetros das 6 receitas (paridade EXATA com o iOS)

Valores **REAIS** (no banco, `hl/sh/sat` ficam ×2). Ordem 1 = Neutra/raw (padrão).

| Ordem | Receita | hl | sh | sat | temp WB | tinta | R/B | grão (força·tam) | nitidez | clareza | color chrome (Q·Az) | mono/filtro |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Neutra | 0 | 0 | 0 | nulo | 0 | 0/0 | nenhum·peq | 0 | 0 | –·– | não |
| 2 | Classic | −1 | +1 | +2 | 7000K | 0 | 0/0 | fraco·peq | +2 | 0 | –·– | não |
| 3 | Soft | −2 | 0 | −2 | 5600K | +1 | 0/0 | nenhum·peq | −1 | 0 | –·– | não |
| 4 | Mono | −1 | +4 | 0 | nulo | −1 | 0/0 | forte·peq | −2 | 0 | –·– | sim/vermelho |
| 5 | Positivo | −3 | +3 | +3 | 5000K | +3 | 0/0 | nenhum·peq | 0 | 0 | –·– | não |
| 6 | Vívida | 0 | +0,5 | +2 | 5500K | 0 | +2/−5 | fraco·peq | +1 | +3 | forte·– | não |

> Estes números são a fonte de verdade compartilhada com o iOS. Se algum precisar de ajuste após
> a validação visual no Android (telas/câmeras diferentes), ajustar **aqui e no seed**, mantendo
> a intenção. `inspiracao` por receita: Classic="Classic Chrome", Soft="Astia/Soft",
> Mono="Acros+R", Positivo="Ricoh GR Positive Film", Vívida="Kodachrome 64", Neutra="Câmera
> (sem receita)".

### 11.3 — Pipeline de captura
O **mesmo** shader do preview é aplicado ao frame de captura antes de codificar o JPEG (princípio
0.2). Não existe caminho "sem filtro": a Neutra (ordem 1) é o padrão e faz early-return no shader.
O app não guarda cópia sem filtro — só o JPEG final é salvo (MediaStore).

### 11.4 — Paywall (gate no 1º disparo)
Todas as telas/modos navegáveis sem compra. Gate só no disparo: `compra_desbloqueada = false` →
captura para memória (`foto_pendente`) + paywall; não salva. Após confirmar a compra: marca
`compra_desbloqueada`/`data_compra` e salva a pendente sem novo toque. Posse verificada
localmente (offline após a 1ª confirmação).

### 11.5 — Sincronização do catálogo (`sincronizarCatalogo`) — não quebrar
Roda a cada abertura. Upsert por `nome_exibicao` (identidade estável, preserva `id` e portanto
`receita_ativa_id`); copia **todos** os campos (inclusive v1.1); e **deleta** receitas fixas que
saíram do catálogo (por `nome_exibicao`), respeitando `criada_pelo_usuario = 1`. Catálogo é dado
do código, não do usuário — o banco sempre reflete os valores canônicos.

### 11.6 — Matemática do shader (tradução 1:1 do `RecipePipeline.swift`)

Cada estágio só age quando o campo ≠ neutro (as 5 receitas originais saem idênticas). Uniforms
recebem `hl/sh/sat` **já divididos por 2**.

| Estágio | Fórmula (do iOS) |
|---|---|
| **WB (temp/tinta)** | Equivalente ao `temperatureAndTint` com `neutral=(temp?6500, tinta*4)`, `targetNeutral=(6500,0)`. Escala do tinta = **×4** (reduzida no device; não usar ×10). |
| **Shift R/B (v1.1)** | `ganhoR = 1 + (ajuste_vermelho/9)*0.18`; `ganhoB = 1 + (ajuste_azul/9)*0.18`; multiplica canais R e B. |
| **Curva de tom** | 5 pontos: (0,0),(0.25, 0.25+sh/4*0.12),(0.5,0.5),(0.75, 0.75+hl/4*0.12),(1,1). Interpolação suave; clamp 0..1. |
| **Saturação/Mono** | Colorido: `sat_out = 1 + (sat/4)*0.5`. Mono (ou preview P&B): luma ponderada pelo filtro — nenhum `(.299,.587,.114)`, vermelho `(.80,.10,.10)`, amarelo `(.50,.40,.10)`, verde `(.10,.70,.20)`. |
| **Color Chrome (v1.1)** | `total = peso(quente) + peso(azul)*0.7`, peso: desligado 0 / fraco 0.05 / forte 0.11. Aplica `saturação *= (1 - total)` (aprox. global). |
| **Clareza (v1.1)** | `clareza>0`: unsharp raio grande (~12px), intensidade `(clareza/4)*0.6`. `clareza<0`: leve blur, raio `(-clareza/4)*0.8`. |
| **Nitidez** | `>0`: sharpen luminância, `sharpness=nitidez/4`. `<0`: blur gaussiano leve, raio `(-nitidez/4)*1.5`. |
| **Grão** | Opacidade: nenhum→skip / fraco 0.05 / medio 0.11 / forte 0.18. Ruído procedural deslocado por frame; `tamanho_grao=grande` amplia o ruído ~2.6×; mescla em **soft light**. |

> **Nota de fidelidade:** algumas etapas do Core Image não têm equivalente idêntico em GLSL ES 2.0
> (o color chrome já é aproximação global no próprio iOS). O alvo é **equivalência visual**
> validada lado a lado no device, não igualdade de bits. Divergências inevitáveis vão para a
> seção 6 da doc técnica.

### 11.7 — Prioridade de obturador no Rua
Obturador fixo (piso `rua_obturador_denominador`: 1/250 padrão, 1/500, 1/1000) + ISO automático,
via loop amortecido sobre `Camera2Interop` (`SENSOR_EXPOSURE_TIME` fixo, `CONTROL_AE` ajustando
ISO). No Pro, o loop desliga e valem os controles manuais.

### 11.8 — Sem controle de abertura
Abertura fixa por hardware; o Pro expõe ISO, obturador, EV, foco e WB — sem abertura (paridade).

---

## 16. Direção visual

**Reutiliza o design system do iOS** — não há redesenho. Fundo do visor preto; accent âmbar
`#FFB000` só para significado (foco travado, receita ativa, toggle ligado); painéis translúcidos
escuros; tipografia e ícones equivalentes aos do iOS (usar Material 3 com tema escuro custom que
reproduz a aparência iOS, não o Material padrão colorido). Swatches de fallback das receitas
(quando não há frame, ex.: emulador) iguais aos do iOS. Modo do app é **sempre escuro** (câmera).

Referência literal: telas e assets em `~/I See Life  app/telas`, `iseelifeicon.png` e o
`DesignSystem.swift` do iOS. Ícone adaptativo Android (`ic_launcher` foreground/background) a
partir do ícone do iOS, com área segura — **não** reaproveitar o `AppIcon` iOS direto.

---

## 17. Requisitos não-funcionais

- **Preview fluido:** shader roda a ≥ 30 fps em aparelho mediano; sem travar a UI thread (GL em
  render thread própria).
- **Abertura → pronto para disparar:** alvo ≤ 1,5 s em aparelho mediano.
- **Offline:** 100% funcional sem rede (exceto a transação de compra).
- **Retrato apenas**, phone apenas (paridade). `minSdk 26`.
- **Acessibilidade:** área de toque ≥ 48 dp nos controles; contraste do texto sobre o visor;
  `contentDescription` nos ícones; respeitar tamanho de fonte do sistema onde não quebrar o HUD.
- **Bateria/térmico:** preview de câmera + GL é custoso; pausar a sessão fora do primeiro plano.
- **Binário:** sem dependências pesadas desnecessárias; sem libs de rede.
- **Idiomas:** pt-BR (default) e en.

---

## 18. Convenções de código, documentação e estrutura do repositório

- **Idioma — banco em português, código em inglês.** Tabelas, colunas e enums em português
  `snake_case` (as `@Entity`/`@ColumnInfo` do Room usam esses nomes); classes, funções, variáveis
  e arquivos Kotlin em inglês idiomático. Os tipos que espelham o banco mantêm os nomes de campo
  do banco.
- **Comentários obrigatórios em português.** Toda classe, função pública, composable de tela,
  DAO, migration e etapa de shader leva cabeçalho em português dizendo o que faz, o que recebe/
  devolve e **qual regra do spec implementa** (com número). Lógica não óbvia (matemática do
  shader, loop de obturador, ×2 do meio-passo, deleção do catálogo) leva comentário inline.
- **Organização (módulo por feature):**
  ```
  app/src/main/java/com/dablab/iseelife/
    App.kt, MainActivity.kt
    dados/        (Room: entidades, DAOs, database, seed/sincronizacao)
    camera/       (CameraX, Camera2Interop, controles manuais, shutter priority, hardware shutter)
    filtros/      (GL: renderer, programa de shader, uniforms da receita, zebra/peaking)
    compras/      (Play Billing manager)
    telas/        (onboarding, visor{rua,pro}, seletor, paywall, configuracoes, permissao)
    designsystem/ (cores, tipografia, swatches)
    localizacao/  (troca de locale em runtime)
    plataforma/   (app shortcut, quick settings tile, volume keys)
    res/values, res/values-en, res/drawable, res/mipmap (ícone adaptativo)
  ```
- **Documentação viva:** `README.md` (setup no Android Studio, como rodar, versões),
  `CLAUDE.md` na raiz (convenções, arquitetura, comandos de build/lint, e as **pegadinhas que não
  podem ser quebradas** — ×2 do meio-passo, deleção do `sincronizarCatalogo`, mesmo shader em
  preview e captura, migração aditiva), e a `DOCUMENTACAO-TECNICA.md` (seção 19). Manter os três
  atualizados **no mesmo commit** da mudança.
- **Sem variáveis de ambiente/segredos no app** (local-first; a chave de assinatura do APK/AAB e
  a conta Play ficam fora do repositório).

---

## 19. Documento de documentação técnica (entregável ao fim)

Ao final da implementação, gerar `DOCUMENTACAO-TECNICA.md` (português), descrevendo o app Android
como ficou de fato, com os 6 itens: (1) modelo de dados explicado; (2) relação entre entidades
(duas ilhas, ligação por valor); (3) lógica que vive no banco (nenhuma — declarar); (4) mapa tela
→ entidades; (5) lógica das principais funcionalidades por tela (com destaque para o pipeline GL
e o loop de obturador); (6) **divergências** em relação a este spec **e** em relação ao
comportamento iOS (fidelidade do shader, APIs de câmera, etc.). Atualizar a cada mudança
relevante, no mesmo commit.

---

## 20. Privacidade, segurança e conformidade

- **Dados coletados:** nenhum enviado para fora do aparelho. Config e receitas ficam no Room
  local; fotos vão para a galeria do usuário (MediaStore). Sem analytics de rede, sem conta.
- **Permissões:** câmera (obrigatória); vibração (feedback). Em Android < 10, escrita em storage
  para salvar (fluxo de exceção se negada); Android 10+ usa MediaStore sem permissão de storage.
- **Compra:** processada pela Google Play; o app guarda só o booleano de posse + data. Sem dado
  de pagamento no app.
- **LGPD:** política de privacidade da Play declarando "não coletamos dados pessoais; fotos ficam
  no dispositivo" — reaproveitar o texto do iOS (Dab Lab), adaptado à Play. Sem base legal
  sensível porque não há coleta.
- **Segurança:** sem credenciais no app; validação de posse é a `Purchase` verificada pela Play
  Billing localmente (sem servidor próprio no MVP; assinatura verificada pela biblioteca).

---

## 22. Estratégia de teste

- **Automatizado:** testes unitários das regras numéricas — conversão ×2 → uniforms, `ehNeutra`,
  a lógica de `sincronizarCatalogo` (upsert + deleção respeitando `criada_pelo_usuario`), o
  cálculo do piso de obturador. Testes de migração do Room.
- **Manual (device):** validação visual das 6 receitas lado a lado com o iOS; captura real;
  permissões nos 3 estados; compra em sandbox (conta de teste da Play); botões de volume, App
  Shortcut e Quick Tile; troca de idioma; aparelhos de fabricantes diferentes (câmera varia).
- **O que só roda no device:** câmera, GL sobre câmera, compra, salvamento — o emulador tem
  câmera virtual limitada; as telas sem câmera validam no emulador.

---

## 23. Riscos e mitigação

| Risco | Impacto | Prob. | Mitigação |
|---|---|---|---|
| **Sem toolchain Android no ambiente do agente** — não dá p/ compilar/rodar aqui. | Alto | Certo | Código idiomático e comentado; primeiro build no Android Studio do Alexandre; iterar sobre os erros de build reais. Instalar Android Studio (SDK+emulador+JDK 17/21) antes de começar a testar. |
| **Fidelidade do shader GLSL vs Core Image** — cor não bate 1:1. | Alto | Média | Traduzir a matemática 1:1 (11.6); validar lado a lado no device; ajustar por comparação visual (mesmo DoD do iOS); registrar aproximações. |
| **Fragmentação de câmera Android** (Camera2 varia por fabricante; nem todo device dá controle manual/exposição fixa). | Alto | Média | CameraX + checagem de capacidade; degradar controles indisponíveis (ocultar, como o botão de lente); testar em ≥ 2 fabricantes. |
| **Play sobe o piso da Billing Library** e recusa o upload. | Médio | Média | Fixar a versão mínima exigida no momento do build; conferir no Play Console antes de publicar. |
| **Performance do preview GL** em aparelho de entrada (< 30 fps, aquecimento). | Médio | Média | GL em render thread; evitar passes desnecessários (early-return neutra); medir em device mediano. |
| **JDK 25 presente incompatível com o AGP.** | Baixo | Certo | Usar o JDK que o Android Studio instala (17/21); apontar `JAVA_HOME` do projeto. |

---

## 24. Assunções declaradas (revisar antes de codar)

- **24.1** `applicationId = com.dablab.iseelife` (mesmo namespace de marca do iOS). Se preferir
  outro, aviso e ajusto.
- **24.2** `minSdk 26` (Android 8.0). Cobre TileService, App Shortcuts e GL ES 2.0. Se quiser
  alcançar aparelhos mais antigos (21–25), aviso e reduzo (perdendo a Quick Tile em <24).
- **24.3** Room para **as duas** entidades (config singleton + catálogo), espelhando o SwiftData
  do iOS, em vez de DataStore para a config. Se preferir DataStore para as preferências, ajusto.
- **24.4** Filtros em **OpenGL ES 2.0 com shader próprio** (sem lib de terceiros). Reduz
  dependência e casa com o pipeline do iOS; se preferir uma lib (ex.: GPUImage) para acelerar,
  decidimos juntos (menos controle, dependência a mais).
- **24.5** Captura silenciosa = frame-grab do preview em full-res (mesma decisão do iOS). Em
  Android, o som do obturador também é controlável por `ImageCapture`/config do device; mantemos
  o frame-grab por paridade e por ser mais previsível entre fabricantes.
- **24.6** Ícone adaptativo Android derivado do ícone do iOS (foreground/background), não o
  `AppIcon` cru. Gero uma proposta; você valida.
- **24.7** Preço do IAP: o mesmo do iOS (~R$ 49,90), configurado no Play Console (não hardcoded).
  Confirmar valor/nome do produto (seção 25).
- **24.8** Sem verificação server-side da compra no MVP (validação local pela Billing Library),
  igual ao iOS (validação local StoreKit). Se quiser backend de verificação, é roadmap.

## 25. Perguntas abertas (o agente pergunta em vez de assumir)

- **25.1** Repositório remoto: criar `github.com/asiqueira1973/iseelife-android` privado? (mesmo
  padrão dos outros apps). — decide Alexandre.
- **25.2** `applicationId` final e nome do produto de IAP na Play (`iseelife_desbloqueio_completo`
  ou outro) + preço. — decide Alexandre.
- **25.3** Faixa de aparelhos-alvo para teste (quais Androids o Alexandre tem/consegue testar). —
  decide Alexandre.
- **25.4** Instalar o Android Studio nesta máquina agora (para termos build/emulador) ou o
  Alexandre prefere buildar em outro ambiente? — decide Alexandre.

## 26. Definition of Done

- [ ] 6 receitas com paridade visual validada lado a lado com o iOS no device.
- [ ] Preview e captura usam o **mesmo** shader (WYSIWYG); Neutra sai crua.
- [ ] Modo Rua (foco por toque + prioridade de obturador) e Modo Pro (sliders + Auto) completos.
- [ ] Captura silenciosa salva na galeria (MediaStore) com a receita ativa.
- [ ] Paywall: gate no 1º disparo, compra salva a pendente, posse offline, restaurar compra.
- [ ] Botões de volume + App Shortcut + Quick Settings Tile disparam/abrem no Rua.
- [ ] pt-BR/en em runtime, sem string hardcoded.
- [ ] Banco em português conforme a convenção; migração aditiva; `sincronizarCatalogo` com
      deleção de não-canônicas testada.
- [ ] Todas as rotinas/etapas comentadas em português.
- [ ] `DOCUMENTACAO-TECNICA.md`, `CLAUDE.md` e `README.md` gerados e conferidos.
- [ ] Build de release (AAB) assinado, `targetSdk` e Billing Library na versão exigida pela Play.
- [ ] Permissões nos 3 estados e compra em sandbox testadas no device.

## 27. Próximos passos operacionais

1. Alexandre revisa as assunções (seção 24) e responde as perguntas abertas (seção 25).
2. Instalar/confirmar Android Studio + SDK + emulador na máquina (seção 25.4).
3. Criar o repositório (25.1).
4. Implementar na ordem dos épicos (8.0 → 8.11): fundação de dados → **pipeline GL** → câmera →
   telas → compra → integrações → i18n. Validar cada épico contra os critérios de aceite.
5. Ao fim, gerar a documentação técnica; rodar o DoD; preparar a ficha da Play.

## 28. Roadmap: próximas releases

- **Zebra/Peaking** — portados já escondidos atrás de flag; calibrar e reativar (mesmo estado do
  iOS).
- **Receitas criadas pelo usuário** — campo `criada_pelo_usuario` já preparado; editor de
  receitas + persistência de custom.
- **Widget de home screen** customizado (além da Quick Tile).
- **Zone focus por distância** — se houver calibração viável no aparelho.
- **Verificação server-side da compra** — se houver necessidade antifraude.

## 29. Protocolo de mudanças incrementais

Depois do v1 Android pronto, para não transformar ajuste pequeno em reescrita:

1. Commit de checkpoint antes de qualquer mudança (git diff confiável).
2. Não recolar o spec inteiro: colar só o trecho novo/alterado.
3. Escrever a instrução como diff ("o projeto Android já existe e funciona; altere só X").
4. Delimitar nominalmente o que **não** muda (ex.: "não mexer no pipeline GL nem no schema").
5. Pedir a lista de arquivos a criar/alterar **antes** do código, e aprovar.
6. Revisar o `git diff` contra o plano; reverter o que passou do escopo.
7. Atualizar `DOCUMENTACAO-TECNICA.md` no mesmo commit.

**Modelo de prompt incremental (colar no Claude Code):**
```
O app Android I See Life já existe e funciona em ~/I See Life Android. Quero UMA mudança:
[descreva]. NÃO altere: [liste — ex.: schema Room, matemática do shader (11.6), fluxo de
compra]. Antes de codar, liste os arquivos que vai criar/alterar e aguarde meu OK. Ao final,
atualize DOCUMENTACAO-TECNICA.md no mesmo commit. Não toque no projeto iOS.
```

---

## Anexo — Fonte de verdade herdada do iOS

- `~/I See Life  app/spec.md` — spec de produto original (princípios, receitas, regras).
- `~/I See Life  app/DOCUMENTACAO-TECNICA.md` — comportamento real implementado (referência de
  paridade).
- `~/I See Life  app/Sources/Filtros/RecipePipeline.swift` — a matemática traduzida em 11.6.
- `~/I See Life  app/Sources/Filtros/RecipeSeed.swift` — os valores de seed (tabela 11.2).
- `~/I See Life  app/telas`, `iseelifeicon.png`, `DesignSystem.swift` — referência visual.
