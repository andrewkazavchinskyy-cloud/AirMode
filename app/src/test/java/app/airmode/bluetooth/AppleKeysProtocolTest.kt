package app.airmode.bluetooth

import org.junit.Assert.*
import org.junit.Test

class AppleKeysProtocolTest {
    @Test fun parsesBoundedKeysWithoutSecretRepresentationsOrModeAcknowledgement() {
        val packet = reply(entry(1, hex("ec0234a357c8ad05341010a60a397d9b")), entry(4, ByteArray(16) { it.toByte() }))
        val event = AapProtocol.parse(packet, 1000) as ProtocolEvent.ProximityKeys
        event.keys.use { keys ->
            assertTrue(keys.hasIrk); assertTrue(keys.hasEncryption)
            assertTrue(keys.matchesAddress("70:81:94:0D:FB:AA"))
            assertFalse(event.toString().contains("ec0234"))
        }
        assertArrayEquals(hex("0400040030000500"), AapProtocol.requestProximityKeys())
        assertNull(AapProtocol.parse(AapProtocol.requestProximityKeys(), 1000))
    }

    @Test fun rejectsTruncatedOversizedDuplicateAndUnknownOnlyKeyReplies() {
        val valid = reply(entry(1, ByteArray(16)), entry(4, ByteArray(16)))
        for (size in 0 until valid.size) assertNull(AapProtocol.parse(valid.copyOf(size), 1000))
        assertNull(AapProtocol.parse(valid + byteArrayOf(0), 1000))
        assertNull(AapProtocol.parse(reply(entry(1, ByteArray(15))), 1000))
        assertNull(AapProtocol.parse(reply(entry(4, ByteArray(17))), 1000))
        assertNull(AapProtocol.parse(reply(entry(1, ByteArray(16)), entry(1, ByteArray(16))), 1000))
        assertNull(AapProtocol.parse(reply(entry(2, ByteArray(1))), 1000))
        assertNull(AapProtocol.parse(reply(entry(2, ByteArray(65)), entry(4, ByteArray(16))), 1000))
        assertNull(AapProtocol.parse(valid.copyOf().apply { this[6] = 9 }, 1000))
    }

    @Test fun acceptsDocumentedHeadsetComponentWithoutDuplicatingEarbudValues() {
        val event = AapProtocol.parse(hex("0400040004000101014b0201"), 1000) as ProtocolEvent.Batteries
        assertEquals(75, event.battery.headset.percent)
        assertTrue(event.battery.headset.available)
        assertNull(event.battery.left.percent); assertNull(event.battery.right.percent); assertNull(event.battery.case.percent)
    }

    private fun entry(type: Int, bytes: ByteArray) = byteArrayOf(type.toByte(), 0, bytes.size.toByte(), 0) + bytes
    private fun reply(vararg entries: ByteArray) = hex("040004003100") + byteArrayOf(entries.size.toByte()) + entries.fold(byteArrayOf()) { all, entry -> all + entry }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
