package fr.geotower.data.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripGeometryCodecTest {
    @Test
    fun roundTripsAPathWithinTheAdvertisedPrecision() {
        val points = listOf(
            doubleArrayOf(48.853606, 2.349176),
            doubleArrayOf(48.853152, 2.350561),
            doubleArrayOf(48.852714, 2.351952),
            doubleArrayOf(48.855069, 2.350282)
        )

        val decoded = TripGeometryCodec.decode(TripGeometryCodec.encode(points))

        assertEquals(points.size, decoded.size)
        points.forEachIndexed { index, point ->
            // Précision 5 : l'écart admis est l'arrondi au cent-millième de degré.
            assertEquals(point[0], decoded[index][0], 1e-5)
            assertEquals(point[1], decoded[index][1], 1e-5)
        }
    }

    @Test
    fun handlesNegativeAndCrossingCoordinates() {
        val points = listOf(
            doubleArrayOf(-16.264, -61.551),
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(48.8534, -1.5),
            doubleArrayOf(-0.00001, 0.00001)
        )

        val decoded = TripGeometryCodec.decode(TripGeometryCodec.encode(points))

        assertEquals(points.size, decoded.size)
        points.forEachIndexed { index, point ->
            assertEquals(point[0], decoded[index][0], 1e-5)
            assertEquals(point[1], decoded[index][1], 1e-5)
        }
    }

    @Test
    fun encodesEmptyPathsAsEmptyStrings() {
        assertEquals("", TripGeometryCodec.encode(emptyList()))
        assertEquals(emptyList<DoubleArray>(), TripGeometryCodec.decode(""))
    }

    @Test
    fun staysCompactComparedToRawCoordinates() {
        // Un tracé réaliste : ~2 000 points, ce que rend un segment routier de quelques dizaines de
        // kilomètres. C'est la raison d'être du codec, donc elle mérite d'être vérifiée.
        val points = List(2_000) { doubleArrayOf(48.80 + it * 0.0001, 2.30 + it * 0.0001) }

        val encoded = TripGeometryCodec.encode(points)
        val rawSize = points.sumOf { "[${it[0]},${it[1]}]," .length }

        assertTrue("encodé=${encoded.length} brut=$rawSize", encoded.length < rawSize / 4)
    }

    @Test
    fun stopsAtTheLastCompleteValueWhenTruncated() {
        val encoded = TripGeometryCodec.encode(
            listOf(
                doubleArrayOf(48.85, 2.35),
                doubleArrayOf(48.86, 2.36),
                doubleArrayOf(48.87, 2.37)
            )
        )

        // Une chaîne coupée doit rendre ce qu'elle contient, pas lever : un fichier de trajets un peu
        // abîmé doit coûter un tracé approximatif, jamais la perte de la tournée.
        val decoded = TripGeometryCodec.decode(encoded.dropLast(1))

        assertTrue(decoded.size in 1..3)
        assertEquals(48.85, decoded.first()[0], 1e-5)
    }
}
