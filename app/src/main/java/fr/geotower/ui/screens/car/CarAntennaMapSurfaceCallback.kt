package fr.geotower.ui.screens.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.app.Presentation
import android.view.Surface
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.Job
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
    private val traceStartedAt = SystemClock.elapsedRealtime()
    private val lock = Any()
    private var renderJob: Job? = null
    @Volatile private var latestData: WidgetMapData? = null
    @Volatile private var surfaceContainer: SurfaceContainer? = null
    private var camera: WidgetMapCamera? = null
    private var scaleRemainder = 0.0
    private var visibleArea: Rect? = null
    private var stableArea: Rect? = null
    /**
     * Chemin recommandé par la documentation Android for Cars pour rendre des vues dans la
     * Surface fournie par l'hôte. Certains hôtes Android Auto acceptent lockHardwareCanvas() et
     * postent bien le buffer sans toutefois le composer à l'écran. La VirtualDisplay force ici un
     * vrai pipeline de vue, tout en gardant le Canvas direct comme repli.
     */
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var surfaceView: MapSurfaceView? = null
    /**
     * Un Canvas matériel peut consommer une bitmap après le retour de
     * unlockCanvasAndPost(). On conserve donc quelques images publiées comme le fait un
     * TextureView, au lieu de laisser la dernière image devenir éligible au GC immédiatement.
     */
    private val postedBitmaps = ArrayList<Bitmap>(3)

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
            if (old !== container) postedBitmaps.clear()
            old
        }
        if (previous !== container) {
            trace(
                "onSurfaceAvailable: remplacement de la surface précédente=" +
                    "${previous?.let { System.identityHashCode(it) } ?: "null"}"
            )
            releaseRenderingTarget(previous?.getSurface(), "remplacement")
        }
        AppFileLog.i(
            CAR_LOG_TAG,
            "Surface carte disponible : ${container.getWidth()}x${container.getHeight()}, " +
                "valide=${container.getSurface()?.isValid == true}"
        )
        // La VirtualDisplay affiche immédiatement le fond de la vue pendant le téléchargement des
        // tuiles. Si l'hôte refuse ce chemin, postFallback utilise le Canvas direct.
        val virtualDisplayAttached = attachVirtualDisplay(container)
        trace("onSurfaceAvailable: VirtualDisplay attachée=$virtualDisplayAttached")
        if (!virtualDisplayAttached) postFallback(container)
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
                postedBitmaps.clear()
                true
            } else {
                false
            }
        }
        if (shouldRelease) {
            val newGeneration = generation.incrementAndGet()
            trace("onSurfaceDestroyed: surface libérée, génération=$newGeneration")
            val job = synchronized(lock) {
                renderJob.also { renderJob = null }
            }
            if (job?.isActive == true) {
                trace("onSurfaceDestroyed: rendu actif annulé car la surface a disparu")
                job.cancel()
            }
            releaseRenderingTarget(container.getSurface(), "destruction")
        } else {
            trace("onSurfaceDestroyed: ancienne surface ignorée")
        }
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        synchronized(lock) { this.visibleArea = Rect(visibleArea) }
        trace("onVisibleAreaChanged: $visibleArea, ${surfaceDescription()}")
        requestRender()
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        synchronized(lock) { this.stableArea = Rect(stableArea) }
        trace("onStableAreaChanged: $stableArea, ${surfaceDescription()}")
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
        trace("recenter demandé")
        val shouldRender = try {
            synchronized(lock) {
                val container = surfaceContainer
                val data = latestData
                when {
                    container == null -> {
                        trace("recenter ignoré: aucune surface disponible")
                        false
                    }
                    data == null -> {
                        trace("recenter ignoré: données de carte absentes")
                        false
                    }
                    container.getWidth() <= 0 || container.getHeight() <= 0 -> {
                        trace(
                            "recenter ignoré: dimensions invalides " +
                                "${container.getWidth()}x${container.getHeight()}"
                        )
                        false
                    }
                    else -> {
                        camera = AntennaMapWidgetRenderer.initialSurfaceCamera(
                            data,
                            container.getWidth(),
                            container.getHeight()
                        )
                        scaleRemainder = 0.0
                        trace("recenter appliqué: caméra=$camera")
                        true
                    }
                }
            }
        } catch (error: Throwable) {
            AppFileLog.e(CAR_LOG_TAG, "Echec du recentrage de la carte", error)
            trace(
                "recenter en échec: ${error.javaClass.simpleName}: " +
                    (error.message ?: "-")
            )
            false
        }
        if (shouldRender) requestRender()
    }

    fun detachSurface() {
        trace("detachSurface demandé")
        val old = synchronized(lock) {
            val value = surfaceContainer
            surfaceContainer = null
            postedBitmaps.clear()
            value
        }
        val newGeneration = generation.incrementAndGet()
        trace("detachSurface: ancienne surface=${old?.let { System.identityHashCode(it) } ?: "null"}, génération=$newGeneration")
        releaseRenderingTarget(old?.getSurface(), "détachement")
    }

    fun close() {
        val newGeneration = generation.incrementAndGet()
        trace("close: génération=$newGeneration")
        detachSurface()
        val job = synchronized(lock) {
            renderJob.also { renderJob = null }
        }
        if (job != null) {
            trace("close: annulation du rendu actif=${job.isActive}")
            job.cancel()
        }
        renderScope.cancel()
    }

    private fun requestRender(previewFirst: Boolean = false) {
        val requestedGeneration = generation.incrementAndGet()
        trace(
            "requestRender: génération=$requestedGeneration, preview=$previewFirst, " +
                "provider=${AppConfig.mapProvider.intValue}, ignStyle=${AppConfig.ignStyle.intValue}"
        )
        val previousJob = synchronized(lock) {
            renderJob.also { renderJob = null }
        }
        if (previousJob?.isActive == true) {
            trace("requestRender[$requestedGeneration]: ancien rendu annulé pour éviter les rendus concurrents")
            previousJob.cancel()
        }
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

        val newJob = renderScope.launch(Dispatchers.IO) {
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
                        prepareBitmapForSurface(preview, requestedGeneration, "aperçu")
                        withContext(Dispatchers.Main.immediate) {
                            val current = requestedGeneration == generation.get()
                            trace(
                                "render[$requestedGeneration] aperçu prêt, " +
                                    "générationCourante=${generation.get()}, post=$current"
                            )
                            if (current) {
                                postBitmap(snapshot, preview)
                            } else {
                                // Un aperçu obsolète n'a jamais été remis à la surface.
                                preview.recycle()
                            }
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
                prepareBitmapForSurface(bitmap, requestedGeneration, "carte complète")
                withContext(Dispatchers.Main.immediate) {
                    val current = requestedGeneration == generation.get()
                    trace(
                        "render[$requestedGeneration] carte complète prête, " +
                            "générationCourante=${generation.get()}, post=$current"
                    )
                    if (current) {
                        postBitmap(snapshot, bitmap)
                    } else {
                        // Une image obsolète peut être libérée immédiatement : elle n'est pas
                        // référencée par un Canvas matériel.
                        bitmap.recycle()
                    }
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
        synchronized(lock) {
            renderJob = newJob
        }
    }

    private fun currentRenderOptions(): WidgetMapRenderOptions {
        return WidgetMapRenderOptions(
            defaultOperator = AppConfig.defaultOperator.value,
            showAzimuths = AppConfig.showAzimuths.value,
            showAzimuthCones = AppConfig.showAzimuthsCone.value,
            showTechnoFh = AppConfig.showTechnoFH.value,
            drawSiteOverlays = DRAW_CAR_MAP_SITE_OVERLAYS
        )
    }

    private fun prepareBitmapForSurface(bitmap: Bitmap, requestedGeneration: Long, label: String) {
        runCatching { bitmap.prepareToDraw() }
            .onSuccess {
                trace(
                    "render[$requestedGeneration] $label préparée pour le Canvas, " +
                        "taille=${bitmap.width}x${bitmap.height}, config=${bitmap.config}, " +
                        "échantillons=${bitmapSampleSummary(bitmap)}"
                )
            }
            .onFailure {
                trace(
                    "render[$requestedGeneration] préparation de $label impossible=" +
                        "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                )
            }
    }

    private fun bitmapSampleSummary(bitmap: Bitmap): String {
        return runCatching {
            val points = listOf(
                0 to 0,
                bitmap.width / 2 to bitmap.height / 2,
                (bitmap.width * 0.25f).toInt() to (bitmap.height * 0.25f).toInt(),
                (bitmap.width * 0.75f).toInt() to (bitmap.height * 0.75f).toInt(),
                (bitmap.width - 1).coerceAtLeast(0) to (bitmap.height - 1).coerceAtLeast(0)
            )
            points.joinToString(";") { (x, y) ->
                "${x},${y}=#${Integer.toHexString(bitmap.getPixel(x, y)).padStart(8, '0')}"
            }
        }.getOrElse { "indisponibles:${it.javaClass.simpleName}" }
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
        if (bitmap.isRecycled) {
            trace(
                "postBitmap: bitmap déjà recyclée avant affichage, " +
                    "surface=${System.identityHashCode(snapshot.surface)}"
            )
            AppFileLog.w(CAR_LOG_TAG, "Bitmap de carte recyclée avant publication")
            postFallback(snapshot)
            return
        }
        if (postBitmapToVirtualDisplay(snapshot, bitmap)) return
        // La carte est déjà rasterisée par notre renderer. Un Canvas logiciel rend la copie de
        // cette bitmap synchrone et évite les particularités GPU de certains hôtes Android Auto.
        // Le helper repasse automatiquement au Canvas matériel si le logiciel est refusé.
        val canvas = lockSurfaceCanvas(snapshot.surface, preferSoftware = true) ?: run {
            trace("postBitmap: impossible de verrouiller la surface=${System.identityHashCode(snapshot.surface)}")
            return
        }
        trace(
            "postBitmap: début image=${bitmap.width}x${bitmap.height}, " +
                "config=${bitmap.config}, recyclée=${bitmap.isRecycled}, " +
                "canvasMatériel=${canvas.isHardwareAccelerated}, " +
                "surface=${System.identityHashCode(snapshot.surface)}"
        )
        var unlockSucceeded = false
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
            if (bitmap.width == snapshot.width && bitmap.height == snapshot.height) {
                // La bitmap est déjà à la taille de la surface : il n'est donc pas nécessaire de
                // demander une mise à l'échelle au Canvas.
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            } else {
                canvas.drawBitmap(
                    bitmap,
                    null,
                    Rect(0, 0, snapshot.width, snapshot.height),
                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                )
            }
        } finally {
            runCatching { snapshot.surface.unlockCanvasAndPost(canvas) }
                .onSuccess {
                    unlockSucceeded = true
                    trace("postBitmap: unlockCanvasAndPost réussi")
                }
                .onFailure {
                    trace(
                        "postBitmap: unlockCanvasAndPost échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        }
        if (unlockSucceeded) {
            synchronized(lock) {
                postedBitmaps += bitmap
                while (postedBitmaps.size > 3) postedBitmaps.removeAt(0)
            }
            trace("postBitmap: référence forte conservée, images retenues=${synchronized(lock) { postedBitmaps.size }}")
        }
        trace(
            "postBitmap: image=${bitmap.width}x${bitmap.height} publiée; " +
                "bitmap non recyclée après publication pour préserver sa durée de vie GPU; " +
                "surface=${System.identityHashCode(snapshot.surface)}"
        )
    }

    private fun postFallback(snapshot: SurfaceSnapshot) {
        if (clearVirtualDisplay(snapshot)) return
        val canvas = lockSurfaceCanvas(snapshot.surface) ?: run {
            trace("postFallback(snapshot): impossible de verrouiller la surface")
            return
        }
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
        } finally {
            runCatching { snapshot.surface.unlockCanvasAndPost(canvas) }
                .onSuccess { trace("postFallback(snapshot): unlockCanvasAndPost réussi") }
                .onFailure {
                    trace(
                        "postFallback(snapshot): unlockCanvasAndPost échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        }
        trace("postFallback(snapshot): fond uni publié")
    }

    private fun postFallback(container: SurfaceContainer) {
        if (clearVirtualDisplay(container)) return
        val surface = container.getSurface() ?: return
        val canvas = lockSurfaceCanvas(surface) ?: run {
            trace("postFallback(container): impossible de verrouiller la surface")
            return
        }
        try {
            canvas.drawColor(Color.rgb(18, 29, 40))
        } finally {
            runCatching { surface.unlockCanvasAndPost(canvas) }
                .onSuccess { trace("postFallback(container): unlockCanvasAndPost réussi") }
                .onFailure {
                    trace(
                        "postFallback(container): unlockCanvasAndPost échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        }
        trace("postFallback(container): fond uni publié sur container=${System.identityHashCode(container)}")
    }

    /**
     * Rend la bitmap via une vraie vue attachée à une Presentation sur la VirtualDisplay de la
     * Surface Auto. Android documente explicitement ce chemin pour les cartes, en complément du
     * Canvas direct.
     */
    private fun attachVirtualDisplay(container: SurfaceContainer): Boolean {
        val surface = container.getSurface() ?: run {
            trace("attachVirtualDisplay: surface absente")
            return false
        }
        if (!surface.isValid || container.getWidth() <= 0 || container.getHeight() <= 0) {
            trace(
                "attachVirtualDisplay: surface/dimensions invalides, " +
                    "valide=${surface.isValid}, taille=${container.getWidth()}x${container.getHeight()}"
            )
            return false
        }
        val alreadyAttached = synchronized(lock) {
            surfaceContainer?.getSurface() === surface &&
                virtualDisplay != null &&
                surfaceView != null
        }
        if (alreadyAttached) {
            trace(
                "attachVirtualDisplay: cible déjà attachée, " +
                    "surface=${System.identityHashCode(surface)}"
            )
            return true
        }

        var createdDisplay: VirtualDisplay? = null
        var createdPresentation: Presentation? = null
        return runCatching {
            val displayManager = context.getSystemService(DisplayManager::class.java)
                ?: error("DisplayManager indisponible")
            val dpi = container.getDpi().coerceAtLeast(1)
            trace(
                "attachVirtualDisplay: création, surface=${System.identityHashCode(surface)}, " +
                    "taille=${container.getWidth()}x${container.getHeight()}, dpi=$dpi"
            )
            val displayInstance = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                container.getWidth(),
                container.getHeight(),
                dpi,
                surface,
                0
            ) ?: error("createVirtualDisplay a renvoyé null")
            createdDisplay = displayInstance
            val display = displayInstance.display
            val view = MapSurfaceView(context, container.getWidth(), container.getHeight())
            view.layoutParams = ViewGroup.LayoutParams(
                container.getWidth(),
                container.getHeight()
            )
            createdPresentation = Presentation(context, display).also { presentation ->
                presentation.window?.setBackgroundDrawable(
                    ColorDrawable(Color.rgb(18, 29, 40))
                )
                presentation.setContentView(view)
                presentation.show()
                presentation.window?.setLayout(container.getWidth(), container.getHeight())
            }
            synchronized(lock) {
                virtualDisplay = createdDisplay
                presentation = createdPresentation
                surfaceView = view
            }
            trace(
                "attachVirtualDisplay: succès, display=${display.displayId}, " +
                    "view=${System.identityHashCode(view)}, " +
                    "présentation=${System.identityHashCode(createdPresentation)}"
            )
            true
        }.onFailure { error ->
            trace(
                "attachVirtualDisplay: échec=${error.javaClass.simpleName}: " +
                    (error.message ?: "-")
            )
            AppFileLog.e(CAR_LOG_TAG, "Impossible d'attacher la carte à une VirtualDisplay", error)
            runCatching { createdPresentation?.dismiss() }
            runCatching { createdDisplay?.release() }
        }.getOrDefault(false)
    }

    private fun postBitmapToVirtualDisplay(snapshot: SurfaceSnapshot, bitmap: Bitmap): Boolean {
        val view = synchronized(lock) {
            val currentSurface = surfaceContainer?.getSurface()
            if (currentSurface === snapshot.surface) surfaceView else null
        } ?: return false
        view.setBitmap(bitmap)
        synchronized(lock) {
            postedBitmaps += bitmap
            while (postedBitmaps.size > 3) postedBitmaps.removeAt(0)
        }
        trace(
            "postBitmap: bitmap remise à la vue VirtualDisplay, " +
                "image=${bitmap.width}x${bitmap.height}, view=${System.identityHashCode(view)}, " +
                "matériel=${view.isHardwareAccelerated}, imagesRetenues=" +
                synchronized(lock) { postedBitmaps.size }
        )
        return true
    }

    private fun clearVirtualDisplay(snapshot: SurfaceSnapshot): Boolean {
        val view = synchronized(lock) {
            if (surfaceContainer?.getSurface() === snapshot.surface) surfaceView else null
        } ?: return false
        view.clearBitmap()
        trace("postFallback: fond uni remis à la vue VirtualDisplay")
        return true
    }

    private fun clearVirtualDisplay(container: SurfaceContainer): Boolean {
        val view = synchronized(lock) {
            if (surfaceContainer === container) surfaceView else null
        } ?: return false
        view.clearBitmap()
        trace("postFallback: fond uni remis à la vue VirtualDisplay")
        return true
    }

    /** Libère la Presentation et la VirtualDisplay, ou la Surface directement en mode Canvas. */
    private fun releaseRenderingTarget(surface: Surface?, reason: String) {
        val target = synchronized(lock) {
            val value = ReleaseTarget(virtualDisplay, presentation)
            virtualDisplay = null
            presentation = null
            surfaceView = null
            value
        }
        if (target.presentation != null) {
            runCatching { target.presentation.dismiss() }
                .onFailure {
                    trace(
                        "releaseRenderingTarget[$reason]: dismiss échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        }
        if (target.virtualDisplay != null) {
            runCatching { target.virtualDisplay.release() }
                .onSuccess { trace("releaseRenderingTarget[$reason]: VirtualDisplay libérée") }
                .onFailure {
                    trace(
                        "releaseRenderingTarget[$reason]: release VirtualDisplay échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        } else if (surface != null) {
            runCatching { surface.release() }
                .onSuccess { trace("releaseRenderingTarget[$reason]: Surface libérée directement") }
                .onFailure {
                    trace(
                        "releaseRenderingTarget[$reason]: release Surface échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
        }
    }

    /** Android Auto fournit généralement une surface matérielle pour cette zone cartographique. */
    private fun lockSurfaceCanvas(surface: Surface, preferSoftware: Boolean = false): Canvas? {
        if (!surface.isValid) {
            trace("lockSurfaceCanvas: surface invalide=${System.identityHashCode(surface)}")
            return null
        }
        if (preferSoftware) {
            runCatching { surface.lockCanvas(null) }
                .onSuccess {
                    trace(
                        "lockSurfaceCanvas: Canvas logiciel préféré obtenu, " +
                            "matériel=${it.isHardwareAccelerated}, surface=${System.identityHashCode(surface)}"
                    )
                }
                .onFailure {
                    trace(
                        "lockSurfaceCanvas: Canvas logiciel préféré échoué=" +
                            "${it.javaClass.simpleName}: ${it.message ?: "-"}"
                    )
                }
                .getOrNull()
                ?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { surface.lockHardwareCanvas() }
                .onSuccess {
                    trace(
                        "lockSurfaceCanvas: lockHardwareCanvas réussi, " +
                            "matériel=${it.isHardwareAccelerated}, surface=${System.identityHashCode(surface)}"
                    )
                }
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
            .onSuccess {
                trace(
                    "lockSurfaceCanvas: lockCanvas réussi, " +
                        "matériel=${it.isHardwareAccelerated}, surface=${System.identityHashCode(surface)}"
                )
            }
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
            "valide=${surface?.isValid == true}, " +
            "visible=$visibleArea, stable=$stableArea, " +
            "virtualDisplay=${virtualDisplay != null}, view=${surfaceView?.let { System.identityHashCode(it) } ?: "null"}"
    }

    private fun trace(message: String) {
        val elapsed = SystemClock.elapsedRealtime() - traceStartedAt
        AppFileLog.i(
            CAR_LOG_TAG,
            "SurfaceMap#${traceSequence.incrementAndGet()} (+${elapsed}ms, " +
                "thread=${Thread.currentThread().name}): $message"
        )
    }

    private data class SurfaceSnapshot(
        val surface: Surface,
        val width: Int,
        val height: Int,
        val camera: WidgetMapCamera
    )

    private data class ReleaseTarget(
        val virtualDisplay: VirtualDisplay?,
        val presentation: Presentation?
    )

    private class MapSurfaceView(
        context: Context,
        private val expectedWidth: Int,
        private val expectedHeight: Int
    ) : View(context) {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        @Volatile private var bitmap: Bitmap? = null
        private var firstDrawLogged = false
        private var drawCount = 0

        init {
            setBackgroundColor(Color.rgb(18, 29, 40))
            isFocusable = false
            // Le contenu est une bitmap déjà rasterisée. Une couche logicielle évite qu'un hôte
            // Android Auto compose mal le buffer GPU de la Presentation sur la Surface fournie.
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }

        fun setBitmap(value: Bitmap) {
            bitmap = value
            AppFileLog.i(
                CAR_LOG_TAG,
                "MapSurfaceView: bitmap reçue=${value.width}x${value.height}, " +
                    "vue=${width}x${height}, attachée=$isAttachedToWindow, visible=$isShown, " +
                    "visibilité=$visibility, fenêtre=$windowVisibility, " +
                    "thread=${Thread.currentThread().name}"
            )
            // postInvalidateOnAnimation() peut ne pas être relayé par le Choreographer d'une
            // Presentation sur écran secondaire. Les publications viennent normalement du main
            // thread ; invalidate() force alors le passage par ViewRootImpl.
            if (Looper.myLooper() == Looper.getMainLooper()) {
                invalidate()
            } else {
                postInvalidate()
            }
        }

        fun clearBitmap() {
            bitmap = null
            if (Looper.myLooper() == Looper.getMainLooper()) {
                invalidate()
            } else {
                postInvalidate()
            }
        }

        override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
            super.onSizeChanged(width, height, oldWidth, oldHeight)
            AppFileLog.i(
                CAR_LOG_TAG,
                "MapSurfaceView: taille=${width}x${height}, attendue=${expectedWidth}x${expectedHeight}, " +
                    "ancienne=${oldWidth}x${oldHeight}"
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            drawCount += 1
            canvas.drawColor(Color.rgb(18, 29, 40))
            val value = bitmap
            var bitmapDrawn = false
            if (value != null && !value.isRecycled) {
                if (value.width == width && value.height == height) {
                    canvas.drawBitmap(value, 0f, 0f, null)
                } else {
                    canvas.drawBitmap(value, null, Rect(0, 0, width, height), bitmapPaint)
                }
                bitmapDrawn = true
            }
            if (!firstDrawLogged || bitmapDrawn) {
                firstDrawLogged = true
                AppFileLog.i(
                    CAR_LOG_TAG,
                    "MapSurfaceView: onDraw#$drawCount, view=${width}x${height}, " +
                        "bitmap=${value?.let { "${it.width}x${it.height},recyclée=${it.isRecycled}" } ?: "null"}, " +
                        "bitmapDessiné=$bitmapDrawn, canvasMatériel=${canvas.isHardwareAccelerated}, " +
                        "attachée=$isAttachedToWindow, visible=$isShown"
                )
            }
        }
    }
}

private const val VIRTUAL_DISPLAY_NAME = "GeoTower Android Auto Map"

// Variante normale. Pour l'essai A/B demandé, cette constante est temporairement passée à false
// lors de la génération d'un APK de diagnostic, puis remise à true pour l'APK courant.
private const val DRAW_CAR_MAP_SITE_OVERLAYS = true
