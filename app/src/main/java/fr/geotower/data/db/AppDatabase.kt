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
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // On branche notre nouveau DAO (adieu l'ancien AntennaDao !)
    abstract fun geoTowerDao(): GeoTowerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geotower.db" // ⚠️ TRÈS IMPORTANT : C'est le nom exact du fichier téléchargé
                )
                    .fallbackToDestructiveMigration()
                    .build()
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