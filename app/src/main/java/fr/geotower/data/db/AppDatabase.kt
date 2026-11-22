package fr.geotower.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.geotower.data.models.AntenneDbEntity
import fr.geotower.data.models.LocalisationDbEntity
import fr.geotower.data.models.MetadataDbEntity
import fr.geotower.data.models.RadioStatCurrentDbEntity
import fr.geotower.data.models.RadioStatWeeklyDbEntity
import fr.geotower.data.models.RefCommuneDbEntity
import fr.geotower.data.models.RefExploitantDbEntity
import fr.geotower.data.models.RefNatureDbEntity
import fr.geotower.data.models.RefOperateurDbEntity
import fr.geotower.data.models.RefProprietaireDbEntity
import fr.geotower.data.models.RefStatutDbEntity
import fr.geotower.data.models.RefSystemeDbEntity
import fr.geotower.data.models.RefTypeAntenneDbEntity
import fr.geotower.data.models.SupportDbEntity
import fr.geotower.data.models.TechniqueDbEntity

@Database(
    entities = [
        LocalisationDbEntity::class,
        TechniqueDbEntity::class,
        SupportDbEntity::class,
        AntenneDbEntity::class,
        RefOperateurDbEntity::class,
        RefNatureDbEntity::class,
        RefProprietaireDbEntity::class,
        RefExploitantDbEntity::class,
        RefTypeAntenneDbEntity::class,
        RefSystemeDbEntity::class,
        RefStatutDbEntity::class,
        RefCommuneDbEntity::class,
        MetadataDbEntity::class,
        RadioStatCurrentDbEntity::class,
        RadioStatWeeklyDbEntity::class
    ],
    version = GeoTowerDatabaseValidator.EXPECTED_SCHEMA_VERSION,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun geoTowerDao(): GeoTowerDao

    companion object {
        private val INSTANCES = java.util.EnumMap<DatabaseCountry, AppDatabase>(DatabaseCountry::class.java)

        fun getDatabase(
            context: Context,
            country: DatabaseCountry = DatabaseCountryStore.getActive(context)
        ): AppDatabase {
            INSTANCES[country]?.let { return it }
            return synchronized(this) {
                INSTANCES[country]?.let { return it }
                val appContext = context.applicationContext
                val fileStatus = GeoTowerDatabaseValidator.getInstalledDatabaseFileStatus(appContext, country)
                if (fileStatus.state != GeoTowerDatabaseValidator.LocalDatabaseState.VALID) {
                    closeDatabase(country)
                    throw InvalidGeoTowerDatabaseException(fileStatus.reason ?: "Base GeoTower absente")
                }

                val instance = Room.databaseBuilder(
                    appContext,
                    AppDatabase::class.java,
                    country.dbFileName
                )
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            // Index de performance (re)créés à chaque ouverture, en idempotent.
                            // Impossible via @Index : la base est un fichier préconstruit téléchargé
                            // dont le hash de schéma Room doit rester figé. Voir GeoTowerDatabaseIndexes.
                            GeoTowerDatabaseIndexes.apply(db)
                        }
                    })
                    .build()
                try {
                    instance.openHelper.readableDatabase
                    GeoTowerDatabaseValidator.clearInstalledDatabaseInvalid(appContext, country)
                } catch (e: Exception) {
                    instance.close()
                    GeoTowerDatabaseValidator.markInstalledDatabaseInvalid(
                        appContext,
                        e.message ?: "Schema Room incompatible",
                        country
                    )
                    throw InvalidGeoTowerDatabaseException(
                        "Schema Room incompatible avec ${country.dbFileName}",
                        e
                    )
                }
                INSTANCES[country] = instance
                instance
            }
        }

        fun closeDatabase(country: DatabaseCountry? = null) {
            synchronized(this) {
                if (country != null) {
                    INSTANCES.remove(country)?.close()
                } else {
                    INSTANCES.values.forEach { it.close() }
                    INSTANCES.clear()
                }
            }
        }
    }
}

class InvalidGeoTowerDatabaseException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
