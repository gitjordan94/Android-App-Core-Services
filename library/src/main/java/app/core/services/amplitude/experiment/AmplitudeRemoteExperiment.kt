package app.core.services.amplitude.experiment

import android.app.Application
import android.content.Context
import app.core.services.BuildConfig
import app.core.services.amplitude.experiment.util.toAmplitudeVariant
import com.amplitude.experiment.Experiment.initializeWithAmplitudeAnalytics
import com.amplitude.experiment.ExperimentClient
import com.amplitude.experiment.ExperimentConfig
import com.amplitude.experiment.ExperimentUser
import com.amplitude.experiment.util.LogLevel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

internal class AmplitudeRemoteExperiment(
    private val experiment: ExperimentClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : RemoteExperiment {
    override suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ) {
        Timber.d("Fetching remote config")

        return withContext(ioDispatcher) {
            try {
                val experimentUser = ExperimentUser.builder()
                    .userId(userId)
                    .userProperties(userProperties)
                    .build()

                experiment.fetch(experimentUser).get(5, TimeUnit.SECONDS)
            } catch (e: Throwable) {
                Timber.e(e, "Failed to fetch remote config")
                throw e
            }
        }
    }

    override fun getVariant(key: String, defaultValue: ExperimentVariant?): ExperimentVariant {
        val variant = experiment.variant(key, defaultValue?.toAmplitudeVariant())
        Timber.d("Variant for $key: $variant")
        return ExperimentVariant(key, variant.value, variant.payload?.toString())
    }

    override fun exposure(key: String) {
        experiment.exposure(key)
        Timber.d("Exposed to $key")
    }

    internal companion object {
        internal fun create(
            apiKey: String,
            applicationContext: Context,
        ): RemoteExperiment {
            return AmplitudeRemoteExperiment(
                experiment = initializeWithAmplitudeAnalytics(
                    application = applicationContext as Application,
                    apiKey = apiKey,
                    config = ExperimentConfig.builder()
                        .fetchOnStart(false)
                        .automaticExposureTracking(false)
                        .loggerProvider(AmplitudeExperimentLoggerProvider())
                        .debug(BuildConfig.DEBUG)
                        .logLevel(if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.ERROR)
                        .build()
                )
            )
        }
    }
}