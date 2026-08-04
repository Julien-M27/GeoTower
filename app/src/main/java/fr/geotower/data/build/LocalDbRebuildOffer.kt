package fr.geotower.data.build

import android.content.Context
import fr.geotower.data.db.LocalDbProvenance
import fr.geotower.utils.AppConfig

/**
 * « Mettre a jour la base » ne veut pas dire la meme chose pour tout le monde : celui qui a genere
 * la sienne sur l'appareil n'a rien a retelecharger, il la **regenere** depuis les sources ANFR.
 * Cet objet dit si une nouvelle version disponible doit lui etre proposee en regeneration plutot
 * qu'en telechargement (bandeau d'accueil, notification de mise a jour, carte des reglages).
 *
 * Deux cas, l'un de droit et l'autre de fait :
 * - le cran de traitement local impose deja la generation locale ([AppConfig.dbForcedLocal]) :
 *   le telechargement serveur est bloque, une regeneration est le SEUL moyen de se mettre a jour ;
 * - ou la base installee vient d'un build local (cf. [LocalDbProvenance]) **et** l'appareil est
 *   toujours eligible (cf. [LocalBuildCapability]) — sinon on proposerait une action que la carte
 *   de generation refuse de lancer (stockage retombe sous le seuil, par ex.), et l'utilisateur se
 *   retrouverait sans aucun moyen de mettre sa base a jour.
 *
 * Lecture disque + SQLite : a appeler hors du thread principal.
 */
object LocalDbRebuildOffer {

    /** La base **mobile** (`geotower_fr.db`) doit-elle etre regeneree plutot que retelechargee ? */
    fun forMobile(context: Context): Boolean =
        AppConfig.dbForcedLocal() || forMobile(context, LocalDbProvenance.readMobile(context))

    /** Variante pour les appelants qui ont deja lu la provenance (evite une 2e ouverture SQLite). */
    fun forMobile(context: Context, mobileProvenance: LocalDbProvenance.Info): Boolean =
        AppConfig.dbForcedLocal() ||
            (mobileProvenance.locallyBuilt && LocalBuildCapability.evaluate(context).eligible)
}
