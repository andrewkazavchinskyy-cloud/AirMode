package app.airmode.bluetooth

import app.airmode.domain.Battery
import app.airmode.domain.BatteryReading
import app.airmode.domain.BatterySource
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Original decoder from public wire observations:
 * https://github.com/stuffz/airpods/blob/main/docs/ble.md
 * Address hashing follows Bluetooth Core Vol 3, Part H 2.2.2 / Appendix D.7.
 * Current-session keys only. A keyed layout match is NOT an authenticated BLE message.
 */
class AppleProximity(irk: ByteArray?, encryptionKey: ByteArray?) : AutoCloseable {
    private val identityKey = irk?.also { require(it.size == 16) }?.copyOf()
    private val cipherKey = encryptionKey?.also { require(it.size == 16) }?.copyOf()
    private var closed = false
    private var caseCounter: Long? = null

    init { require(identityKey != null || cipherKey != null) }

    val hasIrk get() = synchronized(this) { !closed && identityKey != null }
    val hasEncryption get() = synchronized(this) { !closed && cipherKey != null }

    /** Android's colon-separated address is most-significant-octet first. */
    @Synchronized fun matchesAddress(address: String): Boolean {
        if (closed) return false
        val key = identityKey ?: return false
        if (!address.matches(Regex("[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}"))) return false
        val bytes = address.split(':').map { it.toInt(16).toByte() }.toByteArray()
        if (bytes.u(0) and 0xC0 != 0x40) return false
        val input = ByteArray(16)
        bytes.copyInto(input, 13, 0, 3)
        val hash = crypt(key, input, Cipher.ENCRYPT_MODE).copyOfRange(13, 16)
        return MessageDigest.isEqual(hash, bytes.copyOfRange(3, 6))
    }

    @Synchronized fun decode(address: String, data: ByteArray, now: Long): Battery? {
        if (closed || now <= 0) return null
        return when {
            data.size == 19 && data.u(0) == 7 && data.u(1) == 0x11 && data.u(2) == 6 -> decodeCase(data, now)
            data.size == 27 && matchesAddress(address) -> decodePods(data, now)
            else -> null
        }
    }

    private fun decodeCase(data: ByteArray, now: Long): Battery? {
        val key = cipherKey ?: return null
        val block = crypt(key, data.copyOfRange(3, 19), Cipher.DECRYPT_MODE)
        try {
            if (block.u(0) != 0x29 || block.u(1) != 0x20 ||
                listOf(8, 10, 11).any { block.u(it) != 0 } || (3..5).any { !validLevel(block.u(it)) }) return null
            val counter = (0..3).fold(0L) { result, i -> result or (block.u(12 + i).toLong() shl (8 * i)) }
            val previous = caseCounter
            if (previous != null && counter <= previous) return null
            // Unknown variant bytes 6/7 and transient byte 9 are never treated as identity.
            caseCounter = counter
            return Battery(left = reading(block.u(4), now), right = reading(block.u(5), now),
                case = reading(block.u(3), now))
        } finally { block.fill(0) }
    }

    private fun decodePods(data: ByteArray, now: Long): Battery? {
        val coarse = AdvertParser.parse(data, now) ?: return null
        val key = cipherKey ?: return coarse
        val block = crypt(key, data.copyOfRange(11, 27), Cipher.DECRYPT_MODE)
        try {
            if (block.u(4) != 0x4C || block.u(5) != 0x96 || block.u(6) != 0xFF ||
                (1..3).any { !validLevel(block.u(it)) }) return coarse
            val primaryLeft = data.u(5) and 0x20 != 0
            val primary = reading(block.u(1), now)
            val secondary = reading(block.u(2), now)
            // Absent decrypted fields leave the already attributed coarse observation intact.
            return Battery(
                left = (if (primaryLeft) primary else secondary).takeIf { it.available } ?: coarse.left,
                right = (if (primaryLeft) secondary else primary).takeIf { it.available } ?: coarse.right,
                case = reading(block.u(3), now).takeIf { it.available } ?: coarse.case,
            )
        } finally { block.fill(0) }
    }

    private fun validLevel(value: Int) = value == 0xFF || value and 0x7F <= 100
    private fun reading(value: Int, now: Long): BatteryReading = if (value == 0xFF)
        BatteryReading(source = BatterySource.ADVERTISEMENT, observedAt = now)
    else BatteryReading(value and 0x7F, value and 0x80 != 0, now, source = BatterySource.ADVERTISEMENT)

    private fun crypt(key: ByteArray, block: ByteArray, mode: Int): ByteArray =
        Cipher.getInstance("AES/ECB/NoPadding").run { init(mode, SecretKeySpec(key, "AES")); doFinal(block) }

    @Synchronized override fun close() {
        closed = true
        identityKey?.fill(0)
        cipherKey?.fill(0)
        caseCounter = null
    }
}
