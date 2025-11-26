package app.core.services.config.model

import app.core.services.config.RemoteConfigTarget
import app.core.services.config.model.RemoteConfigParams.AB_PAYWALL_FACEBOOK
import app.core.services.config.model.RemoteConfigParams.AB_PAYWALL_GENERAL
import app.core.services.config.model.RemoteConfigParams.AB_PAYWALL_GOOGLE
import app.core.services.config.model.RemoteConfigParams.MIN_SUPPORTED_APP_VERSION
import app.core.services.config.model.RemoteConfigParams.RATE_US_PRIMARY_SHOWN
import app.core.services.config.model.RemoteConfigParams.RATE_US_SECONDARY_SHOWN
import app.core.services.config.model.RemoteConfigParams.SUBS_SCREEN_STYLE_FULL
import app.core.services.config.model.RemoteConfigParams.SUBS_SCREEN_STYLE_HARD
import app.core.services.core.model.MediaSourceType

class RemoteConfigParameters {
    private val _parameters = mutableMapOf<String, RemoteConfigParameter>()
    internal val parameters: Map<String, RemoteConfigParameter> = _parameters

    init {
        setSubsScreenStyleFull(true)
        setSubsScreenStyleHard(false)
        setShowPrimaryRateUs(false)
        setShowSecondaryRateUs(false)
        setShowSecondaryRateUs(false)
        setGeneralPaywall("")
        setFacebookPaywall("")
        setGooglePaywall("")
        setMinimalSupportedAppVersion(0)
    }

    fun setSubsScreenStyleFull(value: Boolean) = param(SUBS_SCREEN_STYLE_FULL, value)

    fun setSubsScreenStyleHard(value: Boolean) = param(SUBS_SCREEN_STYLE_HARD, value)

    fun setShowPrimaryRateUs(value: Boolean) = param(RATE_US_PRIMARY_SHOWN, value)

    fun setShowSecondaryRateUs(value: Boolean) = param(RATE_US_SECONDARY_SHOWN, value)

    fun setGeneralPaywall(value: String) = param(
        key = AB_PAYWALL_GENERAL,
        value = value,
        target = RemoteConfigTarget.any()
    )

    fun setFacebookPaywall(value: String) = param(
        key = AB_PAYWALL_FACEBOOK,
        value = value,
        target = RemoteConfigTarget.sources(MediaSourceType.FACEBOOK)
    )

    fun setGooglePaywall(value: String) = param(
        key = AB_PAYWALL_GOOGLE,
        value = value,
        target = RemoteConfigTarget.sources(MediaSourceType.GOOGLE)
    )

    fun setMinimalSupportedAppVersion(value: Int) = param(MIN_SUPPORTED_APP_VERSION, value.toLong())

    fun param(
        key: String,
        value: String? = null,
        target: RemoteConfigTarget? = null
    ) = putParam(key, value, target)

    fun param(
        key: String,
        value: Long,
        target: RemoteConfigTarget? = null
    ) = putParam(key, value, target)

    fun param(
        key: String,
        value: Double,
        target: RemoteConfigTarget? = null
    ) = putParam(key, value, target)

    fun param(
        key: String,
        value: Boolean,
        target: RemoteConfigTarget? = null
    ) = putParam(key, value, target)

    private fun putParam(
        key: String,
        value: Any?,
        target: RemoteConfigTarget? = null
    ): RemoteConfigParameters {
        val defaultValue = when {
            target == null -> value?.toString() ?: ""
            value != null -> "none_$value"
            else -> "none"
        }

        _parameters[key] = RemoteConfigParameter(key, defaultValue, target)

        return this
    }
}