package app.core.services.common.uuid

import java.security.MessageDigest
import java.util.UUID

/**
 * Represents a namespace for generating UUIDs (version 3 and 5).
 *
 * Predefined namespaces are provided, but custom namespaces can also be used.
 */
@JvmInline
value class UUIDNamespace private constructor(val uuid: UUID) {

    companion object {
        val DNS = UUIDNamespace(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"))
        val URL = UUIDNamespace(UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8"))
        val OID = UUIDNamespace(UUID.fromString("6ba7b812-9dad-11d1-80b4-00c04fd430c8"))
        val X500 = UUIDNamespace(UUID.fromString("6ba7b814-9dad-11d1-80b4-00c04fd430c8"))

        /**
         * Creates a custom namespace from the given UUID.
         */
        fun custom(uuid: UUID): UUIDNamespace = UUIDNamespace(uuid)
    }

    /**
     * Generates a version 3 UUID (MD5 hash).
     *
     * @param name The name for which the UUID is generated.
     * @return A version 3 UUID.
     */
    fun uuid3(name: String): UUID = UUID.nameUUIDFromBytes(toBytes(uuid) + name.toByteArray())

    /**
     * Generates a version 5 UUID (SHA-1 hash).
     *
     * @param name The name for which the UUID is generated.
     * @return A version 5 UUID.
     */
    fun uuid5(name: String): UUID {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(toBytes(uuid))
        digest.update(name.toByteArray())
        val hash = digest.digest()
        hash[6] = (hash[6].toInt() and 0x0F or 0x50).toByte() // Set version to 5
        hash[8] = (hash[8].toInt() and 0x3F or 0x80).toByte() // Set variant to IETF
        return fromBytes(hash)
    }

    private fun toBytes(uuid: UUID): ByteArray = ByteArray(16).apply {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        for (i in 0..7) this[i] = (msb shr (7 - i) * 8 and 0xFF).toByte()
        for (i in 8..15) this[i] = (lsb shr (15 - i) * 8 and 0xFF).toByte()
    }

    private fun fromBytes(data: ByteArray): UUID {
        require(data.size >= 16) { "Byte array must be at least 16 bytes" }

        val msb = data.take(8)
            .fold(0L) { acc, byte -> acc shl 8 or (byte.toLong() and 0xFF) }

        val lsb = data.drop(8)
            .take(8)
            .fold(0L) { acc, byte -> acc shl 8 or (byte.toLong() and 0xFF) }

        return UUID(msb, lsb)
    }
}

/**
 * Generates a random UUID (version 4).
 */
fun uuid4(): UUID = UUID.randomUUID()

/**
 * Generates a version 3 UUID using the specified namespace and name.
 */
fun uuid3(namespace: UUIDNamespace, name: String): UUID = namespace.uuid3(name)

/**
 * Generates a version 5 UUID using the specified namespace and name.
 */
fun uuid5(namespace: UUIDNamespace, name: String): UUID = namespace.uuid5(name)