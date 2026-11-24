package fr.geotower.ui.screens.speedtests

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import fr.geotower.data.speedtest.SpeedtestEntryEntity
import fr.geotower.data.speedtest.SpeedtestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.FolderOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

/**
 * Calque OsmDroid affichant les miniatures des speedtests géolocalisés sous forme de petites
 * cartes cliquables sur la carte.
 */
class SpeedtestMapOverlay : FolderOverlay() {

    private val iconCache = HashMap<String, BitmapDrawable>()

    suspend fun updateEntries(
        context: Context,
        mapView: MapView,
        entries: List<SpeedtestEntryEntity>,
        onClickEntry: (SpeedtestEntryEntity) -> Unit
    ) {
        val geolocated = entries.filter { entry ->
            val lat = entry.latitude
            val lon = entry.longitude
            lat != null && lon != null && lat.isFinite() && lon.isFinite()
        }

        val iconsByEntryId = withContext(Dispatchers.IO) {
            geolocated.associate { entry ->
                val cacheKey = "${entry.id}|${entry.imageFileName}"
                val cached = iconCache[cacheKey]
                val drawable = cached ?: buildSpeedtestMarkerDrawable(context, entry).also {
                    iconCache[cacheKey] = it
                }
                entry.id to drawable
            }
        }

        items.clear()
        for (entry in geolocated) {
            val lat = entry.latitude ?: continue
            val lon = entry.longitude ?: continue
            val drawable = iconsByEntryId[entry.id] ?: continue

            val marker = Marker(mapView).apply {
                position = GeoPoint(lat, lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = drawable
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ ->
                    onClickEntry(entry)
                    true
                }
            }
            add(marker)
        }
        mapView.invalidate()
    }

    private fun buildSpeedtestMarkerDrawable(
        context: Context,
        entry: SpeedtestEntryEntity
    ): BitmapDrawable {
        val density = context.resources.displayMetrics.density
        fun dp(value: Float): Float = value * density

        val cardW = dp(56f).toInt()
        val cardH = dp(56f).toInt()
        val pointerH = dp(7f).toInt()
        val totalH = cardH + pointerH
        val pad = dp(4f)
        val corner = dp(10f)

        val output = Bitmap.createBitmap(cardW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Fond carte blanc + bordure légère
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 30, 41, 59)
            style = Paint.Style.STROKE
            strokeWidth = dp(1.2f)
        }

        val cardRect = RectF(dp(1f), dp(1f), cardW - dp(1f), cardH - dp(1f))
        canvas.drawRoundRect(cardRect, corner, corner, bgPaint)
        canvas.drawRoundRect(cardRect, corner, corner, borderPaint)

        // Petite pointe en bas de la case
        val pointerPath = Path().apply {
            val cx = cardW / 2f
            moveTo(cx - dp(6f), cardH - dp(1.5f))
            lineTo(cx, totalH - dp(1f))
            lineTo(cx + dp(6f), cardH - dp(1.5f))
            close()
        }
        canvas.drawPath(pointerPath, bgPaint)

        // Miniature carrée rognée de la capture
        val thumbSize = cardW - pad * 2f
        val thumbRect = RectF(pad, pad, cardW - pad, cardH - pad)
        val imageFile = SpeedtestRepository.imageFile(context, entry.imageFileName)
        val thumbBitmap = decodeSampledBitmap(imageFile.absolutePath, thumbSize.toInt().coerceAtLeast(64))

        if (thumbBitmap != null) {
            val saveCount = canvas.save()
            val clipPath = Path().apply {
                addRoundRect(thumbRect, dp(6f), dp(6f), Path.Direction.CW)
            }
            canvas.clipPath(clipPath)

            // Center crop
            val srcSize = minOf(thumbBitmap.width, thumbBitmap.height)
            val srcX = (thumbBitmap.width - srcSize) / 2
            val srcY = (thumbBitmap.height - srcSize) / 2
            val srcRect = Rect(srcX, srcY, srcX + srcSize, srcY + srcSize)
            canvas.drawBitmap(thumbBitmap, srcRect, thumbRect, Paint(Paint.FILTER_BITMAP_FLAG))
            canvas.restoreToCount(saveCount)
            thumbBitmap.recycle()
        } else {
            val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
            }
            canvas.drawRoundRect(thumbRect, dp(6f), dp(6f), fallbackPaint)
        }

        return BitmapDrawable(context.resources, output)
    }

    private fun decodeSampledBitmap(path: String, targetPx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > targetPx * 2 &&
                bounds.outHeight / sampleSize > targetPx * 2
            ) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, opts)
        }.getOrNull()
    }
}
