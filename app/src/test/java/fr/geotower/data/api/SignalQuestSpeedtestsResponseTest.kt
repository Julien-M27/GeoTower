package fr.geotower.data.api

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SignalQuestSpeedtestsResponseTest {
    @Test
    fun parseSiteSpeedtestsResponse_documentedShape() {
        val json = """
            {
              "data": [
                {
                  "id": "speedtest_public_example",
                  "timestamp": "2026-04-07T12:00:00.000Z",
                  "coordinates": {"lat": 45.5017, "lng": -73.5673},
                  "downloadSpeed": 420.5,
                  "averageSpeed": 385.2,
                  "maxSpeed": 450.1,
                  "uploadSpeed": 58.4,
                  "ping": 18,
                  "mcc": 302,
                  "mnc": 720,
                  "mobileOperator": "Rogers",
                  "networkType": "CELLULAR",
                  "connectionType": "5G",
                  "deviceType": "Android",
                  "radio": {
                    "enb": "23444",
                    "gnb": null,
                    "cellId": "6001664",
                    "pci": 123,
                    "rsrp": -88,
                    "rsrq": -9,
                    "snr": 22
                  }
                }
              ],
              "meta": {
                "total": 1,
                "limit": 1,
                "offset": 0,
                "bestOnly": true,
                "market": "FR",
                "operator": "ALL"
              },
              "requestId": "req_example_01"
            }
        """.trimIndent()

        val response = Gson().fromJson(json, SqSpeedtestsResponse::class.java)

        assertEquals("req_example_01", response.requestId)
        assertEquals(true, response.meta?.bestOnly)
        assertEquals("FR", response.meta?.market)
        val speedtest = response.data.first()
        assertEquals("speedtest_public_example", speedtest.id)
        assertEquals(420.5f, speedtest.downloadSpeed!!, 0.0f)
        assertEquals(385.2f, speedtest.averageSpeed!!, 0.0f)
        assertEquals(58.4f, speedtest.uploadSpeed!!, 0.0f)
        assertEquals(18f, speedtest.ping!!, 0.0f)
        assertEquals(45.5017, speedtest.coordinates!!.lat!!, 0.0)
        assertEquals("23444", speedtest.radio?.enb)
        assertNotNull(speedtest.timestamp)
    }

    @Test
    fun bestSignalQuestSpeedtest_prefersDocumentedRanking() {
        val olderFastDownload = SqSpeedtestData(
            id = "older-fast-download",
            downloadSpeed = 800f,
            averageSpeed = 300f,
            maxSpeed = 850f,
            timestamp = "2026-04-07T12:00:00.000Z"
        )
        val bestAverage = SqSpeedtestData(
            id = "best-average",
            downloadSpeed = 600f,
            averageSpeed = 450f,
            maxSpeed = 620f,
            timestamp = "2026-04-06T12:00:00.000Z"
        )
        val newerSameAverage = SqSpeedtestData(
            id = "newer-same-average",
            downloadSpeed = 610f,
            averageSpeed = 450f,
            maxSpeed = 620f,
            timestamp = "2026-04-08T12:00:00.000Z"
        )
        val missingAverageFastDownload = SqSpeedtestData(
            id = "missing-average-fast-download",
            downloadSpeed = 900f,
            maxSpeed = 920f,
            timestamp = "2026-04-09T12:00:00.000Z"
        )

        val best = listOf(olderFastDownload, bestAverage, newerSameAverage, missingAverageFastDownload)
            .bestSignalQuestSpeedtest()

        assertEquals("newer-same-average", best?.id)
    }

    @Test
    fun sortedBySignalQuestRanking_putsBestFirst() {
        val slow = SqSpeedtestData(id = "slow", downloadSpeed = 80f, timestamp = "2026-04-09T12:00:00.000Z")
        val fast = SqSpeedtestData(id = "fast", downloadSpeed = 320f, timestamp = "2026-04-07T12:00:00.000Z")

        val sorted = listOf(slow, fast).sortedBySignalQuestRanking()

        assertEquals(listOf("fast", "slow"), sorted.map { it.id })
    }

    @Test
    fun sortedBySignalQuestMetric_sortsDownloadDescendingWithMissingLast() {
        val slow = SqSpeedtestData(id = "slow", downloadSpeed = 80f, averageSpeed = 500f)
        val missing = SqSpeedtestData(id = "missing", averageSpeed = 900f)
        val fast = SqSpeedtestData(id = "fast", downloadSpeed = 320f, averageSpeed = 20f)

        val sorted = listOf(slow, missing, fast).sortedBySignalQuestMetric(
            metric = SignalQuestSpeedtestSortMetric.DOWNLOAD,
            descending = true
        )

        assertEquals(listOf("fast", "slow", "missing"), sorted.map { it.id })
    }

    @Test
    fun sortedBySignalQuestMetric_sortsAverageAscendingWithMissingLast() {
        val fastAverage = SqSpeedtestData(id = "fast-average", averageSpeed = 420f)
        val missing = SqSpeedtestData(id = "missing", downloadSpeed = 900f)
        val slowAverage = SqSpeedtestData(id = "slow-average", averageSpeed = 120f)

        val sorted = listOf(fastAverage, missing, slowAverage).sortedBySignalQuestMetric(
            metric = SignalQuestSpeedtestSortMetric.AVERAGE,
            descending = false
        )

        assertEquals(listOf("slow-average", "fast-average", "missing"), sorted.map { it.id })
    }

    @Test
    fun bestSignalQuestSpeedtestByMetric_canUseDisplayedDownloadValue() {
        val bestAverage = SqSpeedtestData(id = "best-average", averageSpeed = 500f, downloadSpeed = 300f)
        val bestDisplayed = SqSpeedtestData(id = "best-displayed", averageSpeed = 100f, downloadSpeed = 700f)

        val best = listOf(bestAverage, bestDisplayed).bestSignalQuestSpeedtestByMetric(
            SignalQuestSpeedtestSortMetric.DOWNLOAD
        )

        assertEquals("best-displayed", best?.id)
    }

    @Test
    fun filterBySignalQuestPlmn_keepsOnlyRequestedMnc() {
        val orange = SqSpeedtestData(id = "orange", mcc = 208, mnc = 1)
        val orangeMissingMcc = SqSpeedtestData(id = "orange-missing-mcc", mnc = 1)
        val bouygues = SqSpeedtestData(id = "bouygues", mcc = 208, mnc = 20)
        val missingMnc = SqSpeedtestData(id = "missing-mnc", mcc = 208)

        val filtered = listOf(orange, orangeMissingMcc, bouygues, missingMnc)
            .filterBySignalQuestPlmn(SignalQuestPlmnFilter(mcc = 208, mnc = 1))

        assertEquals(listOf("orange", "orange-missing-mcc"), filtered.map { it.id })
    }
}
