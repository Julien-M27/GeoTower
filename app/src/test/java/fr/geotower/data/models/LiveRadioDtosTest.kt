package fr.geotower.data.models

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRadioDtosTest {

    @Test
    fun radioMarkerDeserializationAndMapping() {
        val json = """
            {
              "country_code": "FR",
              "markers": [
                {
                  "id": "RADIO_0750000001_555123",
                  "latitude": 48.8566,
                  "longitude": 2.3522,
                  "service_mask": 1,
                  "system_mask": 1,
                  "actor_label": "TDF",
                  "emitter_count": 3,
                  "antenna_count": 2,
                  "min_freq_khz": 87600,
                  "max_freq_khz": 107900,
                  "cluster_count": 1,
                  "detail_text": "Systemes: FM",
                  "station_id": "0750000001",
                  "support_id": "555123"
                },
                {
                  "id": "RADIO_CLUSTER_48_2",
                  "latitude": 48.5,
                  "longitude": 2.5,
                  "service_mask": 1,
                  "system_mask": 1,
                  "emitter_count": 10,
                  "antenna_count": 5,
                  "cluster_count": 4
                }
              ]
            }
        """.trimIndent()

        val response = Gson().fromJson(json, LiveRadioMarkersResponseDto::class.java)
        val markers = response.markers.mapNotNull { it.toRadioMapMarker() }
        assertEquals(2, markers.size)

        val raw = markers[0]
        assertEquals("RADIO_0750000001_555123", raw.id)
        assertEquals("TDF", raw.actorLabel)
        assertEquals("Systemes: FM", raw.detailText)
        assertEquals("0750000001", raw.stationId)
        assertEquals("555123", raw.supportId)

        val cluster = markers[1]
        assertTrue(cluster.isCluster)
        assertEquals(4, cluster.clusterCount)
        assertNull(cluster.detailText)
    }
}
