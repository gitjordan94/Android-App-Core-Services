package app.core.services.common.uuid

import junit.framework.TestCase.assertEquals
import org.junit.Test
import java.util.UUID

class UUIDNamespaceTest {
    @Test
    fun `uuid5 matches expected value`() {
        val appsflyerId = "1768043425207-6376669274627248829"
        val expectedDeviceId = UUID.fromString("7350652a-e4f8-5808-a1a0-b651992791a1")

        // when
        val deviceId = UUIDNamespace.OID.uuid5(appsflyerId)

        // then
        assertEquals(expectedDeviceId, deviceId)
        assertEquals(5, deviceId.version())
        assertEquals(2, deviceId.variant()) // RFC 4122 / IETF
    }
}