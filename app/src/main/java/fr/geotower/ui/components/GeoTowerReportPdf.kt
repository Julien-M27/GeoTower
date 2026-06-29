package fr.geotower.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import fr.geotower.utils.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

fun shareGeoTowerReportPdf(
    context: Context,
    bitmaps: List<Bitmap>,
    fileName: String,
    chooserTitle: String
) {
    val usable = bitmaps.filter { it.width > 0 && it.height > 0 }
    if (usable.isEmpty()) return

    try {
        val imagesDir = File(context.cacheDir, "images")
        imagesDir.mkdirs()
        val file = File(imagesDir, fileName)
        FileOutputStream(file).use { output ->
            writeGeoTowerReportPdf(usable, output)
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: Exception) {
        AppLogger.w("GeoTowerReportPdf", "Report PDF generation failed", e)
    }
}

fun downloadGeoTowerReportPdf(
    context: Context,
    bitmaps: List<Bitmap>,
    fileName: String
): Boolean {
    val usable = bitmaps.filter { it.width > 0 && it.height > 0 }
    if (usable.isEmpty()) return false

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/GeoTower")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        try {
            resolver.openOutputStream(uri)?.use { output ->
                writeGeoTowerReportPdf(usable, output)
            } ?: return false
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null
            )
            true
        } catch (e: Exception) {
            AppLogger.w("GeoTowerReportPdf", "Report PDF download failed", e)
            runCatching { resolver.delete(uri, null, null) }
            false
        }
    } else {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val geoTowerDir = File(downloadsDir, "GeoTower")
            geoTowerDir.mkdirs()
            val file = File(geoTowerDir, fileName)
            FileOutputStream(file).use { output ->
                writeGeoTowerReportPdf(usable, output)
            }
            true
        } catch (e: Exception) {
            AppLogger.w("GeoTowerReportPdf", "Report PDF download failed", e)
            false
        }
    }
}

private fun writeGeoTowerReportPdf(
    usable: List<Bitmap>,
    output: OutputStream
) {
    val pageW = 595
    val pageH = 842
    val margin = 18f
    val pagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    val document = PdfDocument()
    try {
        var pageNumber = 1
        usable.forEach { bmp ->
            val availW = pageW - 2 * margin
            val availH = pageH - 2 * margin
            val scale = availW / bmp.width.toFloat()
            val sourcePageHeight = (availH / scale).toInt().coerceAtLeast(1)
            var sourceTop = 0

            while (sourceTop < bmp.height) {
                val sourceBottom = minOf(bmp.height, sourceTop + sourcePageHeight)
                val sourceHeight = sourceBottom - sourceTop
                val drawnHeight = sourceHeight * scale
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
        }

        document.writeTo(output)
    } finally {
        document.close()
    }
}
