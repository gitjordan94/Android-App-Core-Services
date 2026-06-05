package app.core.services.consent

data class Consent(
    val analyticsStorage: Boolean,
    val adPersonalization: Boolean,
    val adStorage: Boolean,
    val adUserData: Boolean
) {
    companion object {
        /**
         * All consent signals denied. Use as the initial state for apps that
         * integrate a CMP and have not yet collected user consent.
         */
        val DENIED_ALL = Consent(
            analyticsStorage = false,
            adPersonalization = false,
            adStorage = false,
            adUserData = false
        )

        /** All consent signals granted. */
        val GRANTED_ALL = Consent(
            analyticsStorage = true,
            adPersonalization = true,
            adStorage = true,
            adUserData = true
        )
    }
}