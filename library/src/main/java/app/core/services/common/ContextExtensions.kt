package app.core.services.common

import android.content.Context
import android.content.res.Configuration

internal fun Context.isSystemInDarkTheme(): Boolean {
    val configuration = resources.configuration
    return configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}