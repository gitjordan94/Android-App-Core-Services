package app.core.services.config

import app.core.services.config.model.RemoteConfigParameter

class RemoteConfigParameters {
    private val _parameters = mutableMapOf<String, RemoteConfigParameter>()
    internal val parameters: Map<String, RemoteConfigParameter> = _parameters

    fun param(
        key: String,
        defaultValue: String? = null,
        defaultPayload: Map<String, Any?>? = null,
        isStickyBucketed: Boolean = false
    ): RemoteConfigParameters {
        _parameters[key] = RemoteConfigParameter(
            key,
            defaultValue,
            defaultPayload,
            isStickyBucketed
        )

        return this
    }
}