package app.core.services.amplitude.experiment.util

import app.core.services.amplitude.experiment.ExperimentVariant
import app.core.services.config.model.RemoteConfigParameter

internal fun RemoteConfigParameter.toExperimentVariant(): ExperimentVariant {
    return ExperimentVariant(
        key = key,
        value = defaultValue,
        payloadJson = defaultPayload
    )
}