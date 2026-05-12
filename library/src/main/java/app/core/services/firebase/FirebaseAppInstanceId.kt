package app.core.services.firebase

internal interface FirebaseAppInstanceId {
    suspend fun getAppInstanceId(): String?
}