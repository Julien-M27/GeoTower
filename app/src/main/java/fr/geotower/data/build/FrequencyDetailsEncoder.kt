package fr.geotower.data.build

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Encodeur du blob `technique.details_frequences`. Miroir en ecriture de
 * [fr.geotower.data.models.FrequencyDetailsCodec] (decode) et port de
 * `compress_frequency_details` (docs/server/build_fr_anfr_db.py).
 *
 * Format : `"Z1:" + base64(zlib(texte, niveau 9))`, conserve uniquement si plus
 * court que le texte brut ; sinon le texte brut est stocke tel quel.
 */
object FrequencyDetailsEncoder {
    private const val COMPRESSED_PREFIX = "Z1:"

    /** Retourne null pour une entree vide (comme Python `if not value: return None`). */
    fun encode(value: String?): String? {
        if (value.isNullOrEmpty()) return null

        val output = ByteArrayOutputStream()
        DeflaterOutputStream(output, Deflater(Deflater.BEST_COMPRESSION)).use { deflater ->
            deflater.write(value.toByteArray(Charsets.UTF_8))
        }
        val encoded = COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray())
        return if (encoded.length < value.length) encoded else value
    }
}
