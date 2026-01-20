package app.core.services.analytics

import app.core.services.amplitude.sessionreplay.SessionReplayController

interface Analytics : EventLogger, SessionReplayController {
    fun setUserProperties(properties: Map<String, Any?>?)

    fun setUserPropertiesOnce(properties: Map<String, Any?>?) {}
}