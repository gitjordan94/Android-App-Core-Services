package app.core.services.agesignals.util

import app.core.services.agesignals.model.AgeSignalsResult
import app.core.services.agesignals.model.AgeSignalsUserStatus
import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus
import com.google.android.play.agesignals.AgeSignalsResult as ExternalAgeSignalsResult

internal fun ExternalAgeSignalsResult.toInternal(): AgeSignalsResult {
    return AgeSignalsResult(
        ageLower = ageLower(),
        ageUpper = ageUpper(),
        installId = installId(),
        userStatus = when (userStatus()) {
            AgeSignalsVerificationStatus.VERIFIED -> AgeSignalsUserStatus.VERIFIED
            AgeSignalsVerificationStatus.DECLARED -> AgeSignalsUserStatus.DECLARED
            AgeSignalsVerificationStatus.SUPERVISED -> AgeSignalsUserStatus.SUPERVISED
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING -> AgeSignalsUserStatus.SUPERVISED_APPROVAL_PENDING
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED -> AgeSignalsUserStatus.SUPERVISED_APPROVAL_DENIED
            null -> null
            else -> AgeSignalsUserStatus.UNKNOWN
        },
        mostRecentApprovalDate = mostRecentApprovalDate()
    )
}