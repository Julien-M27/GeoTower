package fr.geotower.ui.components

import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppLogoDrawingResources

/**
 * Renders an in-app logo while keeping the optional Material-colored wave layer isolated from the
 * rest of the artwork. Non-logo drawables passed here are rendered normally.
 */
@Composable
fun AppLogoImage(
    resId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val materialWavesEnabled by AppConfig.isLogoMaterialWavesEnabled
    val materialColor = MaterialTheme.colorScheme.primary.toArgb()
    val drawable = remember(context, resId, materialWavesEnabled, materialColor) {
        AppLogoDrawingResources.displayDrawable(
            context = context,
            drawableRes = resId,
            materialWavesEnabled = materialWavesEnabled,
            materialColor = materialColor
        )
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
                setImageDrawable(drawable)
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(drawable)
            imageView.contentDescription = contentDescription
        }
    )
}
