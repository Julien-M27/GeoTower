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
        currentTileSourceName = newTileSourceName
        tileCache?.clear()
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

    private var isDetaching = false

    override fun createTileCache(): MapTileCache {
        return SharedMapTileCacheHolder.getOrCreateTileCache(tileSource?.name())
    }

    override fun clearTileCache() {
        // En cas de detach() de la MapView (navigation Compose entre écrans),
        // on ignore la purge pour préserver les tuiles chaudes en RAM.
        // En revanche, lors d'un vrai changement de source de tuiles (setTileSource),
        // osmdroid appelle clearTileCache() et on purge immédiatement le cache partagé.
        if (!isDetaching) {
            SharedMapTileCacheHolder.clear()
        }
    }

    override fun setTileSource(pTileSource: ITileSource) {
        val oldSource = tileSource
        super.setTileSource(pTileSource)
        if (oldSource != null && oldSource.name() != pTileSource.name()) {
            SharedMapTileCacheHolder.onTileSourceChanged(pTileSource.name())
        }
    }

    override fun detach() {
        isDetaching = true
        try {
            super.detach()
        } finally {
            isDetaching = false
        }
    }
}
