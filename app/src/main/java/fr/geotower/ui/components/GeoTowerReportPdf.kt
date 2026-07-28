package fr.geotower.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import fr.geotower.utils.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Pages d'un rapport PDF. Les gros rapports (un support multi-opérateurs peut dépasser dix pages)
 * passent par [Spooled] : chaque page est écrite en PNG temporaire dès son rendu puis relue une par
 * une à l'écriture, au lieu de garder tous les bitmaps plein format en mémoire.
 */
sealed interface GeoTowerReportPdfPages {
    data class InMemory(val bitmaps: List<Bitmap>) : GeoTowerReportPdfPages
    data class Spooled(val files: List<File>) : GeoTowerReportPdfPages

    val isEmpty: Boolean
        get() = when (this) {
            is InMemory -> bitmaps.none { it.width > 0 && it.height > 0 }
            is Spooled -> files.none { it.length() > 0L }
        }
}

fun shareGeoTowerReportPdf(
    context: Context,
    bitmaps: List<Bitmap>,
    fileName: String,
    chooserTitle: String
) = shareGeoTowerReportPdf(context, GeoTowerReportPdfPages.InMemory(bitmaps), fileName, chooserTitle)

fun shareGeoTowerReportPdf(
    context: Context,
    pages: GeoTowerReportPdfPages,
    fileName: String,
    chooserTitle: String
) {
    if (pages.isEmpty) return

    try {
        val imagesDir = File(context.cacheDir, "images")
        imagesDir.mkdirs()
        val file = File(imagesDir, fileName)
        FileOutputStream(file).use { output ->
            writeGeoTowerReportPdf(pages, output)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // FLAG_ACTIVITY_NEW_TASK requis : contexte localisé (LocaleProvider), pas une Activity → sinon crash OnePlus.
        context.startActivity(Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLogger.w("GeoTowerReportPdf", "Report PDF generation failed", e)
    }
}

fun downloadGeoTowerReportPdf(
    context: Context,
    bitmaps: List<Bitmap>,
    fileName: String
): Uri? = downloadGeoTowerReportPdf(context, GeoTowerReportPdfPages.InMemory(bitmaps), fileName)

/**
 * Enregistre le rapport dans Téléchargements/GeoTower et renvoie l'URI du fichier créé, prête à
 * être ouverte par une autre application (voir [PdfDownloadNotice]), ou `null` si l'écriture échoue.
 */
fun downloadGeoTowerReportPdf(
    context: Context,
    pages: GeoTowerReportPdfPages,
    fileName: String
): Uri? {
    if (pages.isEmpty) return null

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GeoTower")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            val output = resolver.openOutputStream(uri) ?: error("openOutputStream returned null")
            output.use { writeGeoTowerReportPdf(pages, it) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            uri
        } catch (e: Exception) {
            AppLogger.w("GeoTowerReportPdf", "Report PDF download failed", e)
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    } else {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val geoTowerDir = File(downloadsDir, "GeoTower")
            geoTowerDir.mkdirs()
            val file = File(geoTowerDir, fileName)
            FileOutputStream(file).use { output ->
                writeGeoTowerReportPdf(pages, output)
            }
            // Avant Android 10 le fichier vit sur le stockage partagé : seul le FileProvider donne
            // une URI ouvrable par une autre app (file:// est refusé depuis Android 7).
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            AppLogger.w("GeoTowerReportPdf", "Report PDF download failed", e)
            null
        }
    }
}

private fun writeGeoTowerReportPdf(
    pages: GeoTowerReportPdfPages,
    output: OutputStream
) {
    val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val document = PdfDocument()
    var pageNumber = 1
    try {
        when (pages) {
            is GeoTowerReportPdfPages.InMemory -> {
                pages.bitmaps
                    .filter { it.width > 0 && it.height > 0 }
                    .forEach { bmp -> pageNumber = drawReportPage(document, bmp, pagePaint, pageNumber) }
            }
            is GeoTowerReportPdfPages.Spooled -> {
                pages.files.forEach { file ->
                    // Décodage juste à temps : le bitmap reste immuable, PdfDocument partage ses pixels.
                    val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return@forEach
                    pageNumber = drawReportPage(document, bmp, pagePaint, pageNumber)
                }
            }
        }

        document.writeTo(output)
    } finally {
        document.close()
    }
}

/** Dessine un bitmap de page, en le découpant si sa hauteur dépasse une page A4. */
private fun drawReportPage(
    document: PdfDocument,
    bmp: Bitmap,
    pagePaint: Paint,
    firstPageNumber: Int
): Int {
    val pageW = 595
    val pageH = 842
    val margin = 18f
    val availW = pageW - 2 * margin
    val availH = pageH - 2 * margin
    val scale = availW / bmp.width.toFloat()
    val sourcePageHeight = (availH / scale).toInt().coerceAtLeast(1)
    var pageNumber = firstPageNumber
    var sourceTop = 0

    while (sourceTop < bmp.height) {
        val sourceBottom = minOf(bmp.height, sourceTop + sourcePageHeight)
        val drawnHeight = (sourceBottom - sourceTop) * scale
        val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber++).create()
        val page = document.startPage(info)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            bmp,
            Rect(0, sourceTop, bmp.width, sourceBottom),
            RectF(margin, margin, pageW - margin, margin + drawnHeight),
            pagePaint
        )
        document.finishPage(page)
        sourceTop = sourceBottom
    }
    return pageNumber
}
