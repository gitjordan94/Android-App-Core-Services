package app.core.services.attribution

import app.core.services.core.model.Attribution

internal interface AttributionParser<T> {
    fun parse(data: T): Attribution
}