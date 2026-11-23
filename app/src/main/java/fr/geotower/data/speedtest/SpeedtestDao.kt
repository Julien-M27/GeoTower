package fr.geotower.data.speedtest

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedtestDao {
    @Query("SELECT * FROM speedtests ORDER BY testedAtMillis DESC, createdAtMillis DESC")
    fun observeAll(): Flow<List<SpeedtestEntryEntity>>

    @Query("SELECT * FROM speedtests ORDER BY testedAtMillis DESC, createdAtMillis DESC")
    suspend fun getAllOnce(): List<SpeedtestEntryEntity>

    @Query("SELECT * FROM speedtests WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SpeedtestEntryEntity?

    @Query("SELECT * FROM speedtests WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<SpeedtestEntryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: SpeedtestEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<SpeedtestEntryEntity>)

    @Query("DELETE FROM speedtests WHERE id = :id")
    suspend fun deleteById(id: String)
}
