/*
 * LunarTune (2026)
 * © cognitiveshadows03 — github.com/cognitiveshadows03
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package dev.citali.lunartune.playback

import android.Manifest
import android.annotation.SuppressLint
import android.app.UiModeManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import dev.citali.lunartune.models.ActiveOutputDevice
import dev.citali.lunartune.models.BluetoothAudioDeviceCategory
import dev.citali.lunartune.models.PlayerOutputDevice
import dev.citali.lunartune.models.classifyBluetoothAudioDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputResolver(
    private val context: Context,
    private val audioManager: AudioManager,
) {
    private val mediaQueryAttributes =
        android.media.AudioAttributes
            .Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

    private val _activeAudioDevice =
        MutableStateFlow(
            ActiveOutputDevice(PlayerOutputDevice.Unknown, PlayerOutputDevice.Unknown.defaultName),
        )

    val activeAudioDevice = _activeAudioDevice.asStateFlow()

    fun refresh() {
        _activeAudioDevice.value = resolveActiveDevice()
    }

    private fun resolveActiveDevice(): ActiveOutputDevice {
        val device =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                resolveActiveDeviceApi33() ?: resolveActiveDeviceHeuristic()
            } else {
                resolveActiveDeviceHeuristic()
            }

        val type = PlayerOutputDevice.from(device)
        val rawProductName =
            device
                ?.productName
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        if (type == PlayerOutputDevice.Bluetooth) {
            val btMetadata = resolveBluetoothDeviceMetadata(device, rawProductName)
            val resolvedName =
                btMetadata.displayName
                    ?: rawProductName?.takeUnless { it.equals(Build.MODEL, ignoreCase = true) }
                    ?: rawProductName
                    ?: type.defaultName
            val isCarUiMode =
                runCatching {
                    context.getSystemService(UiModeManager::class.java)?.currentModeType ==
                        Configuration.UI_MODE_TYPE_CAR
                }.getOrDefault(false)
            val category =
                classifyBluetoothAudioDevice(
                    deviceName = resolvedName,
                    bluetoothDeviceClass = btMetadata.deviceClass,
                    isCarUiMode = isCarUiMode,
                )
            return ActiveOutputDevice(
                type = type,
                name = resolvedName,
                bluetoothCategory = category,
            )
        }

        val name =
            if (type.isProduct) {
                rawProductName ?: type.defaultName
            } else {
                type.defaultName
            }
        return ActiveOutputDevice(
            type = type,
            name = name,
            bluetoothCategory = BluetoothAudioDeviceCategory.Earbuds,
        )
    }

    private data class BluetoothMetadataSnapshot(
        val displayName: String? = null,
        val deviceClass: Int? = null,
    )

    @SuppressLint("MissingPermission")
    private fun resolveBluetoothDeviceMetadata(
        audioDevice: AudioDeviceInfo?,
        rawProductName: String?,
    ): BluetoothMetadataSnapshot {
        if (!hasBluetoothConnectPermission()) return BluetoothMetadataSnapshot()
        return runCatching {
            val adapter =
                context.getSystemService(BluetoothManager::class.java)?.adapter
                    ?: return@runCatching BluetoothMetadataSnapshot()
            val bondedDevices = adapter.bondedDevices.orEmpty()
            if (bondedDevices.isEmpty()) return@runCatching BluetoothMetadataSnapshot()

            val deviceAddress =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    audioDevice
                        ?.address
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() && it != ZERO_MAC_ADDRESS }
                } else {
                    null
                }

            val matchedDevice: BluetoothDevice? =
                (
                    if (deviceAddress != null) {
                        bondedDevices.firstOrNull { it.address.equals(deviceAddress, ignoreCase = true) }
                    } else {
                        null
                    }
                ) ?: if (!rawProductName.isNullOrBlank()) {
                    bondedDevices.firstOrNull { bonded ->
                        val alias = readBluetoothAliasOrName(bonded)
                        alias.equals(rawProductName, ignoreCase = true) ||
                            bonded.name?.equals(rawProductName, ignoreCase = true) == true
                    }
                } else {
                    null
                }

            if (matchedDevice == null) {
                BluetoothMetadataSnapshot()
            } else {
                BluetoothMetadataSnapshot(
                    displayName = readBluetoothAliasOrName(matchedDevice),
                    deviceClass = matchedDevice.bluetoothClass?.deviceClass,
                )
            }
        }.getOrDefault(BluetoothMetadataSnapshot())
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothAliasOrName(device: BluetoothDevice): String? {
        val preferred =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                device.alias
            } else {
                device.name
            }
        return preferred?.trim()?.takeIf { it.isNotEmpty() }
            ?: device.name?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun resolveActiveDeviceApi33(): AudioDeviceInfo? =
        audioManager
            .getAudioDevicesForAttributes(mediaQueryAttributes)
            .firstOrNull { it.isSink }

    private fun resolveActiveDeviceHeuristic(): AudioDeviceInfo? {
        val sinks =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .filter { it.isSink }

        return sinks.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_HEARING_AID
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_HDMI ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                    it.type == AudioDeviceInfo.TYPE_HDMI_EARC
            }
            ?: sinks.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE ||
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
    }

    companion object {
        private const val ZERO_MAC_ADDRESS = "00:00:00:00:00:00"
    }
}
