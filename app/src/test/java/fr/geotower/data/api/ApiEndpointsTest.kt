package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEndpointsTest {

    @Test
    fun officialHostsCoverPrimaryAndMirror() {
        assertTrue(ApiEndpoints.isOfficialApiHost("api.geotower.fr"))
        assertTrue(ApiEndpoints.isOfficialApiHost("api.cajejuma.fr"))
        assertTrue(ApiEndpoints.isOfficialApiHost("API.GeoTower.FR"))
        assertFalse(ApiEndpoints.isOfficialApiHost("cajejuma.fr"))
        assertFalse(ApiEndpoints.isOfficialApiHost("api.geotower.fr.evil.example"))
        assertFalse(ApiEndpoints.isOfficialApiHost(null))
    }

    @Test
    fun failoverTargetIsTheOtherServer() {
        assertEquals(ApiServer.MIRROR, ApiEndpoints.failoverTarget(ApiServer.PRIMARY))
        assertEquals(ApiServer.PRIMARY, ApiEndpoints.failoverTarget(ApiServer.MIRROR))
    }

    @Test
    fun databaseDownloadUrlsAreAcceptedOnBothServers() {
        assertTrue(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.geotower.fr/api/v2/download/db")
        )
        assertTrue(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/db")
        )
        // Le miroir ne relâche rien d'autre : schéma, chemin et absence d'identifiants restent exigés.
        assertFalse(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("http://api.cajejuma.fr/api/v2/download/db")
        )
        assertFalse(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.cajejuma.fr/api/v2/download/other")
        )
        assertFalse(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://user:pass@api.cajejuma.fr/api/v2/download/db")
        )
        assertFalse(
            DatabaseDownloader.isOfficialDatabaseDownloadUrl("https://api.example.com/api/v2/download/db")
        )
    }

    @Test
    fun urlOnHostMovesTheDownloadToTheServerThatSignedTheManifest() {
        // Les deux serveurs construisent leur base separement : le fichier doit venir du serveur
        // qui a servi le manifeste, sinon le SHA-256 ne correspond pas.
        assertEquals(
            "https://api.cajejuma.fr/api/v2/download/db",
            ApiEndpoints.urlOnHost("https://api.geotower.fr/api/v2/download/db", "api.cajejuma.fr")
        )
        assertEquals(
            "https://api.geotower.fr/api/v2/download/db",
            ApiEndpoints.urlOnHost("https://api.geotower.fr/api/v2/download/db", "api.geotower.fr")
        )
        // URL illisible : on rend l'entrée telle quelle, les contrôles amont l'ont déjà validée.
        assertEquals("pas-une-url", ApiEndpoints.urlOnHost("pas-une-url", "api.cajejuma.fr"))
    }

    @Test
    fun baseUrlsAreHttps() {
        assertEquals("https://api.geotower.fr/", ApiServer.PRIMARY.baseUrl)
        assertEquals("https://api.cajejuma.fr/", ApiServer.MIRROR.baseUrl)
        assertNull(ApiEndpoints.serverForHost("example.com"))
    }
}
