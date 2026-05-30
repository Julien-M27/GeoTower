package fr.geotower.utils

import android.app.Notification
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import fr.geotower.R
import kotlin.math.roundToInt

object NotificationIconResources {
    private const val PREFS_NAME = "GeoTowerPrefs"
    private const val LARGE_ICON_DP = 64f

    @Volatile
    private var cachedKey: LargeIconCacheKey? = null

    @Volatile
    private var cachedBitmap: Bitmap? = null

    @DrawableRes
    fun smallIconRes(context: Context): Int {
        return when (AppIconManager.getActiveIconIndex(context.applicationContext)) {
            1 -> R.drawable.ic_launcher_georadio_monochrome
            2 -> R.drawable.ic_launcher_funny_monochrome
            else -> R.drawable.ic_launcher_geotower_monochrome
        }
    }

    fun applyTo(
        builder: NotificationCompat.Builder,
        context: Context,
        includeLargeIcon: Boolean = false
    ): NotificationCompat.Builder {
        builder.setSmallIcon(smallIconRes(context))
        if (includeLargeIcon) {
            largeIconBitmap(context)?.let(builder::setLargeIcon)
        }
        return builder
    }

    fun applyTo(
        builder: Notification.Builder,
        context: Context,
        includeLargeIcon: Boolean = false
    ): Notification.Builder {
        builder.setSmallIcon(smallIconRes(context))
        if (includeLargeIcon) {
            largeIconBitmap(context)?.let { bitmap ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.setLargeIcon(Icon.createWithBitmap(bitmap))
                } else {
                    @Suppress("DEPRECATION")
                    builder.setLargeIcon(bitmap)
                }
            }
        }
        return builder
    }

    private fun largeIconBitmap(context: Context): Bitmap? {
        val appContext = context.applicationContext
        val drawableRes = largeIconDrawableRes(appContext)
        val densityDpi = appContext.resources.displayMetrics.densityDpi
        val key = LargeIconCacheKey(drawableRes, densityDpi)

        cachedBitmap?.takeIf { cachedKey == key && !it.isRecycled }?.let { return it }

        return renderDrawable(appContext, drawableRes)?.also { bitmap ->
            cachedBitmap = bitmap
            cachedKey = key
        }
    }

    @DrawableRes
    private fun largeIconDrawableRes(context: Context): Int {
        val activeIconRes = when (AppIconManager.getActiveIconIndex(context)) {
            1 -> R.mipmap.ic_launcher_georadio
            2 -> R.mipmap.ic_launcher_funny
            else -> R.mipmap.ic_launcher_geotower
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val drawingChoice = AppLogoDrawingResources.normalize(
            prefs.getString(AppLogoDrawingResources.PREF_KEY, AppLogoDrawingResources.AUTO)
        )
        return AppLogoDrawingResources.resolve(drawingChoice, activeIconRes, context.isNightMode)
    }

    private fun renderDrawable(context: Context, @DrawableRes drawableRes: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableRes)?.mutate() ?: return null
        val size = (LARGE_ICON_DP * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private val Context.isNightMode: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private data class LargeIconCacheKey(
        @DrawableRes val drawableRes: Int,
        val densityDpi: Int
    )
}
