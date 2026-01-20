package app.core.services.amplitude.experiment

import android.content.Context
import app.core.services.amplitude.experiment.db.ExperimentDao
import app.core.services.amplitude.experiment.db.ExperimentDatabase
import app.core.services.amplitude.experiment.db.ExperimentEntity
import app.core.services.config.InternalRemoteConfig
import app.core.services.config.OnConfigUpdateListener
import app.core.services.config.RemoteConfigParameters
import app.core.services.config.model.RemoteConfigValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal class AmplitudeRemoteConfig(
    private val remoteExperiment: RemoteExperiment,
    private val experimentDao: ExperimentDao,
    private val params: RemoteConfigParameters,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : InternalRemoteConfig {
    private val json = Json { ignoreUnknownKeys = true }

    private val persistenceScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val cache = ConcurrentHashMap<String, ExperimentVariant>()

    init {
        observeExperimentsAndCache()
    }

    override suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ): Boolean {
        Timber.d("Fetching remote config for userId=$userId")
        return try {
            val result = remoteExperiment.fetch(userId, userProperties)
            if (result) {
                Timber.d("Remote config fetched successfully")
            } else {
                Timber.w("Remote config fetch returned false")
            }
            result
        } catch (e: Throwable) {
            Timber.e(e, "Failed to fetch remote config")
            false
        }
    }

    override operator fun get(key: String): RemoteConfigValue {
        val variant = getVariant(key)
        return AmplitudeRemoteConfigValue(variant)
    }

    override fun getBoolean(key: String) = get(key).asBoolean()

    override fun getLong(key: String) = get(key).asLong()

    override fun getString(key: String) = get(key).value

    override fun getPayload(key: String) = get(key).payload

    override fun setOnOnConfigUpdateListener(onConfigUpdateListener: OnConfigUpdateListener) {
        // do nothing
    }

    private fun observeExperimentsAndCache() {
        experimentDao.getExperiments()
            .flowOn(ioDispatcher)
            .onEach { entities ->
                entities.forEach { entity ->
                    cache.putIfAbsent(entity.key, entity.toExperimentVariant())
                }
            }
            .launchIn(persistenceScope)
    }

    private fun getVariant(key: String): ExperimentVariant {
        val cachedVariant = cache[key]
        val isStickyBucketed = params.parameters[key]?.isStickyBucketed == true

        if (isStickyBucketed && cachedVariant != null) {
            Timber.d("Returning cached variant for $key: $cachedVariant")
            return cachedVariant
        }

        val remoteVariant = remoteExperiment[key]
        Timber.d("Remote variant for $key: $remoteVariant")

        val variantChanged = cachedVariant == null || cachedVariant != remoteVariant

        if (variantChanged) {
            Timber.d("Variant for $key changed. Old value: $cachedVariant, new value: $remoteVariant")
            storeExperiment(key, remoteVariant)
            cache[key] = remoteVariant
            remoteExperiment.exposure(key)

            return remoteVariant
        }

        return cachedVariant
    }

    private fun storeExperiment(key: String, variant: ExperimentVariant) {
        persistenceScope.launch {
            try {
                experimentDao.insert(
                    ExperimentEntity(
                        key = key,
                        value = variant.value,
                        payload = variant.payload?.let(json::encodeToString),
                    )
                )
            } catch (t: Throwable) {
                Timber.e(t, "Failed to save experiment $key")
            }
        }
    }

    internal companion object {
        internal fun create(
            applicationContext: Context,
            apiKey: String,
            params: RemoteConfigParameters
        ): InternalRemoteConfig {
            val database = ExperimentDatabase.create(applicationContext)
            val experimentDao = database.experiments
            val amplitudeExperiment = AmplitudeRemoteExperiment.create(apiKey, applicationContext)

            return AmplitudeRemoteConfig(
                remoteExperiment = amplitudeExperiment,
                experimentDao = experimentDao,
                params = params
            )
        }
    }
}