package app.airmode.bluetooth

import app.airmode.domain.Mode
import app.airmode.domain.BatterySource
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures use public fields; tests explicitly label the user-provided hardware captures. */
class ProtocolTest {
    @Test fun exactSupportedModelsOnly() {
        val groups = listOf(
            Triple(listOf("A3053", "A3050", "A3054"), "AirPods 4", false),
            Triple(listOf("A3056", "A3055", "A3057"), "AirPods 4", true),
            Triple(listOf("A3531", "A3532", "A3533", "A3439", "A3440", "A3441"), "AirPods 5", true),
        )
        groups.forEach { (numbers, generation, anc) ->
            numbers.forEach { number -> assertEquals(ModelId(number, generation, anc), ModelId.fromNumber(number)) }
        }
        assertEquals("A3056", ModelId.fromNumber(" a3056 ")?.number)
        listOf("AirPods 5", "A3048", "A3058", "A35310", "", "A3056\u0000").forEach {
            assertNull(ModelId.fromNumber(it))
        }
    }

    @Test fun advertisementPrimarySidesAndChargingMoveTogether() {
        val leftPrimary = advert(0x20, 0x78, 0x57)
        val left = requireNotNull(AdvertParser.parse(leftPrimary, 1000))
        assertEquals(80, left.left.percent)
        assertTrue(left.left.charging)
        assertEquals(70, left.right.percent)
        assertFalse(left.right.charging)
        assertEquals(70, left.case.percent)
        assertTrue(left.case.charging)
        assertEquals(1000L, left.case.updatedAt)
        val right = requireNotNull(AdvertParser.parse(advert(0, 0x78, 0x57), 1000))
        assertEquals(left.left, right.right)
        assertEquals(left.right, right.left)
        assertEquals(left.case, right.case)
    }

    @Test fun advertisementUnknownNeverBecomesZero() {
        val unknown = requireNotNull(AdvertParser.parse(advert(0x20, 0xFF, 0x7F), 1000))
        assertFalse(unknown.known)
        assertFalse(unknown.left.charging)
        assertEquals(0L, unknown.left.updatedAt)
        for (nibble in 11..15) {
            assertNull(AdvertParser.parse(advert(0x20, nibble, nibble), 1000)?.left?.percent)
        }
        assertEquals(0, AdvertParser.parse(advert(0x20, 0, 0), 1000)?.left?.percent)
    }

    @Test fun advertisementRejectsDifferentLayoutsAndTruncation() {
        val valid = advert(0x20, 0x78, 0x57)
        for (length in 0 until valid.size) assertNull(AdvertParser.parse(valid.copyOf(length), 1000))
        assertNull(AdvertParser.parse(valid + byteArrayOf(0), 1000))
        listOf(0 to 0x06, 1 to 0x11, 2 to 0, 4 to 0x21).forEach { (offset, value) ->
            assertNull(AdvertParser.parse(valid.copyOf().apply { this[offset] = value.toByte() }, 1000))
        }
    }

    @Test fun documentedBatteryComponentTableWinsOverConflictingExampleProse() {
        // Public AAP Definitions example; original component table says 02=right, 04=left.
        val packet = hex("04000400040003020164020104016301010801110201")
        val event = AapProtocol.parse(packet, 1000) as ProtocolEvent.Batteries
        assertEquals(99, event.battery.left.percent)
        assertTrue(event.battery.left.charging)
        assertEquals(100, event.battery.right.percent)
        assertFalse(event.battery.right.charging)
        assertEquals(17, event.battery.case.percent)
    }

    @Test fun batteryUnknownOrDisconnectedDoesNotInventReading() {
        listOf(0, 4).forEach { status ->
            val event = AapProtocol.parse(hex("040004000400010401ff${status.toString(16).padStart(2, '0')}01"), 1000)
                as ProtocolEvent.Batteries
            assertNull(event.battery.left.percent)
            assertEquals(0L, event.battery.left.updatedAt)
            assertFalse(event.battery.left.available)
            assertEquals(BatterySource.PROTOCOL, event.battery.left.source)
            assertEquals(1000L, event.battery.left.observedAt)
        }
    }

