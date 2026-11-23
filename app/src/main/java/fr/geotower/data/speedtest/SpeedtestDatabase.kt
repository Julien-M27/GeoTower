package fr.geotower.data.speedtest

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SpeedtestEntryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class SpeedtestDatabase : RoomDatabase() {
    abstract fun speedtestDao(): SpeedtestDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedtestDatabase? = null

        fun getDatabase(context: Context): SpeedtestDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpeedtestDatabase::class.java,
                    "speedtests.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
