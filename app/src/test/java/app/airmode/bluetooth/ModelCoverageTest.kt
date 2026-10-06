package app.airmode.bluetooth

import app.airmode.domain.Mode
import app.airmode.domain.tileCycle
import app.airmode.domain.DeviceState
import app.airmode.domain.ConnectionState
import org.junit.Assert.*
import org.junit.Test

class ModelCoverageTest {
    @Test fun earlyModeConfirmationCannotAdmitAnotherSonyWriteWhileChannelIsBusy() {
        val model = requireNotNull(ModelId.sony("WH-1000XM5", setOf(Mode.OFF, Mode.ANC), BatteryForm.HEADPHONES))
        val state = DeviceState(connection = ConnectionState.SessionReady, model = model, mode = Mode.ANC,
            controlBusy = true)
        assertFalse(state.canSwitch)
        assertTrue(state.copy(controlBusy = false).canSwitch)
    }
    @Test fun oldAirPodsHaveBatteryWithoutNoiseCommandsAndCasesAreNotModels() {
        listOf("A1523", "A1722", "A2031", "A2032", "A2564", "A2565").forEach {
            val model = requireNotNull(ModelId.fromNumber(it))
            assertFalse(model.anc)
            assertTrue(model.supportedModes.isEmpty())
            assertEquals(BatteryForm.EARBUDS, model.batteryForm)
        }
        listOf("A1602", "A1938", "A2190", "A3122", "A3058", "A3530", "A3529").forEach {
            assertNull(ModelId.fromNumber(it))
        }
    }

    @Test fun onlyNewAncHardwareOffersAdaptiveAndMaxHasNoCase() {
        val three = setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY)
        listOf("A2083", "A2084", "A2096", "A3184").forEach {
            assertEquals(three, ModelId.fromNumber(it)?.supportedModes)
        }
        listOf("A2931", "A2699", "A2698", "A3047", "A3048", "A3049", "A3063", "A3064", "A3065", "A3454").forEach {
            assertEquals(Mode.entries.toSet(), ModelId.fromNumber(it)?.supportedModes)
        }
        listOf("A2096", "A3184", "A3454").forEach {
            assertEquals(BatteryForm.HEADPHONES, ModelId.fromNumber(it)?.batteryForm)
        }
    }

    @Test fun sonyIdentityCannotInventAdaptiveOrComeFromAnArbitraryName() {
        val sony = requireNotNull(ModelId.sony("WH-1000XM5", Mode.entries.toSet(), BatteryForm.HEADPHONES))
        assertEquals(DeviceVendor.SONY, sony.vendor)
        assertFalse(Mode.ADAPTIVE in sony.supportedModes)
        assertNull(ModelId.sony("Someone's headphones", Mode.entries.toSet(), BatteryForm.HEADPHONES))
        assertNull(ModelId.sony("WH-1000XM5\u0000", Mode.entries.toSet(), BatteryForm.HEADPHONES))
        assertNull(ModelId.sony("WH-1000XM5", setOf(Mode.ANC), BatteryForm.HEADPHONES))
    }

    @Test fun oldSavedAdaptivePreferencesStillProduceTwoCapableTileModes() {
        val supported = setOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY)
        assertEquals(listOf(Mode.OFF, Mode.ANC, Mode.TRANSPARENCY), tileCycle(supported, setOf(Mode.ANC, Mode.ADAPTIVE)))
        assertEquals(listOf(Mode.OFF, Mode.ANC), tileCycle(supported, setOf(Mode.OFF, Mode.ANC, Mode.ADAPTIVE)))
        assertTrue(tileCycle(emptySet(), Mode.entries.toSet()).isEmpty())
    }
}
