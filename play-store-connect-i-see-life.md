# Google Play Console — I See Life (pacote de preenchimento)

Pronto para colar no Play Console. App: **I See Life** — câmera de fotografia de rua Android,
local-first, 100% offline, compra única. Package `com.dablab.iseelife`. Conta **Dab Lab Developer**.

> Reaproveita os ativos da Dab Lab (mesmo do iOS): Support URL no Carrd, Política de Privacidade no
> Notion, contato alexandre@dablab.com.br. Publica **direto em produção** (conta estabelecida,
> isenta do teste fechado de 20 pessoas).

---

## A. Ficha da loja (Main store listing)

- **App name (≤30):** `I See Life`
- **Short description (≤80):**
  ```
  Fotografia de rua: o filtro entra na hora do disparo e vira o arquivo final.
  ```
- **Full description (≤4000):**
  ```
  O I See Life é feito para fotografia de rua. A ideia é simples: a foto sai pronta na hora. O visual é aplicado no momento do disparo e vira o arquivo final — nada de passar horas editando depois. É a filosofia out-of-the-camera: escolha a receita, aponte e dispare.

  RECEITAS COM CARÁTER
  Comece na Neutra (imagem crua da câmera, sem filtro) ou escolha um dos visuais, aplicados ao vivo no visor e gravados direto no JPEG:
  - Classic: cores quentes, clima analógico
  - Soft: suave, contido, tons de pele bonitos
  - Mono: preto e branco contrastado com grão
  - Positivo: verde e saturado, cara de slide de filme
  - Vívida: cores punchy, alto impacto

  MODO RUA: UM TOQUE E ACABOU
  Abriu o app, já está pronto para fotografar. Sem menu no caminho até o clique. Toque na tela para travar o foco, dispare, e a foto vai direto para a sua galeria. Obturador silencioso por padrão. Dispare também pelos botões de volume, por um atalho na tela inicial ou por um bloco nas Configurações Rápidas.

  MODO PRO: CONTROLE TOTAL
  Quando quiser mandar na câmera: ISO, velocidade do obturador, exposição, foco e white balance na mão. E prioridade de obturador no Modo Rua: trave em 1/250, 1/500 ou 1/1000 e congele o movimento, sem foto tremida.

  100% SEU, 100% OFFLINE
  Sem conta, sem cadastro, sem nuvem, sem anúncios. O app funciona inteiro sem internet e suas fotos nunca saem do seu aparelho.

  PAGUE UMA VEZ
  Compra única, sem assinatura. Baixe de graça, explore os modos e as receitas à vontade, e desbloqueie quando quiser salvar sua primeira foto.

  Fotografia de rua é sobre estar pronto para o instante. O I See Life tira tudo da frente para você só fotografar.
  ```
- **App icon (512×512):** `play-assets/play_store_icon_512.png`
- **Feature graphic (1024×500):** `play-assets/feature_graphic_1024x500.png`
- **Phone screenshots:** mínimo 2 (recomendado 4–8), PNG/JPEG, lado ≥ 320 px, proporção retrato
  (9:16). **PENDENTE** — ver seção G.

---

## B. Categoria e detalhes de contato (App category / contact details)

- **App category:** Photography *(precedente do Geara — app de foto da Dab Lab)*
- **Email:** `alexandre@dablab.com.br`
- **Website:** `https://suportedablab.carrd.co/`
- **Telefone:** opcional (pode deixar em branco)

---

## C. Política de privacidade (Privacy policy)

- **URL:** o **mesmo documento Notion da Dab Lab** usado no iOS ("Política de Privacidade —
  Aplicativos da Dab Lab", link público). Dentro dele, **adicionar a seção do I See Life abaixo**
  (adaptada para Android — não reaproveitar o texto genérico que cita Firebase/login):

> ### I See Life
>
> **1. Coleta de Dados.** O I See Life não coleta, transmite nem armazena dados pessoais em
> servidores. O app não tem conta, cadastro, login nem backend, e não usa Firebase, analytics ou
> ferramentas de crash reporting. As únicas informações guardadas são as suas preferências de uso
> (idioma, opções do visor, modo padrão), que ficam exclusivamente no seu aparelho Android.
>
> **2. Uso dos Dados.** Suas preferências são usadas apenas para o funcionamento do app no
> aparelho. As fotos que você captura são salvas diretamente na galeria do dispositivo; o I See
> Life não mantém cópia própria nem as envia para qualquer lugar.
>
> **3. Armazenamento e Segurança.** Todos os dados ficam localmente no dispositivo e são removidos
> quando o app é desinstalado. O app funciona 100% offline.
>
> **4. Compartilhamento com Terceiros.** Nenhum dado é compartilhado com terceiros. A compra do
> app é processada pelo Google Play; o I See Life não tem acesso a dados de pagamento. A permissão
> de Câmera é usada somente no seu aparelho, com a sua autorização.
>
> **5. Contato.** Dúvidas sobre privacidade: alexandre@dablab.com.br.

---

## D. Declarações do app ("Set up your app" — tarefas do painel)

- **App access (Sign in details):** voltar para **"All functionality is available without special
  access"**. ✅ A partir da v3 o app dá **10 fotos grátis** antes do paywall — ou seja, o revisor (e
  qualquer usuário) usa o app inteiro **sem pagamento e sem login**; o paywall só aparece depois das
  10. Isso resolveu as duas rejeições ("app restrito por paywall" e "login credentials incorrect")
  na raiz — não há mais bypass nem instrução de login.
