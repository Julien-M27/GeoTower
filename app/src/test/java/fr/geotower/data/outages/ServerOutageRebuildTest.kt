package fr.geotower.data.outages

import fr.geotower.data.api.SitesHsRebuildDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerOutageRebuildTest {

    @Test
    fun acceptedRequestIsRunningAndNotRefused() {
        val status = SitesHsRebuildDto(
            state = "running",
            running = true,
            started = true,
            quotaPerHour = 2,
            usedLastHour = 1,
            remaining = 1,
            fileUpdatedAt = "2026-08-09T10:30:00Z",
        ).toRebuildStatus(202)

        assertEquals(ServerOutageRebuildState.RUNNING, status.state)
        assertTrue(status.running)
        assertTrue(status.startedByThisRequest)
        assertFalse(status.rateLimited)
        assertEquals(ServerOutageRebuildRefusal.NONE, status.refusal)
        assertEquals(1, status.remaining)
        // Horodatage UTC : lu dans le fuseau du serveur, pas dans celui du téléphone.
        assertEquals(1_786_271_400_000L, status.fileUpdatedAtMillis)
    }

    @Test
    fun quotaRefusalCarriesTheDelayInsteadOfBeingAnError() {
        val status = SitesHsRebuildDto(
            state = "done",
            quotaPerHour = 2,
            usedLastHour = 2,
            remaining = 0,
            retryAfterSeconds = 1_800,
            reason = "quota_global",
        ).toRebuildStatus(HTTP_TOO_MANY_REQUESTS)

        assertTrue(status.rateLimited)
        assertEquals(ServerOutageRebuildRefusal.GLOBAL_QUOTA, status.refusal)
        assertEquals(1_800, status.retryAfterSeconds)
        assertEquals(0, status.remaining)
    }

    @Test
    fun perDeviceRefusalIsToldApartFromTheGlobalOne() {
        val status = SitesHsRebuildDto(reason = "quota_client", retryAfterSeconds = 600)
            .toRebuildStatus(HTTP_TOO_MANY_REQUESTS)

        assertEquals(ServerOutageRebuildRefusal.CLIENT_QUOTA, status.refusal)
    }

    @Test
    fun anUnknownStateFallsBackToIdleRatherThanCrashing() {
        val status = SitesHsRebuildDto(state = "n_importe_quoi").toRebuildStatus(200)

        assertEquals(ServerOutageRebuildState.IDLE, status.state)
        assertEquals(0L, status.fileUpdatedAtMillis)
    }

    @Test
    fun failedGenerationKeepsTheServerReason() {
        val status = SitesHsRebuildDto(
            state = "failed",
            error = "Le generateur a rendu le code 1.",
        ).toRebuildStatus(200)

        assertEquals(ServerOutageRebuildState.FAILED, status.state)
        assertEquals("Le generateur a rendu le code 1.", status.serverError)
    }

    @Test
    fun refusalBodyIsReadFromTheErrorPayload() {
        val dto = parseSitesHsRebuildBody(
            """{"state":"idle","remaining":0,"retry_after_seconds":42,"reason":"quota_global"}"""
        )

        assertEquals(0, dto?.remaining)
        assertEquals(42, dto?.retryAfterSeconds)
        assertEquals("quota_global", dto?.reason)
    }

    @Test
    fun unreadableBodiesAreIgnoredRatherThanThrown() {
        assertNull(parseSitesHsRebuildBody(null))
        assertNull(parseSitesHsRebuildBody("   "))
        assertNull(parseSitesHsRebuildBody("pas du json"))
    }

    @Test
    fun serverDetailIsPreferredToARawHttpCode() {
        assertEquals(
            "Regeneration des pannes desactivee.",
            parseServerDetail("""{"detail":"Regeneration des pannes desactivee."}"""),
        )
        assertNull(parseServerDetail("""{"autre":"chose"}"""))
        assertNull(parseServerDetail("pas du json"))
    }
}
