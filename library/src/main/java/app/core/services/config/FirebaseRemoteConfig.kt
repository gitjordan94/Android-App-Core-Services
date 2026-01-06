package app.core.services.config

import app.core.services.config.model.RemoteConfigParameters
import app.core.services.config.model.RemoteConfigParams
import app.core.services.config.model.RemoteConfigValue
import app.core.services.config.model.RemoteConfigValueImpl
import app.core.services.core.model.MediaSourceType
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.get
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.tasks.await
import timber.log.Timber

internal class FirebaseRemoteConfig(
    private val defaults: RemoteConfigParameters,
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig
) : RemoteConfig {

    internal var remoteConfigMatchingContext: RemoteConfigMatchingContext? = null

    internal suspend fun activate(): Boolean {
        return try {
            remoteConfig.activate().await()
        } catch (e: Throwable) {
            Timber.e(e)
            false
        }
    }

    internal suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = 1
                }
            ).await()

            remoteConfig.setDefaultsAsync(defaults.parameters.mapValues { it.value.defaultValue })
                .await()

            remoteConfig.fetchAndActivate().await()
        } catch (e: Throwable) {
            Timber.e(e)
            false
        }
    }

    override operator fun get(key: String): RemoteConfigValue {
        return get(key, remoteConfig[key])
    }

    override fun getLong(key: String) = get(key).asLong()

    override fun getDouble(key: String) = get(key).asDouble()

    override fun getBoolean(key: String) = get(key).asBoolean()

    override fun getString(key: String) = get(key).asString()

    override fun getAll(): Map<String, RemoteConfigValue> {
        return remoteConfig.all.mapValues { get(it.key, it.value) }
    }

    override fun getActivePaywallName(): String {
        return getString(
            when (remoteConfigMatchingContext?.attribution?.mediaSource?.type) {
                MediaSourceType.GOOGLE -> RemoteConfigParams.AB_PAYWALL_GOOGLE
                MediaSourceType.FACEBOOK -> RemoteConfigParams.AB_PAYWALL_FACEBOOK
                else -> RemoteConfigParams.AB_PAYWALL_GENERAL
            }
        )
    }

    override fun isSubscriptionStyleFull() = getBoolean(RemoteConfigParams.SUBS_SCREEN_STYLE_FULL)

    override fun isSubscriptionStyleHard() = getBoolean(RemoteConfigParams.SUBS_SCREEN_STYLE_HARD)

    override fun rateUsPrimaryShow() = getBoolean(RemoteConfigParams.RATE_US_PRIMARY_SHOWN)

    override fun rateUsSecondaryShow() = getBoolean(RemoteConfigParams.RATE_US_SECONDARY_SHOWN)

    override fun setOnOnConfigUpdateListener(onConfigUpdateListener: OnConfigUpdateListener) {
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                onConfigUpdateListener.onUpdate(configUpdate.updatedKeys)
                Timber.d("On config update updatedKeys: ${configUpdate.updatedKeys}")
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Timber.e(error)
            }
        })
    }

    private fun get(key: String, value: FirebaseRemoteConfigValue): RemoteConfigValue {
        return RemoteConfigValueImpl.from(
            key = key,
            value = value,
            data = remoteConfigMatchingContext,
            default = defaults.parameters[key]
        )
    }
}