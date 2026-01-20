package app.core.services.amplitude.experiment

import app.core.services.amplitude.experiment.db.ExperimentEntity

internal fun ExperimentEntity.toExperimentVariant(): ExperimentVariant {
    return ExperimentVariant(
        key = key,
        value = value,
        payload = payload,
    )
}