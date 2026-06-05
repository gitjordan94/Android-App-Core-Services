package app.core.services.core.appsetid

internal interface AppSetIdProvider {
    suspend fun provide(): String?
}