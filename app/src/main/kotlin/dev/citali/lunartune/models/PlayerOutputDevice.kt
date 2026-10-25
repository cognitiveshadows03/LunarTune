/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.models

import android.bluetooth.BluetoothClass
import android.media.AudioDeviceInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class PlayerOutputDevice(
    val imageVector: ImageVector,
    val isProduct: Boolean,
    val defaultName: String,
) {
    Bluetooth(Icons.Rounded.Bluetooth, isProduct = true, defaultName = "Bluetooth"),
    Usb(Icons.Rounded.Usb, isProduct = true, defaultName = "USB"),
    Headset(Icons.Rounded.Headphones, isProduct = false, defaultName = "Headset"),
    Hdmi(Icons.Rounded.Cable, isProduct = false, defaultName = "HDMI"),
    BuiltinSpeaker(Icons.Rounded.Speaker, isProduct = false, defaultName = "Speaker"),
    Unknown(Icons.Rounded.Speaker, isProduct = false, defaultName = "Speaker"),
    ;

    companion object {
        fun from(info: AudioDeviceInfo?): PlayerOutputDevice =
            when (info?.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_HEARING_AID,
                -> Bluetooth

                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                -> Usb

                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                -> Headset

                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC,
                -> Hdmi

                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                -> BuiltinSpeaker

                else -> Unknown
            }
    }
}

@Immutable
enum class BluetoothAudioDeviceCategory {
    Earbuds,
    Headphones,
    Car,
}

@Immutable
data class ActiveOutputDevice(
    val type: PlayerOutputDevice,
    val name: String,
    val bluetoothCategory: BluetoothAudioDeviceCategory = BluetoothAudioDeviceCategory.Earbuds,
)

private val CAR_NAME_KEYWORDS =
    listOf(
        "carplay",
        "android auto",
        "car audio",
        "car multimedia",
        "car stereo",
        "car bt",
        "carkit",
        "car kit",
        "my car",
        "handsfree link",
        "handsfreelink",
        "uconnect",
        "idrive",
        "mbux",
        "entune",
        "mylink",
        "carlinkit",
        "aawireless",
        "toyota",
        "honda",
        "hyundai",
        "kia",
        "mazda",
        "nissan",
        "subaru",
        "ford",
        "chevrolet",
        "volkswagen",
        "audi",
        "bmw",
        "mercedes",
        "porsche",
        "lexus",
        "acura",
        "infiniti",
        "volvo",
        "tesla",
        "suzuki",
        "maruti",
        "mahindra",
        "skoda",
        "renault",
        "peugeot",
        "jeep",
        "dodge",
        "pioneer",
        "kenwood",
        "alpine",
        "sony xav",
    )

private val EARBUDS_NAME_KEYWORDS =
    listOf(
        "airpods",
        "earbuds",
        "earbud",
        "earpods",
        "buds",
        "wf-",
        "tws",
        "nothing ear",
        "linkbuds",
        "beats fit",
        "powerbeats",
        "freebuds",
        "encobuds",
        "jabra elite",
        "neckband",
        "bullets",
        "airdopes",
        "space a40",
        "liberty ",
    )

private val HEADPHONES_NAME_KEYWORDS =
    listOf(
        "airpods max",
        "headphones",
        "headphone",
        "wh-",
        "xm3",
        "xm4",
        "xm5",
        "xm6",
        "ult wear",
        "ch510",
        "ch520",
        "ch700",
        "ch710",
        "ch720",
        "quietcomfort",
        "bose qc",
        "bose 700",
        "bose nc",
        "beats studio",
        "beats solo",
        "beats pro",
        "momentum",
        "accentum",
        "hd 350",
        "hd 450",
        "hd 458",
        "space q",
        "space one",
        "life q",
        "soundcore q",
        "anker q",
        "crusher",
        "hesh",
        "major ",
        "monitor ",
        "ath-m",
        "ath-s",
        "px7",
        "px8",
        "bathys",
        "sonos ace",
        "dyson zone",
        "jbl tune 5",
        "jbl tune 6",
        "jbl tune 7",
        "jbl live 4",
        "jbl live 6",
        "jbl live 7",
        "jbl tour one",
        "rockerz 4",
        "rockerz 5",
        "rockerz 6",
        "nirvanaa 751",
    )

internal fun classifyBluetoothAudioDevice(
    deviceName: String?,
    bluetoothDeviceClass: Int?,
    isCarUiMode: Boolean = false,
): BluetoothAudioDeviceCategory {
    if (isCarUiMode || bluetoothDeviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) {
        return BluetoothAudioDeviceCategory.Car
    }

    val normalized = deviceName?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (normalized.isNotEmpty()) {
        if (normalized.contains("airpods max")) {
            return BluetoothAudioDeviceCategory.Headphones
        }
        if (CAR_NAME_KEYWORDS.any { normalized.contains(it) }) {
            return BluetoothAudioDeviceCategory.Car
        }
        if (EARBUDS_NAME_KEYWORDS.any { normalized.contains(it) }) {
            return BluetoothAudioDeviceCategory.Earbuds
        }
        if (HEADPHONES_NAME_KEYWORDS.any { normalized.contains(it) }) {
            return BluetoothAudioDeviceCategory.Headphones
        }
    }

    if (bluetoothDeviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES) {
        return BluetoothAudioDeviceCategory.Headphones
    }

    return BluetoothAudioDeviceCategory.Earbuds
}
