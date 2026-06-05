package app.core.services.attribution

import app.core.services.BuildConfig

data class AttributionServerConfig(
    val token: String,
    val externalAuthorization: Boolean,
    val serverUrl: String,
    val environment: Environment = Environment.DEFAULT
) {
    enum class Environment(val value: String) {
        DEBUG("Debug"),
        RELEASE("Release");

        companion object {
            val DEFAULT = if (BuildConfig.DEBUG) DEBUG else RELEASE
        }
    }
}