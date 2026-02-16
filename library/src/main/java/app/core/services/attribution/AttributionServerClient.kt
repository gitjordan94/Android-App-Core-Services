package app.core.services.attribution

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.core.services.BuildConfig
import app.core.services.attribution.model.AttributionInstallRequestBody
import app.core.services.attribution.model.ExternalAuthorizationRequestBody
import app.core.services.billing.BillingStoreCountryProvider
import app.core.services.data.KeyValueStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

internal interface AttributionServerClient {
    fun install(userId: String)

    fun setExternalUserId(externalUserId: String)

    suspend fun getInstallUserId(): String?

    companion object {
        fun create(
            applicationContext: Context,
            attributionServerConfig: AttributionServerConfig,
            deviceIdProvider: DeviceIdProvider,
            billingStoreCountryProvider: BillingStoreCountryProvider,
            keyValueStorage: KeyValueStorage,
        ): AttributionServerClient {
            return AttributionServerClientImpl(
                config = attributionServerConfig,
                appInfoProvider = AppInfoProvider.create(applicationContext),
                advertisingIdProvider = AdvertisingIdProvider.create(applicationContext),
                attributionServerApi = AttributionServerApi.create(
                    attributionServerConfig.token,
                    attributionServerConfig.serverUrl
                ),
                billingStoreCountryProvider = billingStoreCountryProvider,
                keyValueStorage = keyValueStorage,
                deviceIdProvider = deviceIdProvider
            )
        }
    }
}

internal class AttributionServerClientImpl(
    private val config: AttributionServerConfig,
    private val appInfoProvider: AppInfoProvider,
    private val advertisingIdProvider: AdvertisingIdProvider,
    private val attributionServerApi: AttributionServerApi,
    private val billingStoreCountryProvider: BillingStoreCountryProvider,
    private val deviceIdProvider: DeviceIdProvider,
    private val keyValueStorage: KeyValueStorage,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AttributionServerClient {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val installUserId = MutableStateFlow<String?>(null)

    @Volatile
    private var isFirstStart = true

    private val installMutex = Mutex()
    private val externalUserIdMutex = Mutex()

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_ATTRIBUTION_USER_ID = "attribution_user_id"
        private const val KEY_EXTERNAL_USER_ID = "external_user_id"
        private const val KEY_ATTRIBUTION_EXTERNAL_USER_ID = "attribution_external_user_id"
    }

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    if (isFirstStart) {
                        isFirstStart = false
                    } else {
                        retryFailedRequests()
                    }
                }
            }
        )

        retryFailedRequests()
    }

    override fun install(userId: String) {
        scope.launch {
            keyValueStorage.putString(KEY_USER_ID, userId)
            installInternal(userId)
        }
    }

    override fun setExternalUserId(externalUserId: String) {
        scope.launch {
            keyValueStorage.putString(KEY_EXTERNAL_USER_ID, externalUserId)
            setExternalUserIdInternal(externalUserId)
        }
    }

    override suspend fun getInstallUserId(): String? {
        return keyValueStorage.getString(KEY_ATTRIBUTION_USER_ID)
    }

    private suspend fun installInternal(userId: String) {
        if (keyValueStorage.getString(KEY_ATTRIBUTION_USER_ID) != null) {
            Timber.d("Install already sent.")
            return
        }

        installMutex.withLock {
            if (keyValueStorage.getString(KEY_ATTRIBUTION_USER_ID) != null) {
                Timber.d("Install already sent.")
                return
            }

            val appVersion = requireNotNull(appInfoProvider.getAppVersionName()) {
                "App version name is required but was null."
            }

            val advertisingId = withContext(dispatcher) {
                advertisingIdProvider.provide()
            }

            val request = AttributionInstallRequestBody(
                sdkVersion = BuildConfig.SDK_VERSION,
                osVersion = Build.VERSION.RELEASE,
                appVersion = appVersion,
                limitAdTracking = advertisingId.isLimitAdTrackingEnabled,
                advertisingId = advertisingId.id ?: UUID.randomUUID().toString(),
                appsflyerId = userId,
                storeCountry = billingStoreCountryProvider.getStoreCountry() ?: "Unknown",
                environment = config.environment.value,
                externalAuthorization = config.externalAuthorization,
                deviceId = deviceIdProvider.provide()
            )

            val installAttributionResult = attributionServerApi.install(request)

            installAttributionResult
                .onSuccess { response ->
                    Timber.d("Successfully sent install request.")
                    keyValueStorage.putString(KEY_ATTRIBUTION_USER_ID, response.userId)
                    installUserId.value = response.userId
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to send install request.")
                }
        }
    }

    private suspend fun setExternalUserIdInternal(externalUserId: String) {
        val attributionExternalUserId = keyValueStorage.getString(KEY_ATTRIBUTION_EXTERNAL_USER_ID)

        if (externalUserId == attributionExternalUserId) {
            Timber.d("External user ID $externalUserId already sent.")
            return
        }

        externalUserIdMutex.withLock {
            val attributionExternalUserId =
                keyValueStorage.getString(KEY_ATTRIBUTION_EXTERNAL_USER_ID)

            if (externalUserId == attributionExternalUserId) {
                Timber.d("External user ID $externalUserId already sent.")
                return
            }

            keyValueStorage.putString(KEY_EXTERNAL_USER_ID, externalUserId)

            val userId = installUserId.first { it != null }

            val externalAuthResult = attributionServerApi.externalAuthorization(
                request = ExternalAuthorizationRequestBody(
                    userId = userId!!,
                    productUserId = externalUserId
                )
            )

            externalAuthResult
                .onSuccess {
                    Timber.d("Successfully sent external user id.")
                    keyValueStorage.putString(KEY_ATTRIBUTION_EXTERNAL_USER_ID, externalUserId)
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to send external user id.")
                }
        }
    }

    private fun retryFailedRequests() {
        scope.launch {
            Timber.d("Checking for failed requests.")

            installUserId.value = keyValueStorage.getString(KEY_USER_ID)

            val userId = keyValueStorage.getString(KEY_USER_ID)
            if (userId != null) {
                Timber.d("Retrying install request.")
                installInternal(userId)
            }

            val externalUserId = keyValueStorage.getString(KEY_EXTERNAL_USER_ID)
            if (externalUserId != null) {
                Timber.d("Retrying external user id request.")
                setExternalUserIdInternal(externalUserId)
            }
        }
    }
}