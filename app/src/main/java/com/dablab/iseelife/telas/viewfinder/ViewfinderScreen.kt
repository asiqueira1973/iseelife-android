// ViewfinderScreen.kt — visor com preview filtrado ao vivo (épico 8.3). Hospeda a GlPreviewView
// (câmera → shader), pede a permissão de câmera, mostra a receita ativa (toque cicla as receitas)
// e o botão de lente (só quando há mais de uma — regra 11.1). O disparo, o Modo Pro e o toque
// para travar foco chegam nos épicos 8.4/8.5/8.6; aqui o foco é o preview WYSIWYG.

package com.dablab.iseelife.telas.viewfinder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import com.dablab.iseelife.App
import com.dablab.iseelife.BuildConfig
import com.dablab.iseelife.camera.CameraController
import com.dablab.iseelife.camera.GlPreviewView
import com.dablab.iseelife.camera.LensDiscovery
import com.dablab.iseelife.camera.PhotoSaver
import com.dablab.iseelife.dados.LenteCamera
import com.dablab.iseelife.dados.ModoCaptura
import com.dablab.iseelife.designsystem.Estilo
import com.dablab.iseelife.filtros.RecipeUniforms
import com.dablab.iseelife.telas.configuracoes.SettingsScreen
import com.dablab.iseelife.telas.paywall.PaywallScreen
import kotlinx.coroutines.launch

