package fr.geotower.utils

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.RadioMapMarker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Keep
data class CachedScreenData(
    @SerializedName("zoom") val zoom: Double = 0.0,
    @SerializedName("lat") val lat: Double = 0.0,
    @SerializedName("lon") val lon: Double = 0.0,
    @SerializedName("antennas") val antennas: List<LocalisationEntity>? = emptyList(),
    @SerializedName("radioMarkers") val radioMarkers: List<RadioMapMarker>? = emptyList()
)

/**
 * Cache mémoire (RAM) et persistant pour les antennes et marqueurs radio actuellement
 * affichés à l'écran ("juste elles").
 *
 * Fonctionne de façon analogue à [SharedMapTileCacheHolder] pour les tuiles de carte :
 * conserve en mémoire les dernières antennes chargées pour le viewport visible lors de la
 * navigation entre onglets, de l'ouverture d'un volet latéral ou d'un écran de détail,
 * évitant ainsi tout écran vide, latence ou clignotement au retour sur la carte.
 */
object SharedMapAntennaCacheHolder {
    private const val TAG = "SharedAntennaCache"
    private const val CACHE_FILE_NAME = "visible_screen_antennas_cache.json"

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var cachedAntennas: List<LocalisationEntity> = emptyList()

    @Volatile
    private var cachedRadioMarkers: List<RadioMapMarker> = emptyList()

    @Volatile
    private var cachedZoom: Double? = null

    @Volatile
    private var cachedLat: Double? = null

    @Volatile
    private var cachedLon: Double? = null

    @Volatile
    private var isInitializedFromDisk = false

    @Synchronized
    fun getAntennas(): List<LocalisationEntity> = cachedAntennas

    @Synchronized
    fun getRadioMarkers(): List<RadioMapMarker> = cachedRadioMarkers

    @Synchronized
    fun hasData(): Boolean = cachedAntennas.isNotEmpty() || cachedRadioMarkers.isNotEmpty()

    /**
     * Initialise le cache depuis le fichier persistant au premier démarrage.
     */
    fun initFromDisk(context: Context) {
        if (isInitializedFromDisk) return
        synchronized(this) {
            if (isInitializedFromDisk) return
            isInitializedFromDisk = true
            try {
                val file = File(context.cacheDir, CACHE_FILE_NAME)
                if (file.exists() && file.length() > 0) {
                    val json = file.readText()
                    val data = gson.fromJson(json, CachedScreenData::class.java)
                    if (data != null) {
                        val antennas = data.antennas ?: emptyList()
                        val radioMarkers = data.radioMarkers ?: emptyList()

                        // Sécurité runtime : vérifie que la désérialisation a bien produit des instances
                        // du type attendu et non des LinkedTreeMap orphelins (ex: résidu de build obfusqué)
                        val validAntennas = antennas.filterIsInstance<LocalisationEntity>()
                        val validRadioMarkers = radioMarkers.filterIsInstance<RadioMapMarker>()

                        if (antennas.size != validAntennas.size || radioMarkers.size != validRadioMarkers.size) {
                            AppLogger.w(TAG, "Cached screen antennas file contained invalid/obfuscated objects, deleting file")
                            file.delete()
                        } else if (validAntennas.isNotEmpty() || validRadioMarkers.isNotEmpty()) {
                            cachedAntennas = validAntennas
                            cachedRadioMarkers = validRadioMarkers
                            cachedZoom = data.zoom
                            cachedLat = data.lat
                            cachedLon = data.lon
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to load cached screen antennas from disk", e)
                runCatching { File(context.cacheDir, CACHE_FILE_NAME).delete() }
            }
        }
    }

    /**
     * Met à jour le cache mémoire (et persiste sur disque de manière asynchrone).
     */
    @Synchronized
    fun put(
        context: Context?,
        antennas: List<LocalisationEntity>,
        radioMarkers: List<RadioMapMarker>,
        zoom: Double,
        lat: Double,
        lon: Double
    ) {
        if (antennas.isEmpty() && radioMarkers.isEmpty()) return
        cachedAntennas = antennas
        cachedRadioMarkers = radioMarkers
        cachedZoom = zoom
        cachedLat = lat
        cachedLon = lon

        if (context != null) {
            val appCtx = context.applicationContext
            val snapAntennas = antennas
            val snapRadioMarkers = radioMarkers
            scope.launch {
                try {
                    val file = File(appCtx.cacheDir, CACHE_FILE_NAME)
                    val tempFile = File(appCtx.cacheDir, "$CACHE_FILE_NAME.tmp")
                    val data = CachedScreenData(
                        zoom = zoom,
                        lat = lat,
                        lon = lon,
                        antennas = snapAntennas,
                        radioMarkers = snapRadioMarkers
                    )
                    tempFile.writeText(gson.toJson(data))
                    if (tempFile.exists()) {
                        if (file.exists()) file.delete()
                        tempFile.renameTo(file)
                    }
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Failed to persist cached screen antennas", e)
                }
            }
        }
    }

    @Synchronized
    fun clear(context: Context? = null) {
        cachedAntennas = emptyList()
        cachedRadioMarkers = emptyList()
        cachedZoom = null
        cachedLat = null
        cachedLon = null
        if (context != null) {
            val appCtx = context.applicationContext
            scope.launch {
                try {
                    val file = File(appCtx.cacheDir, CACHE_FILE_NAME)
                    if (file.exists()) file.delete()
                } catch (_: Exception) {}
            }
        }
    }
}
