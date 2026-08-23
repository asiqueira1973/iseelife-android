// RecipeContactSheetTest.kt — GERADOR (não é asserção): renderiza uma imagem de teste pelas 6
// receitas do catálogo usando o pipeline GL real e salva os PNGs em getExternalFilesDir("recipes").
// Serve para validação visual das cores (regra 11.2/8.2) fora da câmera — os arquivos são puxados
// via adb e enviados ao Alexandre. Roda como teste instrumentado (precisa de GPU).

package com.dablab.iseelife.filtros

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.dablab.iseelife.dados.RecipeSeed
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeContactSheetTest {

    // Imagem de teste: barras de cor puras, rampa de cinza, tons de pele e um degradê de céu —
    // suficiente para julgar WB (quente/frio), saturação, curva de tom, mono e grão.
    private fun imagemTeste(w: Int = 480, h: Int = 720): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint()

        // Faixa 1: barras de cor puras + branco/preto.
        val cores = intArrayOf(
            Color.rgb(220, 40, 40), Color.rgb(40, 180, 60), Color.rgb(40, 90, 220),
            Color.rgb(230, 200, 40), Color.rgb(210, 80, 200), Color.WHITE, Color.rgb(20, 20, 20),
        )
        val bw = w / cores.size
        for (i in cores.indices) {
            p.color = cores[i]
            c.drawRect((i * bw).toFloat(), 0f, ((i + 1) * bw).toFloat(), h * 0.28f, p)
        }

        // Faixa 2: rampa de cinza (preto → branco) para a curva de tom/contraste.
        for (x in 0 until w) {
            val g = (x * 255 / w)
            p.color = Color.rgb(g, g, g)
            c.drawRect(x.toFloat(), h * 0.28f, (x + 1).toFloat(), h * 0.46f, p)
        }

        // Faixa 3: tons de pele (claro→escuro) para julgar WB/saturação em pele.
        val peles = intArrayOf(
            Color.rgb(255, 224, 196), Color.rgb(241, 194, 156), Color.rgb(224, 172, 138),
            Color.rgb(198, 134, 100), Color.rgb(141, 85, 61), Color.rgb(90, 56, 42),
        )
        val pw = w / peles.size
        for (i in peles.indices) {
            p.color = peles[i]
            c.drawRect((i * pw).toFloat(), h * 0.46f, ((i + 1) * pw).toFloat(), h * 0.68f, p)
        }

        // Faixa 4: degradê de "céu" (azul claro → azul) para WB e color chrome.
        for (y in (h * 0.68f).toInt() until h) {
            val t = (y - h * 0.68f) / (h - h * 0.68f)
            val r = (170 * (1 - t) + 60 * t).toInt()
            val g = (200 * (1 - t) + 120 * t).toInt()
            val b = (235 * (1 - t) + 210 * t).toInt()
            p.color = Color.rgb(r, g, b)
            c.drawRect(0f, y.toFloat(), w.toFloat(), (y + 1).toFloat(), p)
        }
        return bmp
    }

    // Salva no MediaStore (Pictures/ISeeLifeTest) — sobrevive à desinstalação do APK de teste que o
    // connectedAndroidTest faz ao final, permitindo o adb pull de /sdcard/Pictures/ISeeLifeTest.
    private fun salvar(ctx: Context, bmp: Bitmap, nome: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, nome)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ISeeLifeTest")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert falhou para $nome")
        ctx.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun gerarContactSheet() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val original = imagemTeste()
        salvar(ctx, original, "00_original.png")

        val renderer = OffscreenRecipeRenderer()
        try {
            for (r in RecipeSeed.receitasPadrao()) {
                val out = renderer.aplicar(original, r, forceMonochrome = false, seed = 7f)
                val nome = "%02d_%s.png".format(r.displayOrder, r.displayName.replace(" ", "_"))
                salvar(ctx, out, nome)
            }
        } finally {
            renderer.liberar()
        }
    }
}