@Composable
fun ViewfinderScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as App
    val repo = app.repositorio
    val billing = app.billing
    val scope = rememberCoroutineScope()

    // Gate de paywall (épico 8.7): foto capturada fica pendente até a compra confirmar.
    var fotoPendente by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var mostrarPaywall by remember { mutableStateOf(false) }
    var mostrarConfig by remember { mutableStateOf(false) }
    var mostrarSeletor by remember { mutableStateOf(false) }

    // Estado de coordenação (câmera pronta + surface pronta + permissão).
    val previewView = remember { GlPreviewView(context) }
    val controller = remember { CameraController(context, lifecycleOwner) }
    val stState = remember { mutableStateOf<SurfaceTexture?>(null) }
    val providerReady = remember { mutableStateOf(false) }
    val boundState = remember { mutableStateOf(false) }
    val lensesState = remember { mutableStateOf<LensDiscovery?>(null) }
    val lensState = remember { mutableStateOf(LenteCamera.WIDE) }
    var focoPonto by remember { mutableStateOf<Offset?>(null) }
    var flash by remember { mutableStateOf(false) }
    var temPermissao by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Liga o Preview quando permissão + provider + surface estiverem prontos.
    val tentarLigar = remember {
        {
            val st = stState.value
            val lds = lensesState.value
            if (temPermissao && providerReady.value && st != null && lds != null) {
                controller.ligar(st, lds.seletor(lensState.value)) { w, h ->
                    previewView.definirTamanhoFrame(w, h)
                }
                boundState.value = true
            }
        }
    }

    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedida ->
        temPermissao = concedida
        if (concedida) tentarLigar()
    }

    // Dados: receitas + configuração (fluxos do Room).
    val recipes by repo.observarReceitas().collectAsState(initial = emptyList())
    val config by repo.observarConfiguracao().collectAsState(initial = null)
    val activeRecipe = remember(recipes, config) {
        recipes.firstOrNull { it.id == config?.activeRecipeId } ?: recipes.firstOrNull()
    }
    // Modo atual (Rua/Pro): inicia do padrão salvo, depois é alternado pelo usuário na sessão.
    var modo by remember { mutableStateOf(ModoCaptura.RUA) }
    var modoInicializado by remember { mutableStateOf(false) }
    LaunchedEffect(config) {
        if (!modoInicializado && config != null) {
            modo = config!!.defaultMode
            modoInicializado = true
        }
    }
    val forceMono = (config?.previewBwEnabled == true) && modo == ModoCaptura.RUA
    // Desbloqueado = flag persistida OU posse viva do billing (offline-friendly, regra 11.4).
    val desbloqueado = (config?.purchaseUnlocked == true) || billing.possui

    // Empurra os uniforms da receita ativa (com preview P&B) para o renderer.
    LaunchedEffect(activeRecipe, forceMono) {
        val r = activeRecipe ?: return@LaunchedEffect
        previewView.definirUniforms(RecipeUniforms.de(r, forceMono))
    }

    // Prioridade de obturador no Modo Rua (regra 11.7): liga no Rua, desliga no Pro.
    LaunchedEffect(modo, boundState.value, config?.streetShutterDenominator) {
        if (boundState.value) {
            controller.prioridadeObturador(
                ativar = modo == ModoCaptura.RUA,
                denominador = config?.streetShutterDenominator ?: 250,
            )
        }
    }

    // Compra confirmada (ou restaurada): persiste o desbloqueio e salva a foto pendente sem novo
    // toque, fechando o paywall (regra 11.4).
    LaunchedEffect(billing.possui) {
        if (billing.possui) {
            repo.obterConfiguracao()?.let { c ->
                if (!c.purchaseUnlocked) {
                    repo.atualizarConfiguracao(
                        c.copy(purchaseUnlocked = true, purchaseDate = System.currentTimeMillis())
                    )
                }
            }
            fotoPendente?.let { bmp ->
                PhotoSaver.salvar(context, bmp)
                fotoPendente = null
            }
            mostrarPaywall = false
        }
    }

    // Setup/teardown da câmera e da surface.
    DisposableEffect(Unit) {
        previewView.aoSurfaceTexturePronta = { st ->
            stState.value = st
            tentarLigar()
        }
        controller.inicializar { ld ->
            lensesState.value = ld
            lensState.value = ld.lentes.first().tipo
            providerReady.value = true
            tentarLigar()
        }
        if (!temPermissao) pedirPermissao.launch(Manifest.permission.CAMERA)
        onDispose {
            controller.desligar()
            previewView.liberarGl()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor)) {
        if (temPermissao) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            // Camada transparente de toque: no Modo Rua, toque trava o foco no ponto (épico 8.4).
            // No Pro o foco é manual (slider), então o toque não interfere.
            Box(
                modifier = Modifier.fillMaxSize().pointerInput(modo) {
                    detectTapGestures { off ->
                        if (modo == ModoCaptura.RUA) {
                            controller.focarNoPonto(off.x, off.y, size.width, size.height)
                            focoPonto = off
                        }
                    }
                }
            )

            // Indicador de foco âmbar; some após ~1s.
            focoPonto?.let { ponto -> IndicadorFoco(ponto) { focoPonto = null } }

            // Botão de lente (só com mais de uma — regra 11.1), topo direito.
            lensesState.value?.let { lds ->
                if (lds.temMultiplas()) {
                    Chip(
                        texto = if (lensState.value == LenteCamera.ULTRA_WIDE) "0.5×" else "1×",
                        modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(16.dp),
                    ) {
                        lensState.value =
                            if (lensState.value == LenteCamera.WIDE) LenteCamera.ULTRA_WIDE else LenteCamera.WIDE
                        tentarLigar()
                    }
                }
            }

            // Alternância Rua/Pro + engrenagem de Configurações (topo esquerdo).
            Row(
                modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Chip(
                    texto = if (modo == ModoCaptura.PRO) "PRO" else "RUA",
                    destaque = modo == ModoCaptura.PRO,
                ) {
                    modo = if (modo == ModoCaptura.RUA) ModoCaptura.PRO else ModoCaptura.RUA
                }
                Chip(texto = "⚙︎") { mostrarConfig = true }
            }

            // Rodapé: painel do Pro (quando no Pro) + receita ativa (toque cicla).
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .systemBarsPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (modo == ModoCaptura.PRO) {
                    controller.faixasManuais?.let { faixas ->
                        ProControlsPanel(
                            faixas = faixas,
                            aoISO = { controller.proISO(it) },
                            aoObturador = { controller.proObturador(it) },
                            aoEV = { controller.proEV(it) },
                            aoFoco = { controller.proFoco(it) },
                            aoWhiteBalance = { t, ti -> controller.proWhiteBalance(t, ti) },
                            aoAuto = { controller.proAuto() },
                        )
                    }
                }
                Chip(texto = activeRecipe?.displayName ?: "—", destaque = true) {
                    mostrarSeletor = true
                }

                // Botão de disparo — dispara no TOQUE (down), reduzindo tremor (paridade iOS).
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .background(Estilo.TextoPrimario, CircleShape)
                        .border(4.dp, Estilo.Painel, CircleShape)
                        .pointerInput(desbloqueado) {
                            detectTapGestures(onPress = {
                                vibrar(context)
                                flash = true
                                previewView.capturar { bmp ->
                                    // Gate (regra 11.4): desbloqueado salva; senão segura a foto
                                    // pendente e abre o paywall (não salva ainda).
                                    if (desbloqueado) {
                                        scope.launch { PhotoSaver.salvar(context, bmp) }
                                    } else {
                                        fotoPendente = bmp
                                        mostrarPaywall = true
                                    }
                                }
                            })
                        }
                )
            }

            // Flash de disparo: clarão branco rápido sobre o visor.
            if (flash) {
                LaunchedEffect(Unit) { delay(120); flash = false }
                Box(modifier = Modifier.fillMaxSize().background(Estilo.TextoPrimario.copy(alpha = 0.7f)))
            }

            // Paywall (gate no 1º disparo, regra 11.4).
            if (mostrarPaywall) {
                PaywallScreen(
                    precoFormatado = billing.precoFormatado,
                    aoComprar = { (context as? Activity)?.let { billing.comprar(it) } },
                    aoRestaurar = { billing.restaurar() },
                    aoFechar = { mostrarPaywall = false; fotoPendente = null },
                    aoSimular = if (BuildConfig.DEBUG) ({ billing.simularCompra() }) else null,
                )
            }

            // Seletor de receitas (épico 8.9) — toque no nome da receita.
            if (mostrarSeletor) {
                RecipeSelectorSheet(
                    receitas = recipes,
                    ativaId = activeRecipe?.id,
                    aoSelecionar = { id ->
                        scope.launch {
                            repo.obterConfiguracao()?.let { repo.atualizarConfiguracao(it.copy(activeRecipeId = id)) }
                        }
                        mostrarSeletor = false
                    },
                    aoFechar = { mostrarSeletor = false },
                )
            }

            // Configurações (épico 8.9) — engrenagem.
            if (mostrarConfig) {
                config?.let { c ->
                    SettingsScreen(
                        config = c,
                        aoSalvar = { novo -> scope.launch { repo.atualizarConfiguracao(novo) } },
                        comprado = desbloqueado,
                        aoRestaurar = { billing.restaurar() },
                        aoFechar = { mostrarConfig = false },
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Precisamos da câmera para o visor.",
                    color = Estilo.TextoPrimario,
                )
                Chip(texto = "Permitir câmera", destaque = true) {
                    pedirPermissao.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

// Vibração curta de feedback do disparo (épico 8.6). Compatível com API 26+ (VibrationEffect).
private fun vibrar(context: Context) {
    val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vib?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
}

// Indicador de foco: quadrado âmbar centrado no ponto tocado, some após ~1s (épico 8.4).
@Composable
private fun IndicadorFoco(ponto: Offset, aoFim: () -> Unit) {
    val density = LocalDensity.current
    val ladoPx = with(density) { 72.dp.toPx() }
    LaunchedEffect(ponto) {
        delay(1000)
        aoFim()
    }
    Box(
        modifier = Modifier
            .offset { IntOffset((ponto.x - ladoPx / 2).toInt(), (ponto.y - ladoPx / 2).toInt()) }
            .size(72.dp)
            .border(1.5.dp, Estilo.Accent, RoundedCornerShape(4.dp))
    )
}

@Composable
private fun Chip(
    texto: String,
    modifier: Modifier = Modifier,
    destaque: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = texto,
        color = if (destaque) Estilo.Accent else Estilo.TextoPrimario,
        fontWeight = if (destaque) FontWeight.SemiBold else FontWeight.Normal,
        fontSize = 16.sp,
        modifier = modifier
            .background(Estilo.Painel, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
