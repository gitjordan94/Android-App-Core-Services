package app.core.services.agesignals.model

import java.util.Date

data class AgeSignalsResult(
    val ageLower: Int?,
    val ageUpper: Int?,
    val installId: String?,
    val userStatus: AgeSignalsUserStatus?,
    val mostRecentApprovalDate: Date?
)