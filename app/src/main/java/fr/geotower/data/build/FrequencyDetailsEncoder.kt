package fr.geotower.data.build

import java.io.ByteArrayOutputStream
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
        val encoded = COMPRESSED_PREFIX + base64(output.toByteArray())
        return if (encoded.length < value.length) encoded else value
    }

    /**
     * Base64 standard (alphabet `+/`, avec remplissage `=`, sans retour a la ligne), ecrit a la
     * main comme le decodeur de [fr.geotower.data.models.FrequencyDetailsCodec].
     *
     * POURQUOI PAS `java.util.Base64` : elle exige l'API 26 alors que le `minSdk` de l'app est 24.
     * Sur Android 7.0 / 7.1 la generation locale plantait donc en `NoClassDefFoundError` des la
     * premiere station compressee. Et pas `android.util.Base64` non plus : le builder est prouve
     * par des tests JVM (parite avec le builder serveur), ou les classes Android ne sont que des
     * souches. Sortie identique a l'octet pres a celle de `java.util.Base64` — c'est ce que
     * verifient le round-trip de `FrequencyDetailsEncoderTest` et les instantanes de
     * `BuilderOutputSnapshotTest`, dont les blobs `Z1:` sont figes.
     */
    private fun base64(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 2 < bytes.size) {
            val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            out.append(ALPHABET[(chunk ushr 18) and 0x3F])
            out.append(ALPHABET[(chunk ushr 12) and 0x3F])
            out.append(ALPHABET[(chunk ushr 6) and 0x3F])
            out.append(ALPHABET[chunk and 0x3F])
            index += 3
        }
        when (bytes.size - index) {
            1 -> {
                val chunk = (bytes[index].toInt() and 0xFF) shl 16
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append("==")
            }
            2 -> {
                val chunk = ((bytes[index].toInt() and 0xFF) shl 16) or
                    ((bytes[index + 1].toInt() and 0xFF) shl 8)
                out.append(ALPHABET[(chunk ushr 18) and 0x3F])
                out.append(ALPHABET[(chunk ushr 12) and 0x3F])
                out.append(ALPHABET[(chunk ushr 6) and 0x3F])
                out.append('=')
            }
        }
        return out.toString()
    }

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
}
