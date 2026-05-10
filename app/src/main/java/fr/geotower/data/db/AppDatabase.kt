package fr.geotower.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import fr.geotower.data.models.FaisceauxEntity
import fr.geotower.data.models.LocalisationEntity
import fr.geotower.data.models.PhysiqueEntity
import fr.geotower.data.models.TechniqueEntity
// (Tu peux aussi supprimer l'import de MetadataEntity en haut)

@Database(
    entities = [
        LocalisationEntity::class,
        TechniqueEntity::class,
        PhysiqueEntity::class,
        FaisceauxEntity::class
        // ✅ ON A SUPPRIMÉ METADATA ICI !
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    // On branche notre nouveau DAO (adieu l'ancien AntennaDao !)
    abstract fun geoTowerDao(): GeoTowerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                // Keep the hot path cheap: the full integrity check is done on download/splash/settings.
                // Here we only prevent Room from creating an empty replacement when geotower.db is absent.
                val fileStatus = GeoTowerDatabaseValidator.getInstalledDatabaseFileStatus(appContext)
                if (fileStatus.state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                    closeDatabase()
                    throw InvalidGeoTowerDatabaseException(fileStatus.reason ?: "Base GeoTower absente")
                }

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    "geotower.db" // ⚠️ TRÈS IMPORTANT : C'est le nom exact du fichier téléchargé
                )
                    .build()
                try {
                    instance.openHelper.readableDatabase
                    GeoTowerDatabaseValidator.clearInstalledDatabaseInvalid(appContext)
                } catch (e: Exception) {
                    instance.close()
                    GeoTowerDatabaseValidator.markInstalledDatabaseInvalid(
                        appContext,
                        e.message ?: "Schema Room incompatible"
                    )
                    throw InvalidGeoTowerDatabaseException("Schema Room incompatible avec geotower.db", e)
                }
                INSTANCE = instance
                instance
            }
        }

        // 🎯 Voici la fameuse fonction qui manquait pour ton bouton !
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

class InvalidGeoTowerDatabaseException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
