package fr.geotower.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MapProviderRulesTest {

    @Test
    fun removedMapLibrePreferenceFallsBackToOpenStreetMap() {
        assertEquals(MapProviderRules.OSM, MapProviderRules.sanitize(2))
    }

    @Test
    fun supportedProvidersAreKeptUnchanged() {
        listOf(MapProviderRules.IGN, MapProviderRules.OSM, MapProviderRules.OPEN_TOPO, MapProviderRules.OFFLINE)
            .forEach { provider -> assertEquals(provider, MapProviderRules.sanitize(provider)) }
    }
}
