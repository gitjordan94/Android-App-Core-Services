package app.core.services.agesignals.model

enum class AgeSignalsUserStatus {
    VERIFIED,
    DECLARED,
    SUPERVISED,
    SUPERVISED_APPROVAL_PENDING,
    SUPERVISED_APPROVAL_DENIED,
    UNKNOWN
}