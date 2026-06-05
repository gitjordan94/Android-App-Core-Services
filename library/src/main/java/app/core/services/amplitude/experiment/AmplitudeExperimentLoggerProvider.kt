package app.core.services.amplitude.experiment

import com.amplitude.experiment.util.LoggerProvider
import timber.log.Timber

internal class AmplitudeExperimentLoggerProvider : LoggerProvider {
    override fun debug(msg: String) {
        Timber.d(msg)
    }

    override fun error(msg: String) {
        Timber.e(msg)
    }

    override fun info(msg: String) {
        Timber.i(msg)
    }

    override fun verbose(msg: String) {
        Timber.v(msg)
    }

    override fun warn(msg: String) {
        Timber.w(msg)
    }
}