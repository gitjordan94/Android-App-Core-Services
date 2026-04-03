package app.core.services.agesignals

import app.core.services.agesignals.model.AgeSignalsResult
import app.core.services.agesignals.model.AgeSignalsException

interface AgeSignalsListener {
    fun onSuccess(result: AgeSignalsResult)

    fun onError(e: AgeSignalsException)
}