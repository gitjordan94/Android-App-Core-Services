package app.core.services.core.model

/**
 * Represents the source of user attribution data.
 * This enum is used to identify which platform or service provided the information
 * about how a user was acquired (e.g., through a specific ad campaign).
 */
enum class AttributionSource(val value: String) {
    APPSFLYER("AppsFlyer"),
    GOOGLE_PLAY_INSTALL_REFERRER("Google Play Install Referrer");
}