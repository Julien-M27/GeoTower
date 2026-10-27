package fr.geotower.utils

import android.content.Context
import org.osmdroid.tileprovider.MapTileCache
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.ITileSource

/**
 * Gestionnaire de cache mémoire (RAM) partagé et persistant pour les tuiles de carte.
 *
 * Conserve en mémoire les tuiles déjà téléchargées et décodées lors de la navigation
 * entre onglets (barre inférieure) ou écrans Compose, évitant ainsi tout rechargement visuel,
 * clignotement, lecture disque SQLite répétitive ou appel réseau redondant.
 */
object SharedMapTileCacheHolder {
    private var tileCache: MapTileCache? = null
    private var currentTileSourceName: String? = null

    @Synchronized
    fun getOrCreateTileCache(tileSourceName: String? = null): MapTileCache {
        val cache = tileCache ?: MapTileCache().also {
            // Capacité de 240 tuiles (environ 60 Mo de RAM), largement suffisante
            // pour l'écran visible et les pré-chargements de bordure / zoom.
            it.ensureCapacity(240)
            tileCache = it
        }

        if (tileSourceName != null && currentTileSourceName != null && currentTileSourceName != tileSourceName) {
            // Le fond cartographique a réellement changé (ex: Plan IGN -> Satellite) :
            // on purge les anciennes tuiles pour éviter les superpositions.
            cache.clear()
        }
        if (tileSourceName != null) {
            currentTileSourceName = tileSourceName
        }

        return cache
    }

    @Synchronized
    fun onTileSourceChanged(newTileSourceName: String) {
        if (currentTileSourceName != newTileSourceName) {
            currentTileSourceName = newTileSourceName
            tileCache?.clear()
        }
    }

    @Synchronized
    fun clear() {
        tileCache?.clear()
        currentTileSourceName = null
    }
}

/**
 * Fournisseur de tuiles osmdroid à cache mémoire persistant.
 *
 * Contrairement au [MapTileProviderBasic] d'origine qui vide l'intégralité de son cache RAM
 * lors de son détachement (systématiquement déclenché à chaque sortie de l'écran MapScreen en Compose),
 * ce fournisseur préserve les tuiles en mémoire tant que l'application est active et que la source
 * de tuiles ne change pas.
 */
class PersistentMapTileProvider(
    context: Context,
    tileSource: ITileSource
) : MapTileProviderBasic(context, tileSource) {

    override fun createTileCache(): MapTileCache {
        return SharedMapTileCacheHolder.getOrCreateTileCache(tileSource?.name())
    }

    override fun clearTileCache() {
        // Ignorer les purges automatiques déclenchées lors du detach() de la MapView
        // afin de préserver les tuiles chaudes en RAM pour le prochain passage sur la carte.
    }

    override fun setTileSource(pTileSource: ITileSource) {
        val oldSource = tileSource
        super.setTileSource(pTileSource)
        if (oldSource != null && oldSource.name() != pTileSource.name()) {
            SharedMapTileCacheHolder.onTileSourceChanged(pTileSource.name())
        }
    }

    override fun detach() {
        // Détache les threads d'arrière-plan sans vider le cache RAM partagé
        super.detach()
    }
}
