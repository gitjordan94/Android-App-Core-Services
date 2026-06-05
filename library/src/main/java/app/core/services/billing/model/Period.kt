package app.core.services.billing.model

import kotlin.math.roundToInt

/**
 * Represents a time duration used to describe a subscription or billing period.
 *
 * A [Period] defines how long a pricing phase lasts — for example, one month,
 * one week, or one year. It also preserves the original ISO 8601 duration string
 * (e.g., `"P1M"` for one month or `"P3W"` for three weeks).
 *
 * @property value The numeric length of the period, normalized to an integer.
 * @property unit The unit of time (day, week, month, or year).
 * @property iso8601 The original ISO 8601 duration string (e.g., `"P1Y6M"`).
 */
data class Period(
    val value: Int,
    val unit: Unit,
    val iso8601: String,
) {
    /**
     * Supported time units for representing a [Period].
     */
    enum class Unit { DAY, WEEK, MONTH, YEAR }

    internal companion object {
        const val DAYS_PER_YEAR = 365.2425
        const val MONTHS_PER_YEAR = 12.0
        const val DAYS_PER_WEEK = 7.0
        const val WEEKS_PER_YEAR = DAYS_PER_YEAR / DAYS_PER_WEEK
        const val DAYS_PER_MONTH = DAYS_PER_YEAR / MONTHS_PER_YEAR
        const val WEEKS_PER_MONTH = DAYS_PER_MONTH / DAYS_PER_WEEK

        private val regex = Regex("""^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)W)?(?:(\d+)D)?$""")

        /**
         * Parses an ISO 8601 duration string (e.g., `"P1Y6M"`, `"P3W"`, `"P30D"`)
         * into a [Period] instance.
         *
         * The parser determines the most appropriate [Unit] based on the smallest
         * non-zero component and normalizes all larger components to that scale.
         * For example, `"P1Y6M"` becomes a period of 18 months.
         *
         * @param iso8601 The ISO 8601 duration string to parse.
         * @return A [Period] instance representing the duration.
         * @throws IllegalArgumentException if the format is invalid.
         */
        internal fun parse(iso8601: String): Period {
            val match = regex.matchEntire(iso8601)
                ?: throw IllegalArgumentException("Invalid ISO 8601 period format: $iso8601")

            val (yStr, mStr, wStr, dStr) = match.destructured

            val years = yStr.toDoubleOrNull() ?: 0.0
            val months = mStr.toDoubleOrNull() ?: 0.0
            val weeks = wStr.toDoubleOrNull() ?: 0.0
            val days = dStr.toDoubleOrNull() ?: 0.0

            val unit = when {
                days > 0.0 -> Unit.DAY
                weeks > 0.0 -> Unit.WEEK
                months > 0.0 -> Unit.MONTH
                years > 0.0 -> Unit.YEAR
                else -> Unit.DAY
            }

            val value = when (unit) {
                Unit.YEAR -> years +
                        (months / MONTHS_PER_YEAR) +
                        (weeks / WEEKS_PER_YEAR) +
                        (days / DAYS_PER_YEAR)

                Unit.MONTH -> (years * MONTHS_PER_YEAR) +
                        months +
                        (weeks / WEEKS_PER_MONTH) +
                        (days / DAYS_PER_MONTH)

                Unit.WEEK -> (years * WEEKS_PER_YEAR) +
                        (months * WEEKS_PER_MONTH) +
                        weeks +
                        (days / DAYS_PER_WEEK)

                Unit.DAY -> (years * DAYS_PER_YEAR) +
                        (months * DAYS_PER_MONTH) +
                        (weeks * DAYS_PER_WEEK) +
                        days
            }.roundToInt()

            return Period(value, unit, iso8601)
        }
    }
}