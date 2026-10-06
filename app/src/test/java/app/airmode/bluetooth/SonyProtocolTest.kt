package app.airmode.bluetooth

import app.airmode.domain.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

class SonyProtocolTest {
    private fun hex(value: String) = value.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private val v1 = SonyProtocol.Generation.V1
    private val v2 = SonyProtocol.Generation.V2

    @Test fun documentedWireFramesHaveExactFramingAndChecksum() {
        // Primary wire sample: marconvcm/sony-device-center/issues/56, GET protocol info.
        assertArrayEquals(hex("3e0c000000000200000e3c"),
            SonyProtocol.encode(SonyProtocol.Frame(SonyProtocol.DATA, 0, hex("0000"))))
        assertArrayEquals(hex("3e010100000000023c"),
            SonyProtocol.encode(SonyProtocol.Frame(SonyProtocol.ACK, 1, byteArrayOf())))
    }

    @Test fun fragmentedAndCoalescedFramesAndEscapedBytesArePreserved() {
        val payload = byteArrayOf(0x3c, 0x3d, 0x3e)
        val wire = SonyProtocol.encode(SonyProtocol.Frame(SonyProtocol.DATA, 1, payload))
        for (split in wire.indices) {
            val decoder = SonyProtocol.Decoder()
            val frames = decoder.feed(wire.copyOfRange(0, split)) + decoder.feed(wire.copyOfRange(split, wire.size))
            assertEquals(1, frames.size)
            assertArrayEquals(payload, frames.single().payload)
        }
        val decoder = SonyProtocol.Decoder()
        assertEquals(2, decoder.feed(wire + wire).size)
        val incremental = SonyProtocol.Decoder()
        assertEquals(1, wire.flatMap { incremental.feed(byteArrayOf(it)) }.size)
    }

    @Test fun malformedFramingIsRejectedAndCannotPoisonNextFrame() {
        val valid = hex("3e0c000000000200000e3c")
        val malformed = listOf(
            hex("3e0c000000000200000f3c"), // checksum mismatch
            hex("3e010200000000033c"), // invalid sequence
            hex("3e0c000000000100000d3c"), // trailing bytes
            hex("3e0c00ffffffff003c"), // hostile length
            hex("3e3d003c"), // unknown escape
            hex("3e3d3c"), // truncated escape
            byteArrayOf(0x3e) + ByteArray(3000) + byteArrayOf(0x3c),
        )
        malformed.forEach { bytes ->
            val decoder = SonyProtocol.Decoder()
            assertTrue(decoder.feed(bytes).isEmpty())
            assertEquals(1, decoder.feed(valid).size)
        }
        assertTrue(SonyProtocol.Decoder().feed(valid.copyOf(valid.size - 1)).isEmpty())
    }

    @Test fun versionResponseGatesDangerousOpcodeGeneration() {
        // v1 0x3000 is documented in AndreasOlofsson/mdr-protocol; fixture is synthetic.
        assertEquals(v1, SonyProtocol.generation(hex("01003000")))
        // Public hardware payload, WF-1000XM5 firmware6.1.0, capture000002 at
        // mos9527/SonyHeadphonesClient/tests/WF-1000XM5-6.1.0/ (no unique identifiers).
        val captured = hex("0100030030180000")
        assertEquals(v2, SonyProtocol.generation(captured))
        for (size in 0 until captured.size) assertNull(SonyProtocol.generation(captured.copyOf(size)))
        assertNull(SonyProtocol.generation(hex("0100040030180000")))
        val queries = SonyProtocol.batteryQueries(v1, BatteryForm.EARBUDS, setOf(0x11, 0x15, 0x18)) +
            SonyProtocol.batteryQueries(v1, BatteryForm.HEADPHONES, setOf(0x11))
        assertTrue(queries.isNotEmpty())
        assertTrue(queries.all { it[0].toInt() and 255 == 0x10 })
        assertTrue(SonyProtocol.batteryQueries(v1, BatteryForm.HEADPHONES, emptySet()).isEmpty())
    }

    @Test fun identityAndCapabilitiesMustComeFromStrictResponses() {
        // Same public WF-1000XM5 capture, model-only payload000015, not Bluetooth display name.
        assertEquals("WF-1000XM5", SonyProtocol.model(hex("05010a57462d31303030584d35")))
        assertNull(SonyProtocol.model(hex("05010b57462d31303030584d35")))
        assertNull(SonyProtocol.model(hex("050103c080ff")))
        assertNull(SonyProtocol.model(hex("050108416972506f647335")))
        assertEquals(setOf(0x11, 0x62), SonyProtocol.features(hex("0700021162"), v1))
        assertEquals(setOf(0x10ff, 0x6b08), SonyProtocol.features(hex("07000210ff6b08"), v2))
        assertNull(SonyProtocol.features(hex("07000210ff6b08"), v1))
        assertNull(SonyProtocol.features(hex("0700021162"), v2))
        assertNull(SonyProtocol.features(hex("0700021111"), v1))
    }

