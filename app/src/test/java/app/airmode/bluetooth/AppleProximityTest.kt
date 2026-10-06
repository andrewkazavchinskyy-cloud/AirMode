package app.airmode.bluetooth

import app.airmode.domain.BatterySource
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Public SIG vector plus synthetic encrypted packets, never device keys or hardware proof. */
class AppleProximityTest {
    private val irk = hex("ec0234a357c8ad05341010a60a397d9b")
    private val encryption = hex("00112233445566778899aabbccddeeff")
    private val resolvedAddress = "70:81:94:0D:FB:AA"

    @Test fun resolvesOfficialBluetoothCoreAppendixD7WithoutByteOrderGuessing() {
        // SIG: ah(ec0234...397d9b, 708194) = 0dfbaa. Canonical address is prand || hash.
        AppleProximity(irk, null).use { keys ->
            assertTrue(keys.matchesAddress(resolvedAddress))
            assertTrue(keys.matchesAddress(resolvedAddress.lowercase()))
            assertFalse(keys.matchesAddress("70:81:94:0D:FB:AB"))
            assertFalse(keys.matchesAddress("0D:FB:AA:70:81:94"))
            assertFalse(keys.matchesAddress("F0:81:94:0D:FB:AA"))
            assertFalse(keys.matchesAddress("7081940dfbaa"))
        }
        AppleProximity(irk.reversedArray(), null).use { assertFalse(it.matchesAddress(resolvedAddress)) }
    }

    @Test fun caseUsesKnownLayoutAcrossAddressAndVariantChangesAndRejectsOldCounters() {
        AppleProximity(irk, encryption).use { keys ->
            // Plaintext sample in primary ble.md: case61%, left94% charging, right93% charging.
            val plain = hex("2920083ddedd510b00000000f7821600")
            val first = requireNotNull(keys.decode("C0:00:00:00:00:01", casePacket(plain), 1000))
            assertEquals(61, first.case.percent)
            assertFalse(first.case.charging)
            assertEquals(94, first.left.percent)
            assertTrue(first.left.charging)
            assertEquals(93, first.right.percent)
            assertTrue(first.right.charging)
            assertEquals(BatterySource.ADVERTISEMENT, first.case.source)
            assertEquals(1000L, first.case.updatedAt)
            assertNull(keys.decode("C0:00:00:00:00:02", casePacket(plain), 2000))
            val next = plain.copyOf().apply { this[3] = 0xbd.toByte(); this[6] = 0x5a; this[7] = 0x86.toByte(); this[12] = 0xf8.toByte() }
            assertTrue(requireNotNull(keys.decode("C0:00:00:00:00:03", casePacket(next), 3000)).case.charging)
            assertNull(keys.decode("C0:00:00:00:00:01", casePacket(plain), 4000))
        }
    }

    @Test fun caseRejectsUnknownLayoutsWrongKeysInvalidLevelsAndTruncatedPackets() {
        val plain = hex("2920083ddedd510b00000000f7821600")
        listOf(0 to 0x28, 1 to 0x21, 8 to 1, 10 to 1, 11 to 1, 3 to 101, 4 to 0xfe, 5 to 127).forEach { (offset, value) ->
            AppleProximity(null, encryption).use { keys ->
                assertNull(keys.decode("C0:00:00:00:00:01", casePacket(plain.copyOf().apply { this[offset] = value.toByte() }), 1000))
            }
        }
        val packet = casePacket(plain)
        AppleProximity(null, encryption).use { keys ->
            for (size in 0 until packet.size) assertNull(keys.decode("C0:00:00:00:00:01", packet.copyOf(size), 1000))
            assertNull(keys.decode("C0:00:00:00:00:01", packet + byteArrayOf(0), 1000))
            assertNull(keys.decode("C0:00:00:00:00:01", packet.copyOf().apply { this[2] = 7 }, 1000))
        }
        AppleProximity(null, ByteArray(16)).use { assertNull(it.decode("C0:00:00:00:00:01", packet, 1000)) }
    }

    @Test fun absentCaseAndPodsNeverBecome127PercentOrCharging() {
        AppleProximity(null, encryption).use { keys ->
            val plain = hex("292008ffffff510b0000000001000000")
            val battery = requireNotNull(keys.decode("C0:00:00:00:00:01", casePacket(plain), 1000))
            assertFalse(battery.known)
            listOf(battery.left, battery.right, battery.case).forEach {
                assertFalse(it.available); assertFalse(it.charging); assertEquals(1000L, it.observedAt)
            }
        }
    }

    @Test fun podsRequireResolvedIdentityBeforeEitherCoarseOrExactReadings() {
        val plain = hex("04b03d644c96ff000000000000000000")
        val packet = podsPacket(plain)
        AppleProximity(irk, encryption).use { keys ->
            assertNull(keys.decode("70:81:94:0D:FB:AB", packet, 1000))
            val exact = requireNotNull(keys.decode(resolvedAddress, packet, 1000))
            assertEquals(48, exact.left.percent); assertTrue(exact.left.charging)
            assertEquals(61, exact.right.percent); assertEquals(100, exact.case.percent)
            val unknownLayout = podsPacket(plain.copyOf().apply { this[4] = 0 })
            val coarse = requireNotNull(keys.decode(resolvedAddress, unknownLayout, 2000))
            assertEquals(80, coarse.left.percent); assertEquals(70, coarse.right.percent)
            val absent = requireNotNull(keys.decode(resolvedAddress, podsPacket(plain.copyOf().apply { this[1] = 0xff.toByte() }), 3000))
            assertEquals(80, absent.left.percent)
        }
        AppleProximity(irk, null).use { assertEquals(80, requireNotNull(it.decode(resolvedAddress, packet, 1000)).left.percent) }
        AppleProximity(null, encryption).use { assertNull(it.decode(resolvedAddress, packet, 1000)) }
    }

    @Test fun closingKeysPermanentlyDisablesReadingWithoutMutatingCallerArrays() {
        val originalIrk = irk.copyOf()
        val originalEncryption = encryption.copyOf()
        val keys = AppleProximity(irk, encryption)
        assertTrue(keys.hasIrk); assertTrue(keys.hasEncryption)
        keys.close(); keys.close()
        assertFalse(keys.hasIrk); assertFalse(keys.hasEncryption)
        assertFalse(keys.matchesAddress(resolvedAddress))
        assertNull(keys.decode(resolvedAddress, casePacket(hex("2920083ddedd510b0000000001000000")), 1000))
        assertArrayEquals(originalIrk, irk); assertArrayEquals(originalEncryption, encryption)
        assertFalse(keys.toString().contains("ec0234"))
    }

    private fun casePacket(plain: ByteArray) = byteArrayOf(7, 0x11, 6) + encrypt(plain)
    private fun podsPacket(plain: ByteArray) = ByteArray(11).apply {
        this[0] = 7; this[1] = 0x19; this[2] = 1; this[3] = 0x1b; this[4] = 0x20
        this[5] = 0x20; this[6] = 0x78; this[7] = 0x57
    } + encrypt(plain)
    private fun encrypt(plain: ByteArray) = Cipher.getInstance("AES/ECB/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(encryption, "AES")); doFinal(plain)
    }
    private fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
