package app.core.services.amplitude.experiment.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class ExperimentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(experiment: ExperimentEntity)

    @Query("SELECT * FROM experiments")
    abstract fun getExperiments(): Flow<List<ExperimentEntity>>

    @Query("DELETE FROM experiments WHERE `key` = :key")
    abstract suspend fun delete(key: String)
}