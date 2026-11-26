package fr.geotower.data.build

/**
 * Moteur utilise pour la generation locale des bases de donnees.
 * - [KOTLIN] : pipeline Kotlin + SQLite Android historique.
 * - [RUST] : moteur natif Rust + SQLite embarque via JNI.
 */
enum class LocalBuildEngine(val id: String) {
    KOTLIN("kotlin"),
    RUST("rust"),
    ;

    companion object {
        fun fromId(id: String?): LocalBuildEngine =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: KOTLIN
    }
}
