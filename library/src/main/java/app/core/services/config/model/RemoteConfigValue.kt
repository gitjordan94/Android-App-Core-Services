package app.core.services.config.model

interface RemoteConfigValue {
    val rawValue: String?

    /**
     * Gets the value as a `long`.
     *
     * @return `long` representation of this parameter value.
     * @throws IllegalArgumentException If the value cannot be converted to a `long`.
     */
    @Throws(IllegalArgumentException::class)
    fun asLong(): Long

    /**
     * Gets the value as a `double`.
     *
     * @return `double` representation of this parameter value.
     * @throws IllegalArgumentException If the value cannot be converted to a `double`.
     */
    @Throws(IllegalArgumentException::class)
    fun asDouble(): Double

    /**
     * Gets the value as a `String`.
     *
     * @return `String` representation of this parameter value.
     */
    fun asString(): String

    /**
     * Gets the value as a `byte[]`.
     *
     * @return `byte[]` representation of this parameter value.
     */
    fun asByteArray(): ByteArray

    /**
     * Gets the value as a `boolean`.
     *
     * @return `boolean` representation of this parameter value.
     * @throws IllegalArgumentException If the value cannot be converted to a `boolean`.
     */
    @Throws(IllegalArgumentException::class)
    fun asBoolean(): Boolean
}