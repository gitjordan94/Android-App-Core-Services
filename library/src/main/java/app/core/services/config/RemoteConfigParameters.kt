package app.core.services.config

import app.core.services.config.model.RemoteConfigParameter

class RemoteConfigParameters(
    internal val amplitudeDeploymentKey: String
) {
    private val _defaults = mutableMapOf<String, RemoteConfigParameter>()
    internal val defaults: Map<String, RemoteConfigParameter> = _defaults

    fun param(
        key: String,
        defaultValue: String? = null,
        defaultPayload: String? = null,
        isStickyBucketed: Boolean = false
    ): RemoteConfigParameters {
        _defaults[key] = RemoteConfigParameter(
            key,
            defaultValue,
            defaultPayload,
            isStickyBucketed
        )

        return this
    }
}