    @Test fun v1NoiseWritesRetainUnrelatedParametersAndRequireCapabilities() {
        // Synthetic states following readback schema documented in sony-device-center/pull/36.
        val current = hex("670201010001010a")
        assertEquals(Mode.TRANSPARENCY, SonyProtocol.listening(current, v1, 2))
        val anc = SonyProtocol.writeMode(v1, "WH-1000XM4", setOf(0x62), current, Mode.ANC)
        assertArrayEquals(hex("6802010102010100"), anc)
        val off = SonyProtocol.writeMode(v1, "WH-1000XM4", setOf(0x62), current, Mode.OFF)
        assertArrayEquals(hex("680200010001010a"), off)
        assertEquals(setOf(Mode.OFF, Mode.ANC), SonyProtocol.supportedModes(v1, "WH-1000XM3", setOf(0x61), current))
        assertTrue(SonyProtocol.supportedModes(v1, "WH-1000XM4", emptySet(), current).isEmpty())
        assertNull(SonyProtocol.listening(current.copyOf(7), v1, 2))
    }

    @Test fun v2CapturedStateAndModelSpecificWindLayoutAreSeparated() {
        // Public WF-1000XM5 capture000069: exact seven-byte noise RET.
        val current = hex("67170100010014")
        assertEquals(Mode.OFF, SonyProtocol.listening(current, v2, 0x17))
        assertArrayEquals(hex("68170101000014"),
            SonyProtocol.writeMode(v2, "WF-1000XM5", setOf(0x6b08), current, Mode.ANC))
        assertNull(SonyProtocol.noiseSubtype(v2, "WF-1000XM5", emptySet()))
        assertNull(SonyProtocol.noiseSubtype(v2, "WF-1000XM4", setOf(0x20ff)))
        assertEquals(0x15, SonyProtocol.noiseSubtype(v2, "WF-1000XM4", setOf(0x6b08)))
        // Synthetic readback matching independently documented WF-XM4 SET in issue56.
        val wind = hex("6715010101020114")
        assertArrayEquals(hex("6815010100020114"),
            SonyProtocol.writeMode(v2, "WF-1000XM4", setOf(0x6b08), wind, Mode.ANC))
        assertNull(SonyProtocol.listening(wind, v2, 0x17))
        assertTrue(SonyProtocol.supportedModes(v2, "WF-1000XM5", setOf(0x6b08), null).isEmpty())
        // Documented ULT response/SET layout; both returned identity spellings select it.
        val ult = hex("67170101000200")
        for (name in listOf("WH-ULT900N", "ULT WEAR")) {
            assertArrayEquals(hex("6817010101020000"),
                SonyProtocol.writeMode(v2, name, setOf(0x6b08), ult, Mode.TRANSPARENCY))
        }
    }

