package com.example.btvolumeknob

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.*

@SuppressLint("MissingPermission")
object BleClientManager {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var onCommand: ((String) -> Unit)? = null

    // We intentionally do NOT hardcode UUIDs since you said they are not exposed
    private val handler = Handler(Looper.getMainLooper())

    fun start(context: Context, callback: (String) -> Unit) {
        onCommand = callback

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        startScan()
    }

    private fun startScan() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(true)
            .build()

        scanner.startScan(
            null,
            settings,
            scanCallback
        )
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "UNKNOWN"

            android.util.Log.d("BLE", "FOUND DEVICE: $name ${device.address}")

            bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
            connect(device)
        }
    }

    private fun connect(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(
            null,
            false,
            gattCallback
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {

            if (newState == BluetoothProfile.STATE_CONNECTED) {

                android.util.Log.d("BLE", "CONNECTED")

                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            android.util.Log.d("BLE", "SERVICES DISCOVERED status=$status")

            for (service in gatt.services) {
                android.util.Log.d("BLE", "SERVICE: ${service.uuid}")

                for (characteristic in service.characteristics) {
                    android.util.Log.d(
                        "BLE",
                        "CHAR: ${characteristic.uuid} props=${characteristic.properties}"
                    )

                    val props = characteristic.properties

                    if (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
                        android.util.Log.d(
                            "BLE",
                            "ENABLING NOTIFY ON: ${characteristic.uuid}"
                        )

                        enableNotifications(gatt, characteristic)
                    }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {

            val data = characteristic.value ?: return

            val hex = data.joinToString(" ") {
                String.format("%02X", it)
            }

            val text = data.decodeToString()

            android.util.Log.d("BLE_DATA", "HEX: $hex")
            android.util.Log.d("BLE_DATA", "TEXT: $text")
        }

        private fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val ok = gatt.setCharacteristicNotification(characteristic, true)

            android.util.Log.d(
                "BLE",
                "setCharacteristicNotification ${characteristic.uuid} ok=$ok"
            )

            val descriptor = characteristic.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )

            if (descriptor == null) {
                android.util.Log.d(
                    "BLE",
                    "NO CCCD DESCRIPTOR FOR ${characteristic.uuid}"
                )
                return
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

            val writeStarted = gatt.writeDescriptor(descriptor)

            android.util.Log.d(
                "BLE",
                "writeDescriptor ${characteristic.uuid} started=$writeStarted"
            )
        }
    }

    private fun decode(value: ByteArray): String {
        // Your ItsyBitsy is sending hex commands like +/- or encoded bytes
        return try {
            val str = String(value)

            when {
                str.contains("+") -> "+"
                str.contains("-") -> "-"
                else -> str.trim()
            }

        } catch (e: Exception) {
            ""
        }
    }

    fun stop() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}