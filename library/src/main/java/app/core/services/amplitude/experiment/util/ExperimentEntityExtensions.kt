package app.core.services.amplitude.experiment.util

import app.core.services.amplitude.experiment.ExperimentVariant
import app.core.services.amplitude.experiment.db.ExperimentEntity

internal fun ExperimentEntity.toExperimentVariant(): ExperimentVariant {
    return ExperimentVariant(
        key = key,
        value = value,
        payloadJson = payload,
    )
}