package fr.geotower.ui.screens.car

import android.graphics.Bitmap
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppFileLog
import fr.geotower.widget.AntennaMapWidgetRenderer
import fr.geotower.widget.WidgetMapData
import fr.geotower.widget.WidgetMapSiteData
import fr.geotower.widget.WidgetMapAntennaData
import fr.geotower.widget.WidgetMapRenderOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Dessine la carte GeoTower dans la surface fournie par l'hôte Android Auto. */
internal class CarAntennaMapSurfaceCallback(
    private val context: Context
) : SurfaceCallback {

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generation = AtomicLong(0L)
    private val lock = Any()
    @Volatile private var latestData: WidgetMapData? = null
    @Volatile private var surfaceContainer: SurfaceContainer? = null

    fun updateSites(sites: List<CarSiteListItem>) {
        val first = sites.firstOrNull() ?: return
        latestData = WidgetMapData(
            userLat = first.userLatitude,
            userLon = first.userLongitude,
            sites = sites.map { site ->
                WidgetMapSiteData(
                    id = site.idAnfr,
                    operatorKeys = site.antennas
                        .flatMap { antenna -> antenna.operateur?.split(",") ?: emptyList() }
                        .flatMap { operator -> fr.geotower.utils.OperatorColors.keysFor(operator) }
                        .ifEmpty {
                            site.operators.split(",")
                                .flatMap { operator -> fr.geotower.utils.OperatorColors.keysFor(operator) }
                        }
                        .distinct(),
                    distanceMeters = site.distanceMeters,
                    distanceLabel = formatCarDistance(site.distanceMeters),
                    latitude = site.latitude,
                    longitude = site.longitude,
                    antennas = site.antennas.map { antenna ->
                        WidgetMapAntennaData(
                            id = antenna.idAnfr,
                            operatorName = antenna.operateur,
                            azimuts = antenna.azimuts,
                            azimutsFh = antenna.azimutsFh
                        )
                    }
                )
            }
        )
        requestRender()
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val previous = synchronized(lock) {
            val old = surfaceContainer
            surfaceContainer = container
            old
        }
        if (previous !== container) {
            previous?.getSurface()?.release()
        }
        requestRender()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        val shouldRelease = synchronized(lock) {
            if (surfaceContainer === container) {
                surfaceContainer = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            generation.incrementAndGet()
            container.getSurface()?.release()
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        requestRender()
    }

    fun detachSurface() {
        val old = synchronized(lock) {
            val value = surfaceContainer
            surfaceContainer = null
            value
        }
        generation.incrementAndGet()
        old?.getSurface()?.release()
    }

    fun close() {
        generation.incrementAndGet()
        detachSurface()
        renderScope.cancel()
    }

    private fun requestRender() {
        val requestedGeneration = generation.incrementAndGet()
        val snapshot = synchronized(lock) {
            val container = surfaceContainer ?: return
            val surface = container.getSurface() ?: return
            if (!surface.isValid || container.getWidth() <= 0 || container.getHeight() <= 0) return
            SurfaceSnapshot(surface, container.getWidth(), container.getHeight())
        }
        val data = latestData ?: return

        renderScope.launch(Dispatchers.IO) {
            try {
                val bitmap = AntennaMapWidgetRenderer.renderForSurface(
                    context = context,
                    data = data,
                    mapProvider = AppConfig.mapProvider.intValue,
                    ignStyle = AppConfig.ignStyle.intValue,
                    width = snapshot.width,
                    height = snapshot.height,
                    options = WidgetMapRenderOptions(
                        defaultOperator = AppConfig.defaultOperator.value,
                        showAzimuths = AppConfig.showAzimuths.value,
                        showAzimuthCones = AppConfig.showAzimuthsCone.value,
                        showTechnoFh = AppConfig.showTechnoFH.value
                    )
                )
                withContext(Dispatchers.Main.immediate) {
                    if (requestedGeneration == generation.get()) {
                        postBitmap(snapshot, bitmap)
                    }
                    bitmap.recycle()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                AppFileLog.e(CAR_LOG_TAG, "Echec du rendu de la carte applicative", error)
            }
        }
    }

    private fun postBitmap(snapshot: SurfaceSnapshot, bitmap: Bitmap) {
        val canvas = runCatching { snapshot.surface.lockCanvas(null) }.getOrNull() ?: return
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
            canvas.drawBitmap(
                bitmap,
                null,
                Rect(0, 0, snapshot.width, snapshot.height),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
        } finally {
            runCatching { snapshot.surface.unlockCanvasAndPost(canvas) }
        }
    }

    private data class SurfaceSnapshot(
        val surface: Surface,
        val width: Int,
        val height: Int
    )
}
