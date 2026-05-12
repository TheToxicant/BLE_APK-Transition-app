package com.example.btvolumeknob

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.UUID

class VolumeService : Service() {

    companion object {
        var controller: VolumeController? = null
    }

    private val channelId = "volume_service_channel"
    private val handler = Handler(Looper.getMainLooper())

    private var bluetoothGatt: BluetoothGatt? = null

    @Volatile
    private var isConnected = false

    private val serviceUuid =
        UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

    private val txUuid =
        UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    private val cccdUuid =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val deviceAddress = "F8:51:C9:40:E5:C5"

    override fun onCreate() {
        super.onCreate()

        android.util.Log.d("BLE", "VolumeService onCreate")

        createNotificationChannel()

        if (controller == null) {
            controller = VolumeController(applicationContext)
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        android.util.Log.d("BLE", "SERVICE STARTED")

        try {
            startForeground(1, buildNotification())

            android.util.Log.d("BLE", "Foreground started")

            if (!isConnected && bluetoothGatt == null) {
                android.util.Log.d("BLE", "Calling startBleConnect()")
                startBleConnect()
            } else {
                android.util.Log.d("BLE", "Already connected or connecting")
            }

        } catch (e: Exception) {
            android.util.Log.e("BLE", "SERVICE ERROR", e)
        }

        return START_STICKY
    }

    private fun startBleConnect() {
        android.util.Log.d("BLE", "Direct connect mode")

        if (!hasConnectPermission()) {
            android.util.Log.d("BLE", "NO BLUETOOTH_CONNECT PERMISSION")
            return
        }

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        bluetoothGatt?.close()
        bluetoothGatt = null

        val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)

        android.util.Log.d("BLE", "connectGatt -> ${device.address}")

        bluetoothGatt = device.connectGatt(
            this,
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            android.util.Log.d(
                "BLE",
                "CONNECTION STATE status=$status newState=$newState"
            )

            if (
                newState == BluetoothProfile.STATE_CONNECTED &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                android.util.Log.d("BLE", "CONNECTED")
                isConnected = true

                handler.postDelayed({
                    if (hasConnectPermission()) {
                        android.util.Log.d("BLE", "discoverServices()")
                        gatt.discoverServices()
                    }
                }, 600)
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                android.util.Log.d("BLE", "DISCONNECTED status=$status")

                isConnected = false
                bluetoothGatt?.close()
                bluetoothGatt = null
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            android.util.Log.d("BLE", "SERVICES DISCOVERED status=$status")

            for (service in gatt.services) {
                android.util.Log.d("BLE", "SERVICE FOUND: ${service.uuid}")

                for (characteristic in service.characteristics) {
                    android.util.Log.d(
                        "BLE",
                        "  CHARACTERISTIC: ${characteristic.uuid}"
                    )
                }
            }

            val service = gatt.getService(serviceUuid)

            if (service == null) {
                android.util.Log.d("BLE", "UART SERVICE NOT FOUND")
                return
            }

            android.util.Log.d("BLE", "UART SERVICE FOUND")

            val characteristic =
                service.getCharacteristic(txUuid)

            if (characteristic == null) {
                android.util.Log.d("BLE", "UART TX CHARACTERISTIC NOT FOUND")
                return
            }

            android.util.Log.d("BLE", "UART TX CHARACTERISTIC FOUND")

            enableNotifications(gatt, characteristic)
        }

        @Deprecated("Deprecated in newer Android, kept for compatibility")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleIncomingData(characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingData(value)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            android.util.Log.d(
                "BLE",
                "Descriptor write status=$status uuid=${descriptor.uuid}"
            )
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (!hasConnectPermission()) {
            android.util.Log.d("BLE", "NO PERMISSION FOR NOTIFY")
            return
        }

        val notifySet =
            gatt.setCharacteristicNotification(characteristic, true)

        android.util.Log.d("BLE", "setCharacteristicNotification ok=$notifySet")

        val descriptor =
            characteristic.getDescriptor(cccdUuid)

        if (descriptor == null) {
            android.util.Log.d("BLE", "NO CCCD DESCRIPTOR")
            return
        }

        val started =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                ) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value =
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }

        android.util.Log.d(
            "BLE",
            "Notifications enable write started=$started"
        )
    }

    private fun handleIncomingData(data: ByteArray?) {
        if (data == null) return

        val value = data.decodeToString().trim()

        android.util.Log.d("BLE_DATA", "TEXT: $value")

        when (value) {

            "+" -> {
                VolumeAccessibilityService.instance?.volumeUp()
            }

            "-" -> {
                VolumeAccessibilityService.instance?.volumeDown()
            }
        }
    }

    private fun hasConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("BT Volume Knob")
            .setContentText("Listening for encoder BLE input")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Volume Service",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        android.util.Log.d("BLE", "VolumeService onDestroy")

        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}