package fr.geotower.ui.screens.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.view.Surface
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import fr.geotower.utils.AppConfig
import fr.geotower.utils.AppFileLog
import fr.geotower.widget.AntennaMapWidgetRenderer
import fr.geotower.widget.WidgetMapData
import fr.geotower.widget.WidgetMapSiteData
import fr.geotower.widget.WidgetMapAntennaData
import fr.geotower.widget.WidgetMapCamera
import fr.geotower.widget.WidgetMapRenderOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln

/** Dessine la carte GeoTower dans la surface fournie par l'hôte Android Auto. */
internal class CarAntennaMapSurfaceCallback(
    private val context: Context
) : SurfaceCallback {

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val generation = AtomicLong(0L)
    private val traceSequence = AtomicLong(0L)
    private val lock = Any()
    @Volatile private var latestData: WidgetMapData? = null
    @Volatile private var surfaceContainer: SurfaceContainer? = null
    private var camera: WidgetMapCamera? = null
    private var scaleRemainder = 0.0

    fun setPanMode(isPanMode: Boolean) {
        // Le host utilise ce retour pour synchroniser son mode panoramique. Les mêmes callbacks
        // de surface restent valables sur écran tactile, où le bouton PAN est masqué.
        trace("setPanMode=$isPanMode")
    }

    /** Redessine le fond après un changement de fournisseur depuis les réglages Android Auto. */
    fun refresh() {
        trace("refresh demandé, provider=${AppConfig.mapProvider.intValue}, ignStyle=${AppConfig.ignStyle.intValue}")
        requestRender(previewFirst = true)
    }

    fun updateSites(sites: List<CarSiteListItem>) {
        val first = sites.firstOrNull() ?: run {
            trace("updateSites ignoré: liste vide")
            return
        }
        trace(
            "updateSites: sites=${sites.size}, antennes=${sites.sumOf { it.antennas.size }}, " +
                "surface=${surfaceDescription()}"
        )
        val newData = WidgetMapData(
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
        synchronized(lock) {
            val previousData = latestData
            latestData = newData
            if (previousData == null ||
                previousData.userLat != newData.userLat ||
                previousData.userLon != newData.userLon
            ) {
                camera = null
                scaleRemainder = 0.0
            }
        }
        trace(
            "updateSites terminé: sites=${newData.sites.size}, " +
                "antennes=${newData.sites.sumOf { it.antennas.size }}, " +
                "caméraRéinitialisée=${camera == null}"
        )
        requestRender()
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        trace(
            "onSurfaceAvailable: container=${System.identityHashCode(container)}, " +
                "surface=${container.getSurface()?.let { System.identityHashCode(it) } ?: "null"}, " +
                "taille=${container.getWidth()}x${container.getHeight()}, " +
                "valide=${container.getSurface()?.isValid == true}"
        )
        val previous = synchronized(lock) {
            val old = surfaceContainer
            surfaceContainer = container
            old
        }
        if (previous !== container) {
            trace(
                "onSurfaceAvailable: remplacement de la surface précédente=" +
                    "${previous?.let { System.identityHashCode(it) } ?: "null"}"
            )
            previous?.getSurface()?.release()
        }
        AppFileLog.i(
            CAR_LOG_TAG,
            "Surface carte disponible : ${container.getWidth()}x${container.getHeight()}, " +
                "valide=${container.getSurface()?.isValid == true}"
        )
        // Peindre immédiatement évite une surface entièrement vide pendant le téléchargement des
        // tuiles. Le rendu détaillé prend place ensuite sur le thread IO.
        postFallback(container)
        requestRender(previewFirst = true)
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        trace(
            "onSurfaceDestroyed: container=${System.identityHashCode(container)}, " +
                "surface=${container.getSurface()?.let { System.identityHashCode(it) } ?: "null"}, " +
                "courante=${synchronized(lock) { surfaceContainer === container }}"
        )
        val shouldRelease = synchronized(lock) {
            if (surfaceContainer === container) {
                surfaceContainer = null
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            val newGeneration = generation.incrementAndGet()
            trace("onSurfaceDestroyed: surface libérée, génération=$newGeneration")
            container.getSurface()?.release()
        } else {
            trace("onSurfaceDestroyed: ancienne surface ignorée")
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        trace("onVisibleAreaChanged: $visibleArea")
        requestRender()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        trace("onStableAreaChanged: $stableArea")
        requestRender()
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        trace("onScroll: dx=$distanceX, dy=$distanceY")
        updateCamera { current ->
            AntennaMapWidgetRenderer.panSurfaceCamera(current, distanceX, distanceY)
        }
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        trace("onFling: vx=$velocityX, vy=$velocityY")
        // Certains hôtes n'envoient pas de dernier onScroll au relâchement. Une courte
        // translation conserve donc l'inertie attendue sans lancer une animation incontrôlée.
        updateCamera { current ->
            AntennaMapWidgetRenderer.panSurfaceCamera(current, velocityX * 0.12f, velocityY * 0.12f)
        }
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        trace("onScale: focus=($focusX,$focusY), facteur=$scaleFactor")
        if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
        val delta = ln(scaleFactor.toDouble()) / ln(2.0)
        synchronized(lock) {
            scaleRemainder += delta
        }
        val steps = synchronized(lock) {
            if (scaleRemainder >= 0.0) floor(scaleRemainder).toInt() else ceil(scaleRemainder).toInt()
        }
        if (steps == 0) return
        synchronized(lock) {
            scaleRemainder -= steps
        }
        updateCamera { current ->
            AntennaMapWidgetRenderer.zoomSurfaceCamera(current, steps)
        }
    }

    fun zoomIn() {
        trace("zoomIn")
        updateCamera { current -> AntennaMapWidgetRenderer.zoomSurfaceCamera(current, 1) }
    }

    fun zoomOut() {
        trace("zoomOut")
        updateCamera { current -> AntennaMapWidgetRenderer.zoomSurfaceCamera(current, -1) }
    }

    fun recenter() {
        trace("recenter")
        synchronized(lock) {
            val container = surfaceContainer ?: return
            val data = latestData ?: return
            camera = AntennaMapWidgetRenderer.initialSurfaceCamera(
                data,
                container.getWidth(),
                container.getHeight()
            )
            scaleRemainder = 0.0
        }
        requestRender()
    }

    fun detachSurface() {
        trace("detachSurface demandé")
        val old = synchronized(lock) {
            val value = surfaceContainer
            surfaceContainer = null
            value
        }
        val newGeneration = generation.incrementAndGet()
        trace("detachSurface: ancienne surface=${old?.let { System.identityHashCode(it) } ?: "null"}, génération=$newGeneration")
        old?.getSurface()?.release()
    }

    fun close() {
        val newGeneration = generation.incrementAndGet()
        trace("close: génération=$newGeneration")
        detachSurface()
        renderScope.cancel()
    }

    private fun requestRender(previewFirst: Boolean = false) {
        val requestedGeneration = generation.incrementAndGet()
        trace(
            "requestRender: génération=$requestedGeneration, preview=$previewFirst, " +
                "provider=${AppConfig.mapProvider.intValue}, ignStyle=${AppConfig.ignStyle.intValue}"
        )
        val snapshot = synchronized(lock) {
            val container = surfaceContainer ?: run {
                trace("requestRender[$requestedGeneration] abandonné: aucune surface")
                return@synchronized null
            }
            val surface = container.getSurface() ?: run {
                trace("requestRender[$requestedGeneration] abandonné: surface null")
                return@synchronized null
            }
            if (!surface.isValid || container.getWidth() <= 0 || container.getHeight() <= 0) {
                trace(
                    "requestRender[$requestedGeneration] abandonné: surface invalide ou dimensions " +
                        "${container.getWidth()}x${container.getHeight()}"
                )
                return@synchronized null
            }
            val data = latestData ?: run {
                trace("requestRender[$requestedGeneration] abandonné: données absentes")
                return@synchronized null
            }
            val currentCamera = camera ?: AntennaMapWidgetRenderer.initialSurfaceCamera(
                data,
                container.getWidth(),
                container.getHeight()
            ).also { camera = it }
            SurfaceSnapshot(surface, container.getWidth(), container.getHeight(), currentCamera)
        } ?: return
        val data = latestData ?: run {
            trace("requestRender[$requestedGeneration] abandonné: données disparues après snapshot")
            return
        }

        trace(
            "requestRender[$requestedGeneration] lancé: surface=${System.identityHashCode(snapshot.surface)}, " +
                "taille=${snapshot.width}x${snapshot.height}, sites=${data.sites.size}, " +
                "antennes=${data.sites.sumOf { it.antennas.size }}, caméra=${snapshot.camera}"
        )

        renderScope.launch(Dispatchers.IO) {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            trace("render[$requestedGeneration] début sur thread=${Thread.currentThread().name}")
            try {
                if (previewFirst) {
                    trace("render[$requestedGeneration] aperçu sans tuiles: début")
                    val preview = runCatching {
                        AntennaMapWidgetRenderer.renderForSurface(
                            context = context,
                            data = data,
                            mapProvider = AppConfig.mapProvider.intValue,
                            ignStyle = AppConfig.ignStyle.intValue,
                            width = snapshot.width,
                            height = snapshot.height,
                            options = currentRenderOptions(),
                            camera = snapshot.camera,
                            drawBaseTiles = false
                        )
                    }.getOrNull()
                    if (preview != null) {
                        withContext(Dispatchers.Main.immediate) {
                            val current = requestedGeneration == generation.get()
                            trace(
                                "render[$requestedGeneration] aperçu prêt, " +
                                    "générationCourante=${generation.get()}, post=$current"
                            )
                            if (current) postBitmap(snapshot, preview)
                            preview.recycle()
                        }
                    } else {
                        trace("render[$requestedGeneration] aperçu indisponible")
                    }
                }
                trace("render[$requestedGeneration] rendu complet avec tuiles: début")
                val bitmap = AntennaMapWidgetRenderer.renderForSurface(
                    context = context,
                    data = data,
                    mapProvider = AppConfig.mapProvider.intValue,
                    ignStyle = AppConfig.ignStyle.intValue,
                    width = snapshot.width,
                    height = snapshot.height,
                    options = currentRenderOptions(),
                    camera = snapshot.camera
                )
                trace(
                    "render[$requestedGeneration] rendu complet terminé en " +
                        "${android.os.SystemClock.elapsedRealtime() - startedAt} ms"
                )
                withContext(Dispatchers.Main.immediate) {
                    val current = requestedGeneration == generation.get()
                    trace(
                        "render[$requestedGeneration] carte complète prête, " +
                            "générationCourante=${generation.get()}, post=$current"
                    )
                    if (current) {
                        postBitmap(snapshot, bitmap)
                    }
                    bitmap.recycle()
                }
            } catch (cancelled: CancellationException) {
                trace("render[$requestedGeneration] annulé après ${android.os.SystemClock.elapsedRealtime() - startedAt} ms")
                throw cancelled
            } catch (error: Throwable) {
                AppFileLog.e(CAR_LOG_TAG, "Echec du rendu de la carte applicative", error)
                trace(
                    "render[$requestedGeneration] exception après " +
                        "${android.os.SystemClock.elapsedRealtime() - startedAt} ms: " +
                        "${error.javaClass.simpleName}: ${error.message ?: "-"}"
                )
                withContext(Dispatchers.Main.immediate) {
                    val current = requestedGeneration == generation.get()
                    trace("render[$requestedGeneration] fallback après exception, post=$current")
                    if (current) postFallback(snapshot)
                }
            }
        }
    }

    private fun currentRenderOptions(): WidgetMapRenderOptions {
        return WidgetMapRenderOptions(
            defaultOperator = AppConfig.defaultOperator.value,
            showAzimuths = AppConfig.showAzimuths.value,
            showAzimuthCones = AppConfig.showAzimuthsCone.value,
            showTechnoFh = AppConfig.showTechnoFH.value
        )
    }

    private fun updateCamera(transform: (WidgetMapCamera) -> WidgetMapCamera) {
        synchronized(lock) {
            val container = surfaceContainer ?: return
            val data = latestData ?: return
            val current = camera ?: AntennaMapWidgetRenderer.initialSurfaceCamera(
                data,
                container.getWidth(),
                container.getHeight()
            )
            camera = transform(current)
        }
        requestRender()
    }

    private fun postBitmap(snapshot: SurfaceSnapshot, bitmap: Bitmap) {
        val canvas = lockSurfaceCanvas(snapshot.surface) ?: run {
            trace("postBitmap: impossible de verrouiller la surface=${System.identityHashCode(snapshot.surface)}")
            return
        }
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
        trace("postBitmap: image=${bitmap.width}x${bitmap.height} publiée sur surface=${System.identityHashCode(snapshot.surface)}")
    }

    private fun postFallback(snapshot: SurfaceSnapshot) {
        val canvas = lockSurfaceCanvas(snapshot.surface) ?: run {
            trace("postFallback(snapshot): impossible de verrouiller la surface")
            return
        }
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
        } finally {
            runCatching { snapshot.surface.unlockCanvasAndPost(canvas) }
        }
        trace("postFallback(snapshot): fond uni publié")
    }

    private fun postFallback(container: SurfaceContainer) {
        val surface = container.getSurface() ?: return
        val canvas = lockSurfaceCanvas(surface) ?: run {
            trace("postFallback(container): impossible de verrouiller la surface")
            return
        }
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
        }
        trace("postFallback(container): fond uni publié sur container=${System.identityHashCode(container)}")
    }

    /** Android Auto fournit généralement une surface matérielle pour cette zone cartographique. */
    private fun lockSurfaceCanvas(surface: Surface): Canvas? {
        if (!surface.isValid) {
            trace("lockSurfaceCanvas: surface invalide=${System.identityHashCode(surface)}")
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { surface.lockHardwareCanvas() }
                .onFailure {
                    trace(
                        "lockSurfaceCanvas: lockHardwareCanvas échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching { surface.lockCanvas(null) }
            .onFailure {
                trace(
                    "lockSurfaceCanvas: lockCanvas échoué=" +
                        "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                )
            }
            .getOrNull()
    }

    private fun surfaceDescription(): String = synchronized(lock) {
        val container = surfaceContainer ?: return@synchronized "null"
        val surface = container.getSurface()
        "container=${System.identityHashCode(container)}, " +
            "surface=${surface?.let { System.identityHashCode(it) } ?: "null"}, " +
            "taille=${container.getWidth()}x${container.getHeight()}, " +
            "valide=${surface?.isValid == true}"
    }

    private fun trace(message: String) {
        AppFileLog.i(
            CAR_LOG_TAG,
            "SurfaceMap#${traceSequence.incrementAndGet()}: $message"
        )
    }

    private data class SurfaceSnapshot(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val camera: WidgetMapCamera
    )
}
