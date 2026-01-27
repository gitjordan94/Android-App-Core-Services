package app.core.services.amplitude.experiment.util

import app.core.services.amplitude.experiment.ExperimentVariant
import app.core.services.amplitude.experiment.db.ExperimentEntity
import com.amplitude.experiment.Variant

internal fun ExperimentVariant.toExperimentEntity(): ExperimentEntity {
    return ExperimentEntity(
        key = key,
        value = value,
        payload = payloadJson
    )
}

internal fun ExperimentVariant.toAmplitudeVariant(): Variant {
    return Variant(
        value = value,
        payload = payloadJson
    )
}