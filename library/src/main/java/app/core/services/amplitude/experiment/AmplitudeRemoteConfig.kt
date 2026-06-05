package app.core.services.amplitude.experiment

import android.content.Context
import app.core.services.amplitude.experiment.db.ExperimentDao
import app.core.services.amplitude.experiment.db.ExperimentDatabase
import app.core.services.amplitude.experiment.util.toExperimentEntity
import app.core.services.amplitude.experiment.util.toExperimentVariant
import app.core.services.config.RemoteConfig
import app.core.services.config.RemoteConfigParameters
import app.core.services.config.model.RemoteConfigValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

internal class AmplitudeRemoteConfig(
    private val remoteExperiment: RemoteExperiment,
    private val experimentDao: ExperimentDao,
    private val remoteConfigParameters: RemoteConfigParameters,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : RemoteConfig {
    private val keyLocks = ConcurrentHashMap<String, Any>()
    private val memoryCache = ConcurrentHashMap<String, ExperimentVariant>()
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    init {
        observeAndCacheExperiments()
    }

    override suspend fun fetch(
        userId: String?,
        userProperties: Map<String, Any?>?
    ): Boolean {
        Timber.d("\uD83D\uDCE1 Fetching remote config | userId=$userId")
        return try {
            remoteExperiment.fetch(userId, userProperties)
            Timber.d("✅ Remote config fetch successful | userId=$userId")
            true
        } catch (e: Throwable) {
            Timber.e(e, "❌ Remote config fetch failed | userId=$userId")
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

    override fun getPayload(key: String) = get(key).payload()

    private fun observeAndCacheExperiments() {
        experimentDao.getExperiments()
            .flowOn(ioDispatcher)
            .onEach { entities ->
                entities.forEach { entity ->
                    memoryCache[entity.key] = entity.toExperimentVariant()
                }

                Timber.d("\uD83D\uDCBE Loaded experiments from DB | count=${entities.size}")
            }
            .catch { throwable ->
                Timber.e(throwable, "❌ Failed to observe experiments from database")
            }
            .launchIn(ioScope)
    }

    private fun getVariant(key: String): ExperimentVariant {
        getStickyVariant(key, memoryCache[key])?.let { return it }

        val lock = keyLocks.getOrPut(key, ::Any)

        return try {
            synchronized(lock) {
                val cachedVariant = memoryCache[key]

                getStickyVariant(key, cachedVariant)?.let { return@synchronized it }

                val default = remoteConfigParameters.defaults[key]?.toExperimentVariant()
                    ?: cachedVariant

                val remoteVariant = remoteExperiment.getVariant(key, default)
                val variantChanged = cachedVariant == null || cachedVariant != remoteVariant

                if (variantChanged) {
                    logVariantChange(
                        key = key,
                        oldVariant = cachedVariant,
                        newVariant = remoteVariant
                    )

                    storeExperiment(key, remoteVariant)
                    memoryCache[key] = remoteVariant
                    remoteExperiment.exposure(key)
                    remoteVariant
                } else {
                    Timber.d("♻\uFE0F Using unchanged variant | key=$key, value=${cachedVariant.value}")
                    cachedVariant
                }
            }
        } finally {
            keyLocks.remove(key, lock)
        }
    }

    private fun getStickyVariant(key: String, variant: ExperimentVariant?): ExperimentVariant? {
        val isSticky = remoteConfigParameters.defaults[key]?.isStickyBucketed == true
        if (variant != null && isSticky) {
            Timber.d("📍 Using sticky cached variant | key=$key, value=${variant.value}")
            return variant
        }
        return null
    }

    private fun logVariantChange(
        key: String,
        oldVariant: ExperimentVariant?,
        newVariant: ExperimentVariant
    ) {
        val message = buildString {
            fun format(variant: ExperimentVariant?): String {
                if (variant == null) return "null"
                val payload = variant.payloadJson?.take(100) // Truncate for readability
                return "${variant.value} | $payload"
            }

            append("🔄 Variant changed | key=$key")
            append("\n   ├─ old: ${format(oldVariant)}")
            append("\n   └─ new: ${format(newVariant)}")
        }
        Timber.d(message)
    }

    private fun storeExperiment(key: String, variant: ExperimentVariant) {
        ioScope.launch {
            try {
                experimentDao.insert(variant.toExperimentEntity())
                Timber.d("\uD83D\uDCBE Saved experiment to DB | key=$key, value=${variant.value}")
            } catch (t: Throwable) {
                Timber.e(t, "❌ Failed to save experiment | key=$key")
            }
        }
    }

    internal companion object {
        internal fun create(
            applicationContext: Context,
            remoteConfigParameters: RemoteConfigParameters
        ): RemoteConfig {
            val database = ExperimentDatabase.create(applicationContext)
            val experimentDao = database.experiments

            val amplitudeExperiment = AmplitudeRemoteExperiment.create(
                apiKey = remoteConfigParameters.amplitudeDeploymentKey,
                applicationContext = applicationContext
            )

            return AmplitudeRemoteConfig(
                remoteExperiment = amplitudeExperiment,
                experimentDao = experimentDao,
                remoteConfigParameters = remoteConfigParameters
            )
        }
    }
}