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
        // BEST_SPEED (et non BEST_COMPRESSION) : la compression zlib est appelee des centaines de
        // milliers de fois (une par station/support) pendant le build on-device ; le niveau 9 y est
        // un point chaud CPU inutile. Le blob est a peine plus gros mais l'app le relit a l'identique
        // (inflate gere tous les niveaux) -> gain de vitesse sans impact fonctionnel. Serveur = niveau 9.
        //
        // IMPORTANT : `Deflater` detient de la memoire NATIVE (zlib) qui n'est liberee que par `end()`.
        // `DeflaterOutputStream(out, deflater)` (deflater fourni) ne l'appelle PAS a la fermeture ; sans
        // `end()` explicite, ~700 000 deflaters s'accumulent -> OutOfMemoryError (Deflater.init natif).
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            DeflaterOutputStream(output, deflater).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        } finally {
            deflater.end()
        }
        val encoded = COMPRESSED_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray())
        return if (encoded.length < value.length) encoded else value
    }
}
