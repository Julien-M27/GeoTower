package fr.geotower.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class ServerReachabilityTest {

    @Test
    fun pingResultAggregatesSuccessfulAndFailedPasses() {
        val result = ServerPingResult(
            server = ApiServer.PRIMARY,
            requestedPasses = 5,
            samples = listOf(
                ServerPingSample(attempt = 1, latencyMs = 20L, responseCode = 200),
                ServerPingSample(attempt = 2, latencyMs = null),
                ServerPingSample(attempt = 3, latencyMs = 22L, responseCode = 200),
                ServerPingSample(attempt = 4, latencyMs = 21L, responseCode = 200),
                ServerPingSample(attempt = 5, latencyMs = null, responseCode = 503)
            )
        )

        assertEquals(3, result.successfulPasses)
        assertEquals(2, result.failedPasses)
        assertEquals(listOf(20L, 22L, 21L), result.latenciesMs)
        assertEquals(20L, result.minimumMs)
        assertEquals(22L, result.maximumMs)
        assertEquals(21L, result.averageMs)
    }
}