    @Test fun noAdaptiveCommandCanBeEncodedForSony() {
        try {
            SonyProtocol.writeMode(v2, "WF-1000XM5", setOf(0x6b08), hex("67170100010014"), Mode.ADAPTIVE)
            fail("Sony manual Adaptive must not be invented")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun batteryNeverDuplicatesAggregateOrInventsExtraFieldOffsets() {
        assertEquals(48, SonyProtocol.battery(hex("11003000"), v1, BatteryForm.HEADPHONES, 50)?.headset?.percent)
        assertEquals(61, SonyProtocol.battery(hex("23003d01"), v2, BatteryForm.HEADPHONES, 50, setOf(0x20ff))?.headset?.percent)
        assertNull(SonyProtocol.battery(hex("23006400"), v2, BatteryForm.EARBUDS, 50))
        val dual = SonyProtocol.battery(hex("11023c004d01"), v1, BatteryForm.EARBUDS, 50)!!
        assertEquals(60, dual.left.percent); assertEquals(77, dual.right.percent); assertTrue(dual.right.charging)
        assertEquals(BatterySource.PROTOCOL, dual.left.source)
        assertFalse(SonyProtocol.battery(hex("1103ff00"), v1, BatteryForm.EARBUDS, 50)!!.case.available)
        assertNull(SonyProtocol.battery(hex("11007800"), v1, BatteryForm.HEADPHONES, 50))
        assertNull(SonyProtocol.battery(hex("23006402"), v2, BatteryForm.HEADPHONES, 50))
        // A shared service/version is insufficient; unsolicited responses need live capabilities too.
        assertNull(SonyProtocol.battery(hex("23003d01"), v2, BatteryForm.HEADPHONES, 50))
        assertTrue(SonyProtocol.batteryQueries(v2, BatteryForm.HEADPHONES, emptySet()).isEmpty())
        assertArrayEquals(hex("2200"), SonyProtocol.batteryQueries(v2, BatteryForm.HEADPHONES, setOf(0x20ff)).single())
    }

    @Test fun v2ThresholdRecordsPreserveUnequalRawPercentagesAndRequireAdvertisedFeatures() {
        val features = setOf(0x29ff, 0x2aff)
        val queries = SonyProtocol.batteryQueries(v2, BatteryForm.EARBUDS, features)
        assertArrayEquals(hex("2209"), queries[0]); assertArrayEquals(hex("220a"), queries[1])
        // WF-XM5 capture000131/000135 and LinkBuds Clip equalizer000133,
        // listening000129 have unequal readings. Public filtered wire payloads only.
        val xm5 = SonyProtocol.battery(hex("2309000064006464"), v2, BatteryForm.EARBUDS, 50, features)!!
        assertEquals(0, xm5.left.percent); assertEquals(100, xm5.right.percent)
        assertEquals(53, SonyProtocol.battery(hex("230a35001e"), v2, BatteryForm.EARBUDS, 50, features)!!.case.percent)
        val clip = SonyProtocol.battery(hex("23094e0000006464"), v2, BatteryForm.EARBUDS, 50, features)!!
        assertEquals(78, clip.left.percent); assertEquals(0, clip.right.percent)
        val unequal = SonyProtocol.battery(hex("2309000038006464"), v2, BatteryForm.EARBUDS, 50, features)!!
        assertEquals(0, unequal.left.percent); assertEquals(56, unequal.right.percent)
        assertEquals(70, SonyProtocol.battery(hex("230a46001f"), v2, BatteryForm.EARBUDS, 50, features)!!.case.percent)
        assertNull(SonyProtocol.battery(hex("2309000038006464"), v2, BatteryForm.EARBUDS, 50))
        assertNull(SonyProtocol.battery(hex("230a35001e"), v2, BatteryForm.EARBUDS, 50, setOf(0x29ff)))
        assertNull(SonyProtocol.battery(hex("230900003800646401"), v2, BatteryForm.EARBUDS, 50, features))
        assertNull(SonyProtocol.battery(hex("2309000238006464"), v2, BatteryForm.EARBUDS, 50, features))
        assertNull(SonyProtocol.battery(hex("23090000380064ff"), v2, BatteryForm.EARBUDS, 50, features))
        assertNull(SonyProtocol.battery(hex("230a3500ff"), v2, BatteryForm.EARBUDS, 50, features))
        val absent = SonyProtocol.battery(hex("2309ff0038016464"), v2, BatteryForm.EARBUDS, 50, features)!!
        assertFalse(absent.left.available); assertNull(absent.left.percent); assertTrue(absent.right.charging)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun sameModeRetryAt700msDoesNotQueueBehindReadbackOrCloseSession() = runTest {
        var closed = false
        var writes = 0
        val queue = SonyModeWriteQueue(backgroundScope) { closed = true }
        queue.send(Mode.ANC) { delivered -> writes++; delivered(); delay(2_000) }
        advanceTimeBy(700)
        withTimeout(400) { queue.send(Mode.ANC) { fail("Retry must reuse physical delivery") } }
        assertEquals(1, writes)
        assertFalse(closed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun differentSonyTargetsWaitForPreviousOrderedReadback() = runTest {
        val written = mutableListOf<Mode>()
        val queue = SonyModeWriteQueue(backgroundScope) { fail("Unexpected cancellation") }
        queue.send(Mode.ANC) { delivered -> written += Mode.ANC; delivered(); delay(2_000) }
        val second = async { queue.send(Mode.OFF) { delivered -> written += Mode.OFF; delivered() } }
        runCurrent()
        assertEquals(listOf(Mode.ANC), written)
        assertFalse(second.isCompleted)
        advanceTimeBy(2_000); runCurrent(); second.await()
        assertEquals(listOf(Mode.ANC, Mode.OFF), written)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancelledUnsentTargetPreservesHealthyOriginalAndNeverWritesStaleMode() = runTest {
        var closed = false
        val worker = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        val written = mutableListOf<Mode>()
        val queue = SonyModeWriteQueue(worker) { closed = true; worker.cancel() }
        queue.send(Mode.ANC) { delivered -> written += Mode.ANC; delivered(); delay(2_000) }
        assertNull(withTimeoutOrNull(400) {
            queue.send(Mode.OFF) { delivered -> written += Mode.OFF; delivered() }
            true
        })
        assertFalse(closed)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(listOf(Mode.ANC), written)
        // Once the original operation releases the channel, a new explicit target still works.
        queue.send(Mode.TRANSPARENCY) { delivered -> written += Mode.TRANSPARENCY; delivered() }
        assertEquals(listOf(Mode.ANC, Mode.TRANSPARENCY), written)
        worker.cancel()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancelledPhysicalDeliveryClosesSessionAndStopsAcceptedWrite() = runTest {
        var closed = false
        val worker = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext[Job]))
        var writes = 0
        val queue = SonyModeWriteQueue(worker) { closed = true; worker.cancel() }
        assertNull(withTimeoutOrNull(400) {
            queue.send(Mode.ANC) { delivered -> delay(800); writes++; delivered() }
            true
        })
        assertTrue(closed)
        advanceTimeBy(1_000); runCurrent()
        assertEquals(0, writes)
    }

}