- **Ads:** **"No, my app does not contain ads."**
- **Content rating (questionário IARC):**
  - Email: `alexandre@dablab.com.br`
  - Categoria do app no questionário: **Utility, Productivity, Communication, or Other** (ou
    "Reference"/"Other" — é um app de câmera; NÃO é jogo).
  - Todas as perguntas de conteúdo: **Não** (sem violência, sexo, linguagem imprópria, drogas,
    jogos de azar, conteúdo assustador, UGC compartilhado, etc.). Resultado esperado: **Livre /
    Everyone**.
- **Target audience and content (público-alvo):**
  - Faixas etárias: marque **18 e acima** (e, se quiser, 16–17). **NÃO** marque faixas abaixo de
    13 (evita a política de Famílias). O app **não** é direcionado a crianças.
  - "Appeals to children?" **No**.
- **Data safety:** **Nenhum dado coletado e nenhum dado compartilhado.**
  - "Does your app collect or share any of the required user data types?" → **No**.
  - Justificativa (caso pergunte): as fotos ficam na galeria do próprio aparelho (não são
    transmitidas), as preferências são locais, e a compra é processada pelo Google Play. A
    permissão de câmera é acesso, não coleta de dados. O app é offline.
  - "Is all of the user data encrypted in transit?" → não se aplica (não há transmissão) — se
    exigir resposta, **Yes** (não há tráfego de dados do usuário).
  - "Do you provide a way for users to request that their data is deleted?" → desinstalar o app
    remove todos os dados locais.
- **Government apps:** **No.**
- **Financial features:** **No.**
- **Health apps:** **No.**

---

## E. Produto in-app (Monetize with Play → Products → In-app products)

> Só dá para criar/ativar depois de subir um AAB com a permissão de billing (já está no build).

- **Product ID:** `iseelife_desbloqueio_completo`  *(igual ao iOS; não pode mudar depois)*
- **Name:** `I See Life — completo`
- **Description:** `Desbloqueie a captura de fotos com um pagamento único, sem assinatura.`
- **Type:** produto gerenciado (managed product / não-consumível)
- **Preço:** **R$ 49,90** (mesmo alvo do iOS). Definir na criação do produto.
- **Status:** **Active** (ativar após criar).

## F. Testador de licença (para testar a compra de graça)

Play Console (nível da conta) → **Settings → License testing** → adicionar seu e-mail
(`alexandre@dablab.com.br` e/ou a conta do Google do seu aparelho de teste). Assim a compra do
`iseelife_desbloqueio_completo` roda como **teste** (sem cobrança real) no seu aparelho, mesmo em
produção.

---

## G. Release de produção (Production → Create new release)

- **App bundle:** subir `app/build/outputs/bundle/release/app-release.aab` (versionCode 1,
  versionName 1.0.0, assinado com a upload key).
- **Play App Signing:** aceitar (o Google gera/gerencia a chave de assinatura final; você sobe com
  a upload key). Recomendado.
- **Release name:** `1.0.0 (1)`
- **Release notes (pt-BR):**
  ```
  Primeira versão do I See Life, o app de fotografia de rua com filtro na hora do disparo. Seis receitas de cor, Modo Rua de um toque e Modo Pro com controle manual completo e prioridade de obturador. Disparo por toque, botões de volume, atalho e bloco de Configurações Rápidas. Sem assinatura, 100% offline.
  ```
- **Screenshots (pendente):** o Play exige pelo menos 2 screenshots de telefone (retrato). Opções:
  (a) reaproveitar/redimensionar os do iOS (`~/I See Life  app/appstore screenshots` ou `prints/`);
  (b) gerar novos no Claude Design; (c) capturar do app rodando. Me avise qual caminho que eu ajudo.

---

## Pendências suas
1. **Screenshots de telefone** (mín. 2, retrato) — ver seção G.
2. **Preço R$ 49,90** confirmado na criação do produto (seção E).
3. **Adicionar a seção "I See Life" no documento Notion** de privacidade (seção C).
4. **Countries/regions:** disponibilidade — sugiro todos os países (ou só Brasil, se preferir).