    @Test fun capturedAirPods5BatteryOrderingAndUnavailableCaseKeepHistoryHonest() {
        // User-provided AirPods 5 / Pixel 10 Pro CP41.260831.007.A3 debug report.
        fun battery(packet: String, now: Long) = (AapProtocol.parse(hex(packet), now) as ProtocolEvent.Batteries).battery
        val live = battery("0400040004000302013d020104013001010801640201", 1000)
        assertEquals(48, live.left.percent)
        assertTrue(live.left.charging)
        assertEquals(61, live.right.percent)
        assertFalse(live.right.charging)
        assertEquals(100, live.case.percent)
        assertTrue(live.case.available)
        listOf(
            "04000400040003040130020102013d02010801000401",
            "0400040004000302013d020104013002010801ff0401",
        ).forEachIndexed { index, packet ->
            val observedAt = 2000L + index
            val incoming = battery(packet, observedAt)
            assertEquals(48, incoming.left.percent)
            assertEquals(61, incoming.right.percent)
            assertNull(incoming.case.percent)
            assertFalse(incoming.case.available)
            val merged = live.merge(incoming)
            assertEquals(100, merged.case.percent)
            assertFalse(merged.case.available)
            assertEquals(1000L, merged.case.updatedAt)
            assertEquals(observedAt, merged.case.observedAt)
            assertFalse(merged.case.charging)
        }
        val charging = battery("0400040004000302013d010104013001010801640201", 3000)
        assertTrue(charging.left.charging)
        assertTrue(charging.right.charging)
        assertEquals(100, charging.case.percent)
        assertTrue(charging.case.available)
    }

    @Test fun missingCaseIsDistinctFromExplicitlyUnavailableCase() {
        val initial = (AapProtocol.parse(hex("0400040004000302013d020104013001010801640201"), 1000)
            as ProtocolEvent.Batteries).battery
        val onlyLeft = (AapProtocol.parse(hex("040004000400010401300201"), 2000) as ProtocolEvent.Batteries).battery
        assertEquals(0L, onlyLeft.case.observedAt)
        assertEquals(initial.case, initial.merge(onlyLeft).case)
    }

    @Test fun batteryRejectsMalformedCountMarkersLevelsAndDuplicates() {
        val valid = hex("040004000400010401640101")
        for (length in 0 until valid.size) assertNull(AapProtocol.parse(valid.copyOf(length), 1000))
        assertNull(AapProtocol.parse(valid + byteArrayOf(0), 1000))
        listOf(0 to 0, 5 to 1, 6 to 0, 6 to 4, 7 to 1, 8 to 0, 9 to 101, 10 to 3, 11 to 0)
            .forEach { (offset, value) ->
                assertNull(AapProtocol.parse(valid.copyOf().apply { this[offset] = value.toByte() }, 1000))
            }
        assertNull(AapProtocol.parse(hex("0400040004000204016401010401500201"), 1000))
    }

    @Test fun listeningRequiresExactValidResponse() {
        Mode.entries.forEach { mode ->
            assertEquals(ProtocolEvent.Listening(mode), AapProtocol.parse(AapProtocol.listening(mode), 1000))
        }
        val valid = AapProtocol.listening(Mode.ANC)
        for (length in 0 until valid.size) assertNull(AapProtocol.parse(valid.copyOf(length), 1000))
        assertNull(AapProtocol.parse(valid + byteArrayOf(0), 1000))
        listOf(6 to 0x28, 7 to 0, 7 to 5, 8 to 1).forEach { (offset, value) ->
            assertNull(AapProtocol.parse(valid.copyOf().apply { this[offset] = value.toByte() }, 1000))
        }
    }

    @Test fun earsRequireExactDocumentedStatesAndCapabilitiesNeverConfirmMode() {
        for (primary in 0..2) for (secondary in 0..2) {
            val packet = hex("040004000600") + byteArrayOf(primary.toByte(), secondary.toByte())
            assertEquals(ProtocolEvent.Ears(primary, secondary), AapProtocol.parse(packet, 1000))
        }
        val valid = hex("0400040006000002")
        for (length in 0 until valid.size) assertNull(AapProtocol.parse(valid.copyOf(length), 1000))
        assertNull(AapProtocol.parse(valid + byteArrayOf(0), 1000))
        assertNull(AapProtocol.parse(hex("0400040006000300"), 1000))
        assertNull(AapProtocol.parse(hex("04000400060000ff"), 1000))
        val capabilities = AapProtocol.adaptiveCapabilities()
        assertArrayEquals(hex("040004004d00ff00000000000000"), capabilities)
        assertNull(AapProtocol.parse(capabilities, 1000))
        assertNull(AapProtocol.parse(hex("040004002b00"), 1000))
    }

