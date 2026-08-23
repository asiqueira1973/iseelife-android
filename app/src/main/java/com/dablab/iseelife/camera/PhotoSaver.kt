// PhotoSaver.kt — salva o Bitmap filtrado como JPEG na galeria (épico 8.6 / regra 11.3). Não há
// cópia sem filtro: só o JPEG final (com a receita) é gravado. Usa MediaStore em Pictures/I See Life.
// Android 10+ (Q) não precisa de permissão de storage; Android 9 e anterior grava no diretório
// público (permissão WRITE_EXTERNAL_STORAGE declarada com maxSdkVersion=28).

package com.dablab.iseelife.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoSaver {

    private const val PASTA = "I See Life"

    // Salva o bitmap como JPEG e devolve true em sucesso. Roda em IO.
    suspend fun salvar(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val nome = "ISeeLife_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                salvarMediaStore(context, bitmap, nome)
            } else {
                salvarLegado(context, bitmap, nome)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // Android 10+ : insere via MediaStore com RELATIVE_PATH (sem permissão de storage).
    private fun salvarMediaStore(context: Context, bitmap: Bitmap, nome: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nome)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + PASTA)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert falhou")
        resolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        // Publica o arquivo (tira o pendente).
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    // Android 9 e anterior: grava no diretório público de imagens e registra no MediaStore.
    @Suppress("DEPRECATION")
    private fun salvarLegado(context: Context, bitmap: Bitmap, nome: String) {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), PASTA)
        if (!dir.exists()) dir.mkdirs()
        val arquivo = File(dir, nome)
        FileOutputStream(arquivo).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        // Registra no MediaStore para aparecer na galeria.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nome)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, arquivo.absolutePath)
        }
        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
