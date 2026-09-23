package fr.geotower.utils

internal object MapProviderRules {
    const val IGN = 0
    const val OSM = 1
    const val OPEN_TOPO = 3
    const val OFFLINE = 4

    fun sanitize(provider: Int): Int = if (provider == 2) OSM else provider
}
