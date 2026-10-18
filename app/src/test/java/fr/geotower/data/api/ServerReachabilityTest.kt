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

    @Test
    fun pingResultWithPartialSamplesCalculatesCorrectMetrics() {
        val partial = ServerPingResult(
            server = ApiServer.MIRROR,
            requestedPasses = 10,
            samples = listOf(
                ServerPingSample(attempt = 1, latencyMs = 15L, responseCode = 200),
                ServerPingSample(attempt = 2, latencyMs = 25L, responseCode = 200)
            )
        )

        assertEquals(2, partial.successfulPasses)
        assertEquals(8, partial.failedPasses)
        assertEquals(listOf(15L, 25L), partial.latenciesMs)
        assertEquals(15L, partial.minimumMs)
        assertEquals(25L, partial.maximumMs)
        assertEquals(20L, partial.averageMs)
    }

    @Test
    fun pingResultWithNoSuccessfulSamplesReturnsNullStats() {
        val emptyResult = ServerPingResult(
            server = ApiServer.PRIMARY,
            requestedPasses = 5,
            samples = listOf(
                ServerPingSample(attempt = 1, latencyMs = null),
                ServerPingSample(attempt = 2, latencyMs = null)
            )
        )

        assertEquals(0, emptyResult.successfulPasses)
        assertEquals(5, emptyResult.failedPasses)
        assertEquals(emptyList<Long>(), emptyResult.latenciesMs)
        assertEquals(null, emptyResult.minimumMs)
        assertEquals(null, emptyResult.maximumMs)
        assertEquals(null, emptyResult.averageMs)
    }

    @Test
    fun runPingWithInitialSamplesCompletingAllPassesReturnsImmediateResult() = kotlinx.coroutines.runBlocking {
        val initialSamples = listOf(
            ServerPingSample(attempt = 1, latencyMs = 12L, responseCode = 200),
            ServerPingSample(attempt = 2, latencyMs = 14L, responseCode = 200),
            ServerPingSample(attempt = 3, latencyMs = 16L, responseCode = 200),
            ServerPingSample(attempt = 4, latencyMs = 18L, responseCode = 200),
            ServerPingSample(attempt = 5, latencyMs = 20L, responseCode = 200)
        )
        val result = ServerReachability.runPing(
            server = ApiServer.PRIMARY,
            passes = 5,
            initialSamples = initialSamples
        )
        assertEquals(5, result.requestedPasses)
        assertEquals(5, result.successfulPasses)
        assertEquals(initialSamples, result.samples)
        assertEquals(16L, result.averageMs)
    }
}
