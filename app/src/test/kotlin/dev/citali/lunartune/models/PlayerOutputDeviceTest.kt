/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.models

import android.bluetooth.BluetoothClass
import dev.citali.lunartune.constants.PlayerDesignStyle
import dev.citali.lunartune.utils.parsePlayerDesignStyleOverrides
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerOutputDeviceTest {
    @Test
    fun genericBluetoothDevices_defaultToEarbudsAirpodsIcon() {
        assertEquals(
            BluetoothAudioDeviceCategory.Earbuds,
            classifyBluetoothAudioDevice(
                deviceName = "BSV-303",
                bluetoothDeviceClass = BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            ),
        )
        assertEquals(
            BluetoothAudioDeviceCategory.Earbuds,
            classifyBluetoothAudioDevice(
                deviceName = "AirPods Pro",
                bluetoothDeviceClass = BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            ),
        )
    }

    @Test
    fun headphonesModelsAndClass_classifyAsHeadphones() {
        assertEquals(
            BluetoothAudioDeviceCategory.Headphones,
            classifyBluetoothAudioDevice(
                deviceName = "AirPods Max",
                bluetoothDeviceClass = BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
            ),
        )
        assertEquals(
            BluetoothAudioDeviceCategory.Headphones,
            classifyBluetoothAudioDevice(
                deviceName = "Sony WH-1000XM5",
                bluetoothDeviceClass = null,
            ),
        )
        assertEquals(
            BluetoothAudioDeviceCategory.Headphones,
            classifyBluetoothAudioDevice(
                deviceName = "Studio Cans",
                bluetoothDeviceClass = BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
            ),
        )
    }

    @Test
    fun carBluetoothClassUiModeAndHeadUnits_classifyAsCar() {
        assertEquals(
            BluetoothAudioDeviceCategory.Car,
            classifyBluetoothAudioDevice(
                deviceName = "BT Audio",
                bluetoothDeviceClass = BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
            ),
        )
        assertEquals(
            BluetoothAudioDeviceCategory.Car,
            classifyBluetoothAudioDevice(
                deviceName = "Car Multimedia",
                bluetoothDeviceClass = null,
            ),
        )
        assertEquals(
            BluetoothAudioDeviceCategory.Car,
            classifyBluetoothAudioDevice(
                deviceName = "BSV-303",
                bluetoothDeviceClass = null,
                isCarUiMode = true,
            ),
        )
    }

    @Test
    fun legacyImmersiveOverrides_migrateToImmersiveV7() {
        val parsed = parsePlayerDesignStyleOverrides("song1\tV7_LEGACY\nsong2\tV10")
        assertEquals(PlayerDesignStyle.V7, parsed["song1"])
        assertEquals(PlayerDesignStyle.V10, parsed["song2"])
    }
}
