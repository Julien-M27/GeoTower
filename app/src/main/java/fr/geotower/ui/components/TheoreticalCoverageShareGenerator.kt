package fr.geotower.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Compose une image partageable : bandeau (titre + site + paramètres) au-dessus d'une capture de la
 * mini-carte (carte + enveloppe de couverture), puis un pied avec le disclaimer et la source.
 * Autonome (aucune dépendance fragile) — la capture de la MapView est faite côté écran.
 */
object TheoreticalCoverageShareGenerator {
    fun create(
        title: String,
        subtitle: String,
        paramsLine: String,
        disclaimer: String,
        source: String,
        mapBitmap: Bitmap,
        forceDark: Boolean
    ): Bitmap {
        val pad = 36
        val width = maxOf(mapBitmap.width, 920)
        val headerH = 184
        val footerH = 250
        val mapX = (width - mapBitmap.width) / 2

        val bg = if (forceDark) Color.rgb(16, 17, 20) else Color.rgb(247, 248, 250)
        val fg = if (forceDark) Color.WHITE else Color.rgb(20, 22, 26)
        val muted = if (forceDark) Color.rgb(170, 175, 185) else Color.rgb(95, 100, 110)

        val output = Bitmap.createBitmap(width, headerH + mapBitmap.height + footerH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(bg)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg; textSize = 48f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 32f }
        val paramsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fg; textSize = 30f }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = muted; textSize = 26f }

        canvas.drawText(title, pad.toFloat(), 62f, titlePaint)
        canvas.drawText(subtitle, pad.toFloat(), 108f, subtitlePaint)
        canvas.drawText(paramsLine, pad.toFloat(), 152f, paramsPaint)

        canvas.drawBitmap(mapBitmap, mapX.toFloat(), headerH.toFloat(), null)

        var y = headerH + mapBitmap.height + 46
        for (line in wrap(disclaimer, smallPaint, width - 2 * pad)) {
            canvas.drawText(line, pad.toFloat(), y.toFloat(), smallPaint)
            y += 34
        }
        y += 10
        canvas.drawText(source, pad.toFloat(), y.toFloat(), smallPaint)

        return output
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
