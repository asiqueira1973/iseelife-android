// RootView.kt — roteamento inicial do app (épico 8.8, equivalente ao RootView do iOS). Decide entre
// onboarding, visor e tela de permissão negada, a partir de onboarding_concluido e da permissão de
// câmera. Rechecagem em ON_RESUME cobre o retorno das configurações do sistema.

package com.dablab.iseelife.telas

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dablab.iseelife.App
import com.dablab.iseelife.designsystem.Estilo
import com.dablab.iseelife.telas.onboarding.OnboardingScreen
import com.dablab.iseelife.telas.permissoes.PermissionDeniedScreen
import com.dablab.iseelife.telas.viewfinder.ViewfinderScreen
import kotlinx.coroutines.launch

@Composable
fun RootView() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val repo = app.repositorio
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val config by repo.observarConfiguracao().collectAsState(initial = null)

    fun temCamera() =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var temPermissao by remember { mutableStateOf(temCamera()) }

    // Rechecagem ao voltar do sistema (ex.: usuário concedeu nas Configurações).
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, evento ->
            if (evento == Lifecycle.Event.ON_RESUME) temPermissao = temCamera()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val pedirPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { temPermissao = it }

    Box(modifier = Modifier.fillMaxSize().background(Estilo.FundoVisor)) {
        val c = config
        when {
            c == null -> Unit // carregando (fundo preto)
            !c.onboardingDone -> OnboardingScreen(aoConcluir = {
                scope.launch {
                    repo.obterConfiguracao()?.let { repo.atualizarConfiguracao(it.copy(onboardingDone = true)) }
                }
                temPermissao = temCamera()
            })
            temPermissao -> ViewfinderScreen()
            else -> PermissionDeniedScreen(
                aoAbrirConfig = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                },
                aoTentar = { pedirPermissao.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}
