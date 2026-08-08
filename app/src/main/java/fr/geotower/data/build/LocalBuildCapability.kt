package fr.geotower.data.build

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs

/**
 * Eligibilite d'un appareil a la generation locale de la base, calculee sur des **budgets mesures**
 * plutot que sur des seuils arbitraires.
 *
 * POURQUOI PAS LA RAM TOTALE : le premier gate exigeait « RAM >= 6 Go », ce qui ne mesurait pas la
 * bonne chose. Ce qui plafonne un build, c'est le **tas Java du process**
 * ([ActivityManager.getMemoryClass]) : 128 a 192 Mo sur un 4 Go, 192 a 256 Mo sur un 8 Go — un
 * rapport de 1,3 la ou la RAM totale en affiche 2. Le seuil de 6 Go excluait donc par principe des
 * appareils sur lesquels la generation tient tres bien, et son plancher de stockage (1 Go) etait
 * faux dans l'autre sens : un build complet a besoin de ~1,2 Go et echouait donc tard, apres avoir
 * consomme la data et la batterie.
 *
 * MESURES DE REFERENCE (Galaxy A52s, tas max 256 Mo, 2026-08-06, apres les optimisations R3 et S3,
 * cf. `docs/agent-ia-plan-optimisation-generation-locale-db-2026-08-05.md`) :
 *  - mobile seul : pic de tas 110 Mo, pic de stockage 828 Mo, 28 min ;
 *  - tous packs : pic de tas 112 Mo, pic de stockage 1157 Mo, 45 min.
 *
 * Le pic de tas est quasi identique selon les packs (le poste lourd est commun aux deux builds), le
 * stockage non : d'ou un budget memoire unique et un budget disque **par pack**.
 *
 * La decision pure ([evaluate] avec des valeurs) est testable en JVM ; la lecture des capacites
 * reelles ([evaluate] avec un [Context]) touche les API Android.
 */
object LocalBuildCapability {

    private const val MIB = 1024L * 1024L

    /** Ce qu'une generation reclame a l'appareil, marges comprises. */
    data class Budget(val heapBytes: Long, val storageBytes: Long)

    /** Pic de tas mesure, commun a tous les packs. */
    private const val HEAP_PEAK_BYTES = 112L * MIB

    private const val STORAGE_PEAK_MOBILE_BYTES = 828L * MIB
    private const val STORAGE_PEAK_ALL_BYTES = 1157L * MIB

    /**
     * Pack radio seul : **estime**, pas encore mesure. Deduit de l'ecart entre le build complet et
     * le build mobile seul (~330 Mo de staging radio), plus le ZIP SUP et la base produite, arrondi
     * genereusement vers le haut. A remplacer par une vraie mesure.
     */
    private const val STORAGE_PEAK_RADIO_BYTES = 600L * MIB

    /**
     * Marge sur le tas : le pic mesure est de la memoire **occupee** (dechets non encore ramasses
     * compris), pas la taille du vif. Un appareil au plafond plus bas ramasserait plus souvent et
     * afficherait donc un pic plus faible — la marge protege quand meme des appareils ou le
     * ramasse-miettes n'aurait plus de quoi respirer.
     */
    private const val HEAP_MARGIN_PERCENT = 140L

    /** Marge sur le stockage : manquer de place est un echec sec (et tardif), pas un ralentissement. */
    private const val STORAGE_MARGIN_PERCENT = 125L

    fun budgetFor(mobile: Boolean, radio: Boolean): Budget {
        val storagePeak = when {
            mobile && radio -> STORAGE_PEAK_ALL_BYTES
            radio -> STORAGE_PEAK_RADIO_BYTES
            else -> STORAGE_PEAK_MOBILE_BYTES
        }
        return Budget(
            heapBytes = HEAP_PEAK_BYTES * HEAP_MARGIN_PERCENT / 100L,
            storageBytes = storagePeak * STORAGE_MARGIN_PERCENT / 100L,
        )
    }

    data class Eligibility(
        val eligible: Boolean,
        val totalRamBytes: Long,
        val freeStorageBytes: Long,
        val heapLimitBytes: Long,
        val required: Budget,
        val reason: String?,
    )

    /**
     * Decision pure. `lowRamDevice` reste bloquant : c'est le systeme qui declare l'appareil
     * contraint en memoire, et lui imposer 30 a 45 minutes de service au premier plan n'a pas de
     * sens. L'utilisateur garde la possibilite de forcer depuis l'ecran (cf. la carte de reglages).
     */
    fun evaluate(
        totalRamBytes: Long,
        freeStorageBytes: Long,
        heapLimitBytes: Long,
        lowRamDevice: Boolean,
        required: Budget,
    ): Eligibility {
        val reasons = buildList {
            if (lowRamDevice) add("appareil signalé comme faible en RAM")
            if (heapLimitBytes < required.heapBytes) {
                add("mémoire d'application insuffisante (${heapLimitBytes / MIB} Mo pour ${required.heapBytes / MIB} Mo nécessaires)")
            }
            if (freeStorageBytes < required.storageBytes) {
                add("stockage libre insuffisant (${freeStorageBytes / MIB} Mo pour ${required.storageBytes / MIB} Mo nécessaires)")
            }
        }
        return Eligibility(
            eligible = reasons.isEmpty(),
            totalRamBytes = totalRamBytes,
            freeStorageBytes = freeStorageBytes,
            heapLimitBytes = heapLimitBytes,
            required = required,
            reason = reasons.joinToString(", ").ifEmpty { null },
        )
    }

    /**
     * Lecture des capacites reelles pour les packs demandes. Le defaut (mobile seul) est le pack de
     * reference : il repond a la question « cet appareil peut-il generer quelque chose ? », posee
     * par les ecrans qui se contentent d'afficher ou non la fonctionnalite.
     */
    fun evaluate(context: Context, mobile: Boolean = true, radio: Boolean = false): Eligibility {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val workDir = context.noBackupFilesDir ?: context.filesDir
        val freeBytes = StatFs(workDir.absolutePath).availableBytes
        return evaluate(
            totalRamBytes = memoryInfo.totalMem,
            freeStorageBytes = freeBytes,
            heapLimitBytes = activityManager.memoryClass.toLong() * MIB,
            lowRamDevice = activityManager.isLowRamDevice,
            required = budgetFor(mobile, radio),
        )
    }
}
