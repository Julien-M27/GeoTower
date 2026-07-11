package fr.geotower.data.build

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * Eligibilite d'un appareil a la generation locale de la base. Deux criteres bloquants
 * **uniquement** (decision produit) : RAM >= 6 Go et stockage libre >= 1 Go. Le reste
 * (Wi-Fi, batterie) est un simple avertissement non bloquant, gere cote UI.
 *
 * La decision pure ([evaluate] avec des valeurs) est testable en JVM ; la lecture des
 * capacites reelles ([evaluate] avec un [Context]) touche les API Android.
 */
object LocalBuildCapability {

    private const val GIB = 1024L * 1024L * 1024L

    /** Seuil RAM, exprime en GiB (arrondi au GiB superieur, cf. [evaluate]). */
    const val MIN_TOTAL_RAM_GIB = 6L

    /** Seuil de stockage libre : 1 GiB. */
    const val MIN_FREE_STORAGE_BYTES = 1L * GIB

    data class Eligibility(
        val eligible: Boolean,
        val totalRamBytes: Long,
        val freeStorageBytes: Long,
        val reason: String?,
    )

    /**
     * Decision pure. `totalRamBytes` est arrondi au GiB **superieur** : un appareil "6 Go"
     * rapporte un `totalMem` inferieur a 6 GiB (reserve noyau, souvent ~5,5-5,9 GiB), il ne
     * faut donc pas exiger un compte exact de 6 GiB sous peine d'exclure les vrais 6 Go.
     */
    fun evaluate(totalRamBytes: Long, freeStorageBytes: Long, lowRamDevice: Boolean): Eligibility {
        val ramGib = (totalRamBytes + GIB - 1) / GIB
        val reasons = buildList {
            if (lowRamDevice) add("appareil signalé comme faible en RAM")
            if (ramGib < MIN_TOTAL_RAM_GIB) add("RAM insuffisante (< 6 Go)")
            if (freeStorageBytes < MIN_FREE_STORAGE_BYTES) add("stockage libre insuffisant (< 1 Go)")
        }
        return Eligibility(
            eligible = reasons.isEmpty(),
            totalRamBytes = totalRamBytes,
            freeStorageBytes = freeStorageBytes,
            reason = reasons.joinToString(", ").ifEmpty { null },
        )
    }

    /** Lecture des capacites reelles de l'appareil (RAM totale, stockage libre du volume de travail). */
    fun evaluate(context: Context): Eligibility {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val workDir = context.cacheDir ?: context.filesDir
        val freeBytes = StatFs(workDir.absolutePath).availableBytes
        return evaluate(memoryInfo.totalMem, freeBytes, activityManager.isLowRamDevice)
    }
}
