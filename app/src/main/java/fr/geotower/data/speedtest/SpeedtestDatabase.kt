package fr.geotower.data.speedtest

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SpeedtestEntryEntity::class],
    version = 2,
    exportSchema = true
)
abstract class SpeedtestDatabase : RoomDatabase() {
    abstract fun speedtestDao(): SpeedtestDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedtestDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE speedtests ADD COLUMN networkName TEXT")
            }
        }

        fun getDatabase(context: Context): SpeedtestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedtestDatabase::class.java,
                    "speedtests.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
