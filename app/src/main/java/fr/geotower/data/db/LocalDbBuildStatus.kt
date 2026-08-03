package fr.geotower.data.db

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.geotower.data.workers.LocalDbBuildWorker

/**
 * Etat « une generation locale de base est en cours » (worker [LocalDbBuildWorker]).
 *
 * Sert a ne **jamais** annoncer une base *manquante* alors qu'elle est justement en train d'etre
 * generee sur l'appareil : le pipeline construit un fichier `.localbuild` et ne l'installe qu'a la
 * toute fin, donc pendant tout le build la base reste absente du disque. Sans ce garde-fou,
 * l'accueil, les reglages, « A propos » et le diagnostic affichent « base manquante » pendant des
 * dizaines de minutes alors que tout se passe bien.
 *
 * Le pack en cours est lu dans les **tags** du worker : `WorkInfo` ne transporte pas les donnees
 * d'entree, mais les tags sont disponibles des l'etat ENQUEUED.
 */
data class LocalDbBuildStatus(
    val running: Boolean,
    val mobile: Boolean,
    val radio: Boolean,
    val progress: Int,
) {
    /** Une generation en cours produira la base mobile (`geotower_fr.db`). */
    val mobileRunning: Boolean get() = running && mobile

    /** Une generation en cours produira la base radio (`geotower_fr_radio.db`). */
    val radioRunning: Boolean get() = running && radio

    companion object {
        val IDLE = LocalDbBuildStatus(running = false, mobile = false, radio = false, progress = 0)

        fun fromWorkInfos(infos: List<WorkInfo>): LocalDbBuildStatus {
            val info = infos.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            } ?: return IDLE
            val mobileTagged = info.tags.contains(LocalDbBuildWorker.TAG_PACK_MOBILE)
            val radioTagged = info.tags.contains(LocalDbBuildWorker.TAG_PACK_RADIO)
            // Build lance par une version anterieure (sans tag de pack) : on ignore ce qu'il produit,
            // on couvre donc les deux bases plutot que d'en annoncer une « manquante » a tort.
            val untagged = !mobileTagged && !radioTagged
            return LocalDbBuildStatus(
                running = true,
                mobile = mobileTagged || untagged,
                radio = radioTagged || untagged,
                progress = info.progress.getInt(LocalDbBuildWorker.KEY_PROGRESS, 0),
            )
        }

        /** Lecture bloquante (diagnostic, code non-Compose) : a appeler hors du thread principal. */
        fun read(context: Context): LocalDbBuildStatus = runCatching {
            fromWorkInfos(
                WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork(LocalDbBuildWorker.UNIQUE_WORK_NAME)
                    .get(),
            )
        }.getOrDefault(IDLE)
    }
}
