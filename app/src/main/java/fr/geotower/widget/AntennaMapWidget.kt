package fr.geotower.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import fr.geotower.MainActivity
import fr.geotower.R
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppUiMode
import java.io.File

class AntennaMapWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("GeoTowerPrefs", Context.MODE_PRIVATE)
        val lastUpdate = prefs.getString("widget_last_update", "--:--") ?: "--:--"
        val fallbackImagePath = prefs.getString(PREF_WIDGET_MAP_IMAGE_PATH, null)
        val wideImagePath = prefs.getString(PREF_WIDGET_MAP_IMAGE_WIDE_PATH, fallbackImagePath)
        val squareImagePath = prefs.getString(PREF_WIDGET_MAP_IMAGE_SQUARE_PATH, wideImagePath)
        val wideExpandedImagePath = prefs.getString(PREF_WIDGET_MAP_IMAGE_WIDE_EXPANDED_PATH, wideImagePath)
        val squareExpandedImagePath = prefs.getString(PREF_WIDGET_MAP_IMAGE_SQUARE_EXPANDED_PATH, squareImagePath)
        val centerLat = prefs.getFloat(PREF_WIDGET_MAP_CENTER_LAT, Float.NaN)
        val centerLon = prefs.getFloat(PREF_WIDGET_MAP_CENTER_LON, Float.NaN)
        val isOled = prefs.getBoolean("oled_mode", false)
        val useOneUi = AppUiMode.fromStorageKey(
            prefs.getString(AppConfig.PREF_UI_MODE, AppUiMode.Auto.storageKey)
        ).usesOneUi()

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        provideContent {
            val size = LocalSize.current
            val isShortWidget = size.height < 145.dp
            val isSquareishWidget = size.width.value <= size.height.value * 1.25f
            val isExpandedWidget = size.width >= 300.dp || size.height >= 240.dp
            val showHeader = !isSquareishWidget || size.height >= 175.dp
            val horizontalPadding = if (size.width < 250.dp) 6.dp else 12.dp
            val mapImagePath = when {
                isSquareishWidget && isExpandedWidget -> {
                    firstExistingPath(squareExpandedImagePath, squareImagePath, wideExpandedImagePath, wideImagePath)
                }
                isSquareishWidget -> {
                    firstExistingPath(squareImagePath, squareExpandedImagePath, wideImagePath, wideExpandedImagePath)
                }
                isExpandedWidget -> {
                    firstExistingPath(wideExpandedImagePath, wideImagePath, squareExpandedImagePath, squareImagePath)
                }
                else -> {
                    firstExistingPath(wideImagePath, wideExpandedImagePath, squareImagePath, squareExpandedImagePath)
                }
            }
            val mapBitmap = mapImagePath?.let { BitmapFactory.decodeFile(it) }

            GlanceTheme {
                val mainBgColor = if (isOled) {
                    ColorProvider(day = Color(0xFFF3F3F3), night = Color.Black)
                } else {
                    GlanceTheme.colors.background
                }
                val cardBgColor = if (isOled) {
                    ColorProvider(day = Color.White, night = Color(0xFF121212))
                } else {
                    GlanceTheme.colors.surface
                }

                val mapIntent = Intent(context, MainActivity::class.java).apply {
                    action = "ACTION_WIDGET_MAP"
                    putExtra("widget_dest", "map")
                    if (!centerLat.isNaN() && !centerLon.isNaN()) {
                        putExtra("widget_lat", centerLat.toDouble())
                        putExtra("widget_lon", centerLon.toDouble())
                    }
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }

                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(mainBgColor)
                        .padding(horizontalPadding)
                        .clickable(actionStartActivity(mapIntent))
                ) {
                    if (showHeader) {
                        HeaderRow(
                            title = context.getString(R.string.widget_map_title),
                            updatedAt = context.getString(R.string.widget_updated_at, lastUpdate),
                            showUpdatedAt = !isShortWidget
                        )

                        Spacer(modifier = GlanceModifier.height(if (isShortWidget) 4.dp else 8.dp))
                    }

                    if (!hasBackgroundLocation) {
                        PermissionCard(
                            title = context.getString(R.string.widget_bg_location_warning),
                            description = context.getString(R.string.widget_bg_location_desc),
                            showDescription = !isShortWidget,
                            useOneUi = useOneUi,
                            cardBgColor = cardBgColor
                        )
                    } else if (mapBitmap != null) {
                        Image(
                            provider = ImageProvider(mapBitmap),
                            contentDescription = context.getString(R.string.widget_map_image_desc),
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .cornerRadius(if (useOneUi) 24.dp else 16.dp)
                        )
                    } else {
                        EmptyMapCard(
                            text = context.getString(R.string.widget_map_empty),
                            useOneUi = useOneUi,
                            cardBgColor = cardBgColor
                        )
                    }
                }
            }
        }
    }
}

private fun firstExistingPath(vararg paths: String?): String? {
    return paths.firstOrNull { path -> path != null && File(path).isFile }
}

@Composable
private fun HeaderRow(title: String, updatedAt: String, showUpdatedAt: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        if (showUpdatedAt) {
            Text(
                text = updatedAt,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.End
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    showDescription: Boolean,
    useOneUi: Boolean,
    cardBgColor: GlanceColorProvider
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cardBgColor)
            .cornerRadius(if (useOneUi) 24.dp else 12.dp)
            .padding(10.dp)
            .clickable(actionRunCallback<CheckPermissionAndRefreshAction>()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = TextStyle(
                color = GlanceTheme.colors.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            modifier = GlanceModifier.padding(bottom = 4.dp)
        )
        if (showDescription) {
            Text(
                text = description,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun EmptyMapCard(
    text: String,
    useOneUi: Boolean,
    cardBgColor: GlanceColorProvider
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(cardBgColor)
            .cornerRadius(if (useOneUi) 24.dp else 16.dp)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_place),
                contentDescription = null,
                modifier = GlanceModifier.size(28.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary)
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = text,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}
