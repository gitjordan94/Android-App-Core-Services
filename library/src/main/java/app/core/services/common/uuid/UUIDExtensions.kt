package app.core.services.common.uuid

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Converts a UUID into a ByteArray of 16 bytes.
 * The first 8 bytes represent the most significant bits,
 * and the last 8 bytes represent the least significant bits.
 *
 * @return A 16-byte array representing the UUID.
 */
fun UUID.toByteArray(): ByteArray {
    // Allocate a ByteBuffer with a capacity of 16 bytes (128 bits),
    // which is the exact size of a UUID.
    val byteBuffer = ByteBuffer.allocate(16)

    // Write the most significant bits of the UUID (64 bits) into the buffer.
    byteBuffer.putLong(mostSignificantBits)

    // Write the least significant bits of the UUID (64 bits) into the buffer.
    byteBuffer.putLong(leastSignificantBits)

    // Convert the ByteBuffer to a byte array and return it.
    return byteBuffer.array()
}