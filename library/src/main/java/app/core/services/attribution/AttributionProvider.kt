package app.core.services.attribution

import app.core.services.core.model.Attribution

internal interface AttributionProvider {
    suspend fun provide(): Attribution?
}