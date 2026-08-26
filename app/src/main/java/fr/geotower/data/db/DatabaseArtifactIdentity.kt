package fr.geotower.data.db

/**
 * Identite d'une base publiee dans le manifeste signe.
 *
 * Le SHA-256 est l'identite exacte du fichier. La version est conservee comme identite
 * semantique de repli : le principal et le miroir peuvent produire deux fichiers SQLite
 * differents pour le meme jeu de donnees (index ou metadonnees de build), mais doivent publier
 * la meme version de donnees.
 */
data class DatabaseArtifactIdentity(
    val version: String?,
    val sha256: String?
)

object DatabaseArtifactIdentityPolicy {
    /**
     * Deux publications sont la meme base si leurs octets sont identiques, ou si elles portent
     * la meme version de donnees. Le second cas rend la comparaison independante de l'hote qui
     * a construit puis servi le fichier.
     */
    fun matches(
        proposed: DatabaseArtifactIdentity,
        installed: DatabaseArtifactIdentity
    ): Boolean {
        val proposedHash = proposed.sha256?.trim().orEmpty()
        val installedHash = installed.sha256?.trim().orEmpty()
        if (proposedHash.isNotEmpty() && proposedHash.equals(installedHash, ignoreCase = true)) {
            return true
        }
        return DatabaseVersionPolicy.areEquivalent(proposed.version, installed.version)
    }
}
