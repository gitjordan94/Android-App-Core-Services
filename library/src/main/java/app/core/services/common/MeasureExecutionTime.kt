package app.core.services.common

import timber.log.Timber
import kotlin.time.measureTimedValue

/**
 * Measures the execution time of a given code block, logs the duration, and returns the result.
 * This function is robust against exceptions, ensuring that the execution time is always logged.
 *
 * @param T The return type of the code block.
 * @param tag A descriptive tag for logging purposes, identifying the measured operation.
 * @param body The lambda function to execute and measure.
 * @return The value returned by the 'body' lambda.
 */
inline fun <T> measureExecutionTime(tag: String, body: () -> T): T {
    Timber.d("[$tag] Starting execution...")
    val timedValue = measureTimedValue(body)
    Timber.d("[$tag] Execution completed in  ${timedValue.duration}.")
    return timedValue.value
}