    @Test fun metadataReadsOnlyDesignatedFields() {
        val metadata = metadata("Переименованные", "A3056")
        assertEquals(ProtocolEvent.Model("A3056", "Переименованные"), AapProtocol.parse(metadata, 1000))
        assertEquals(ProtocolEvent.Model("A3048", "AirPods"), AapProtocol.parse(metadata("AirPods", "A3048"), 1000))
        assertNull(AapProtocol.parse(metadata("A3056", "NoModel"), 1000))
        assertNull(AapProtocol.parse(metadata("AirPods", "A3056", "Other"), 1000))
    }

    @Test fun metadataRejectsTruncatedEnvelopeStringsAndInvalidUtf8() {
        val valid = metadata("AirPods", "A3531")
        for (length in 0 until valid.size) assertNull(AapProtocol.parse(valid.copyOf(length), 1000))
        assertNull(AapProtocol.parse(valid + byteArrayOf(0), 1000))
        listOf(6 to 3, 9 to 5, 10 to 1, 11 to 0xFF).forEach { (offset, value) ->
            assertNull(AapProtocol.parse(valid.copyOf().apply { this[offset] = value.toByte() }, 1000))
        }
    }

    @Test fun handshakeAcknowledgementIsNeverFabricatedByOutgoingHandshake() {
        assertFalse(AapProtocol.handshakeAcknowledged(AapProtocol.handshake()))
        assertFalse(AapProtocol.handshakeAcknowledged(byteArrayOf(1, 0, 4)))
        assertFalse(AapProtocol.handshakeAcknowledged(byteArrayOf(1, 0, 4, 0)))
        assertFalse(AapProtocol.handshakeAcknowledged(byteArrayOf(1, 0, 4, 0, 0)))
        assertTrue(AapProtocol.handshakeAcknowledged(hex("010004000000")))
        assertFalse(AapProtocol.handshakeAcknowledged(hex("010004008500")))
        assertFalse(AapProtocol.handshakeAcknowledged(hex("010004000001")))
        assertFalse(AapProtocol.handshakeAcknowledged(hex("010005000000")))
        assertFalse(AapProtocol.handshakeAcknowledged(hex("010004000000").copyOf(65)))
        assertArrayEquals(hex("00000400010002000000000000000000"), AapProtocol.handshake())
        assertArrayEquals(hex("040004000f00fffffeff"), AapProtocol.notifications())
        assertArrayEquals(hex("040004000f00ffffffff"), AapProtocol.allNotifications())
    }

    @Test fun diagnosticsExcludeIdentityBodiesButRetainBatteryLayout() {
        val identity = metadata("PrivateName", "A3531") + "PrivateSerial".toByteArray()
        val summary = ProtocolDiagnostics.packetSummary(identity, ProtocolEvent.Model("A3531", "PrivateName"))
        assertFalse(summary.contains("PrivateName"))
        assertFalse(summary.contains("PrivateSerial"))
        assertFalse(summary.contains("50726976617465"))
        val battery = hex("040004000400010401640101")
        assertTrue(ProtocolDiagnostics.packetSummary(battery, AapProtocol.parse(battery, 1)).contains("battery=040004000400010401640101"))
        assertFalse(ProtocolDiagnostics.packetSummary(battery.copyOf(65), null).contains("battery="))
        val malformedBattery = hex("040004000400") + "PrivateSerial".toByteArray()
        assertFalse(ProtocolDiagnostics.packetSummary(malformedBattery, null).contains("50726976617465"))
        val ears = hex("0400040006000002")
        assertTrue(ProtocolDiagnostics.packetSummary(ears, AapProtocol.parse(ears, 1)).contains("ears primary=0 secondary=2"))
    }

    private fun advert(status: Int, buds: Int, flags: Int) = ByteArray(27).apply {
        this[0] = 7; this[1] = 0x19; this[2] = 1; this[3] = 0x1B; this[4] = 0x20
        this[5] = status.toByte(); this[6] = buds.toByte(); this[7] = flags.toByte()
    }

    private fun metadata(name: String, model: String, maker: String = "Apple Inc."): ByteArray {
        // Synthetic same envelope as documented capture, without captured serials or encrypted data.
        val body = byteArrayOf(4, 0) + "$name\u0000$model\u0000$maker\u0000".toByteArray()
        return hex("040004001d0002") + byteArrayOf(body.size.toByte(), (body.size ushr 8).toByte()) + body
    }

    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
