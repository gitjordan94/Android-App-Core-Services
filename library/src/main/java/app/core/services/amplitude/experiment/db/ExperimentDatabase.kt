package app.core.services.amplitude.experiment.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExperimentEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class ExperimentDatabase : RoomDatabase() {
    abstract val experiments: ExperimentDao

    internal companion object {
        internal fun create(applicationContext: Context): ExperimentDatabase {
            return Room.databaseBuilder(
                applicationContext,
                ExperimentDatabase::class.java,
                "experiment_database"
            ).build()
        }
    }
}