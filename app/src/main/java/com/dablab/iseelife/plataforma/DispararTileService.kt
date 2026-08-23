// DispararTileService.kt — bloco de Configurações Rápidas "I See Life" (épico 8.10). É o
// equivalente Android confiável ao Control da Tela de Bloqueio do iOS 18 (que bugou no iOS):
// tocar o bloco abre o app já no visor do Modo Rua ("pronto para disparar", princípio 0.1).

package com.dablab.iseelife.plataforma

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.dablab.iseelife.MainActivity

class DispararTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("abrir_rua", true)
        }
        // Android 14+ exige PendingIntent em startActivityAndCollapse.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
