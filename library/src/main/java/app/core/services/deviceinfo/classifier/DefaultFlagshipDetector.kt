package app.core.services.deviceinfo.classifier

internal class DefaultFlagshipDetector : FlagshipDetector {
    private val indicators = listOf(
        "snapdragon 8", "snapdragon 888", "snapdragon 865", "snapdragon 855",
        "exynos 2100", "exynos 2200", "exynos 990", "exynos 9820",
        "kirin 9000", "kirin 990",
        "dimensity 9000", "dimensity 1200",
        "tensor", "google tensor"
    )

    override fun isFlagship(fingerprintText: String): Boolean {
        val t = fingerprintText.lowercase()
        return indicators.any { t.contains(it) }
    }
}