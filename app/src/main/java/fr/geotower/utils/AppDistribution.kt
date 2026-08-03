package fr.geotower.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * D'où vient l'installation courante de GeoTower.
 *
 * Google Play interdit à une application qu'il distribue de proposer son propre canal de mise à
 * jour (règle « Device and Network Abuse ») : pour ces installations, la vérification de version,
 * le bandeau du tiroir, la notification et le lien de téléchargement direct doivent disparaître —
 * c'est le Play Store qui met l'app à jour. Les installations chargées à la main (APK direct)
 * gardent le canal historique, sinon ces utilisateurs n'auraient plus aucun moyen d'être prévenus.
 *
 * Résolu une fois au démarrage du process ([GeoTowerApp]) : l'origine d'une installation ne change
 * pas à chaud, et [PackageManager.getInstallSourceInfo] est un appel binder qu'on ne veut pas sur
 * un chemin d'UI.
 */
object AppDistribution {

    /** Installateurs considérés comme « c'est le Play Store qui gère les mises à jour ». */
    private val PLAY_INSTALLERS = setOf(
        "com.android.vending",
        // Ancien paquet utilisé par certains flux Play (partage d'app, images système anciennes).
        "com.google.android.feedback"
    )

    private var installed = false

    /**
     * Vrai si l'APK courant a été installé par le Play Store.
     *
     * Vaut `false` tant que [init] n'a pas tourné : le repli est le comportement historique
     * (canal de mise à jour actif), jamais l'inverse.
     */
    var isPlayInstall: Boolean = false
        private set

    fun init(context: Context) {
        if (installed) return
        installed = true
        isPlayInstall = resolveInstallerPackage(context.applicationContext) in PLAY_INSTALLERS
    }

    private fun resolveInstallerPackage(context: Context): String? {
        val packageManager = context.packageManager
        val packageName = context.packageName
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        }.getOrNull()
    }
}
