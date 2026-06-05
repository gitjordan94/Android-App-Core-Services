package app.core.services.deeplink

import android.net.Uri
import app.core.services.core.model.Attribution

/**
 * Represents a parsed deep link.
 *
 * This data class encapsulates the essential components of a deep link, including the original URI,
 * any extracted query parameters, and optional attribution information.
 *
 * @property uri The original, complete [Uri] of the deep link.
 * @property params A map of query parameters extracted from the URI. The keys are the parameter names
 *                  and the values are their corresponding string values.
 * @property attribution Optional [Attribution] data associated with the deep link, which can provide
 *                       information about its source (e.g., a marketing campaign). This can be null
 *                       if no attribution information is present.
 */
data class DeepLink(
    val uri: Uri,
    val params: Map<String, String>,
    val attribution: Attribution?
)