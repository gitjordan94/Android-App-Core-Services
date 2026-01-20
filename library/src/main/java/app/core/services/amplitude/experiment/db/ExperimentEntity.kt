package app.core.services.amplitude.experiment.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "experiments")
data class ExperimentEntity(
    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "key")
    val key: String,

    @ColumnInfo(name = "value")
    val value: String?,

    @ColumnInfo(name = "payload")
    val payload: String?,
)