// CameraController.kt — liga o CameraX ao preview GL (épico 8.3). Obtém o ProcessCameraProvider,
// descobre as lentes (LensDiscovery) e vincula um use case Preview cuja saída é a SurfaceTexture
// da câmera (textura externa OES) consumida pelo shader. Trocar de lente re-vincula com outro
// CameraSelector. O binding respeita o ciclo de vida (bindToLifecycle).

package com.dablab.iseelife.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner

class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private var provider: ProcessCameraProvider? = null
    private var preview: Preview? = null

    var lentes: LensDiscovery? = null
        private set

    // Inicializa o provider e a descoberta de lentes; chama `onReady` na main thread.
    fun inicializar(onReady: (LensDiscovery) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = future.get()
            provider = p
            val ld = LensDiscovery(p)
            lentes = ld
            onReady(ld)
        }, ContextCompat.getMainExecutor(context))
    }

    // Vincula o Preview à `surfaceTexture` com a lente escolhida. `aoTamanhoFrame` informa a
    // resolução real do frame (para o renderer calcular o aspect-fill).
    fun ligar(
        surfaceTexture: SurfaceTexture,
        seletor: CameraSelector,
        aoTamanhoFrame: (Int, Int) -> Unit,
    ) {
        val p = provider ?: return
        val exec = ContextCompat.getMainExecutor(context)
        val prev = Preview.Builder().build().also { preview = it }
        prev.setSurfaceProvider(exec) { request ->
            val res = request.resolution
            surfaceTexture.setDefaultBufferSize(res.width, res.height)
            aoTamanhoFrame(res.width, res.height)
            val surface = Surface(surfaceTexture)
            request.provideSurface(surface, exec) { surface.release() }
        }
        p.unbindAll()
        p.bindToLifecycle(lifecycleOwner, seletor, prev)
    }

    fun desligar() {
        provider?.unbindAll()
    }
}
