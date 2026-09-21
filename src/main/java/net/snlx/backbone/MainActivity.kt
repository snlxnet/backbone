package net.snlx.backbone

import android.app.Activity
import android.os.Bundle
import android.os.ParcelUuid
import android.os.Build
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattServer
import android.Manifest
import android.util.Log
import java.net.Socket
import java.net.ServerSocket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Vector
import java.util.UUID
import kotlin.sequences.takeWhile
import net.snlx.backbone.BleClient
import net.snlx.backbone.BleServer
import androidx.core.app.ActivityCompat
import androidx.activity.result.contract.ActivityResultContracts

const val DEFAULT_URL = "http://192.168.50.174:8899"
val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val UART_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
val UART_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class MainActivity : Activity() {
    lateinit var webview: WebView
    var bleClient: BleClient? = null
    var bleServer: BleServer? = null
    private var onPermsGranted: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onPermsGranted = {
            webview = WebView(this)
            setContentView(webview)
            webview.settings.userAgentString = "backbone"
            webview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webview.settings.javaScriptEnabled = true
            webview.addJavascriptInterface(System(this), "backbone")
            webview.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }
            }

            val pref = this.getPreferences(Context.MODE_PRIVATE)
            val url = pref.getString("url", DEFAULT_URL).toString()
            webview.loadUrl(url)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                    1001
                )
                return
            }
        }

        onPermsGranted?.invoke()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            onPermsGranted?.invoke()
            onPermsGranted = null
        }
    }

    fun serve(port: Int, handler: (input: BufferedReader, output: PrintWriter) -> Unit) {
        Thread(Runnable {
            val socket = ServerSocket(port)
            while (true) {
                val client = socket.accept()
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.getInputStream()))
                handler(input, output)
                output.close()
            }
        }).start()
    }

    fun startApi() {
        serve(2077, {input, output ->
            val path = input.readLine().split(" ")[1].trim()

            val headers = generateSequence { input.readLine() }
                .takeWhile { it.isNotEmpty() }
                .toList()
            val contentLength = headers
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toIntOrNull() ?: 0

            val bodyBuf = CharArray(contentLength)
            input.read(bodyBuf)
            val body = String(bodyBuf)

            if (!body.isEmpty() && path == "/sh") {
                intent = Intent()
                intent.setClassName("com.termux", "com.termux.app.RunCommandService");
                intent.setAction("com.termux.RUN_COMMAND");
                intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
                intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", body));
                intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
                try {
                    startService(intent)
                    output.write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                    output.write("Started in the background")
                } catch (_: SecurityException) {
                    output.write("HTTP/1.1 403 Forbidden\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                    output.write("Forbidden by the OS")
                }

                output.flush()
            } else if (path == "/reload") {
                output.write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                output.write("Reloading")
                runOnUiThread {
                    webview.reload()
                }
            } else {
                output.write("HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                output.write("Command not found" + path)
            }
        })
    }
}

class System(private val app: MainActivity) {
    @JavascriptInterface
    fun replaceApp(url: String) {
        val pref = app.getPreferences(Context.MODE_PRIVATE) ?: return

        with (pref.edit()) {
            putString("url", url)
            apply()
        }
    }

    @JavascriptInterface
    fun bleCentral(deviceNames: Array<String>) {
        app.bleServer?.stop()
        app.bleClient?.stop()
        app.bleClient = BleClient(app, deviceNames.toSet(), {dev, msg -> onBleMessage(msg, dev)})
        app.bleClient?.start()
    }

    @JavascriptInterface
    fun blePeripheral(deviceName: String) {
        app.bleClient?.stop()
        app.bleServer?.stop()
        app.bleServer = BleServer(app, deviceName, {msg -> onBleMessage(msg, "central")})
        app.bleServer?.start()
    }

    @JavascriptInterface
    fun send(message: String, deviceName: String?) {
        app.bleServer?.send(message)

        if (deviceName != null) {
            app.bleClient?.send(message, deviceName)
        }
    }

    fun onBleMessage(message: String, device: String) {
        app.runOnUiThread {
            val deviceJson = org.json.JSONObject.quote(device)
            val messageJson = org.json.JSONObject.quote(message)

            app.webview.evaluateJavascript(
                "backbone.onBleMessage?.($deviceJson, $messageJson)",
                null
            )
        }
    }
}

class BleClient(
    private val context: Context,
    private val deviceNames: Set<String>,
    private val onMessage: (deviceName: String, message: String) -> Unit,
) {
    private data class DeviceSession(
        var gatt: BluetoothGatt? = null,
        var rxChar: BluetoothGattCharacteristic? = null,
        var txChar: BluetoothGattCharacteristic? = null,
    )

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager.adapter
    private val scanner = adapter.bluetoothLeScanner
    private val sessions = mutableMapOf<String, DeviceSession>()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (name !in deviceNames) return
            if (sessions.containsKey(device.name)) return

            sessions[device.name] = DeviceSession()
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val name = gatt.device.name
            val session = sessions[name] ?: return

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                session.gatt = gatt
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                session.gatt?.close()
                sessions.remove(name)
                onMessage(name, "disconnected")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val name = gatt.device.name
            val session = sessions[name] ?: return

            val service = gatt.getService(UART_SERVICE_UUID) ?: return
            val rx = service.getCharacteristic(UART_RX_UUID) ?: return
            val tx = service.getCharacteristic(UART_TX_UUID) ?: return

            session.rxChar = rx
            session.txChar = tx

            enableNotifications(gatt, tx)
            Log.v("BACKBONE", "ble configured: " + name)
            onMessage(name, "connected")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != UART_TX_UUID) return

            val name = gatt.device.name ?: gatt.device.address
            val message = characteristic.value?.toString(Charsets.UTF_8).orEmpty()
            onMessage(name, message)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(txChar, true)
        val cccd = txChar.getDescriptor(CCCD_UUID) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccd)
    }

    fun start() {
        Log.v("BACKBONE", "Requesting permissions")
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        Log.v("BACKBONE", "Starting central")
        adapter.name = "backbone"
        val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
        scanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        scanner.stopScan(scanCallback)
    }

    fun stop() {
        stopScan()
        sessions.forEach({session ->
            session.value.gatt?.close()
        })
    }

    fun send(message: String, deviceName: String) {
        val session = sessions[deviceName] ?: return
        Log.v("BACKBONE", "have session")
        val gatt = session.gatt ?: return
        Log.v("BACKBONE", "have gatt")
        val rx = session.rxChar ?: return
        Log.v("BACKBONE", "have rx")

        rx.value = message.toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(rx)
        Log.v("BACKBONE", "sent: " + message)
    }
}

// GPT-5.4 mini generated after much less nudging than the last time
class BleServer(
    private val context: Context,
    private val deviceName: String,
    private val onMessage: (message: String) -> Unit,
) {
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val adapter = bluetoothManager.adapter
    private val advertiser = adapter.bluetoothLeAdvertiser

    private var bluetoothLeService: BluetoothGattServer? = null
    private var currentClient: BluetoothDevice? = null
    private var txChar: BluetoothGattCharacteristic? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.v("BACKBONE", "state changed")
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                currentClient = device
                onMessage("client connected: ${device.name ?: device.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (currentClient?.address == device.address) currentClient = null
                onMessage("client disconnected")
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (characteristic.uuid == UART_RX_UUID) {
                onMessage(value.toString(Charsets.UTF_8))
            }

            if (responseNeeded) {
                bluetoothLeService?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            Log.v("BACKBONE", "descriptor write: ${descriptor.uuid}")

            if (descriptor.uuid == CCCD_UUID) {
                onMessage("notifications ${if (value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) "enabled" else "disabled"}")
            }

            if (responseNeeded) {
                bluetoothLeService?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }
    }

    private val service = BluetoothGattService(
        UART_SERVICE_UUID,
        BluetoothGattService.SERVICE_TYPE_PRIMARY
    ).apply {
        addCharacteristic(
            BluetoothGattCharacteristic(
                UART_RX_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
        )

        txChar = BluetoothGattCharacteristic(
            UART_TX_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        ).apply {
            addDescriptor(
                BluetoothGattDescriptor(
                    CCCD_UUID,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            )
        }

        addCharacteristic(txChar)
    }

    fun start() {
        Log.v("BACKBONE", "logging works")
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
            != PackageManager.PERMISSION_GRANTED
        ) return
        Log.v("BACKBONE", "permission acquired")

        adapter.name = deviceName

        bluetoothLeService = bluetoothManager.openGattServer(context, gattServerCallback)
        bluetoothLeService?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()
        Log.v("BACKBONE", "settings built")

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UART_SERVICE_UUID))
            .build()

        Log.v("BACKBONE", "advertising started")
        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    fun stop() {
        advertiser.stopAdvertising(advertiseCallback)
        bluetoothLeService?.close()
        bluetoothLeService = null
    }

    fun send(message: String) {
        Log.v("BACKBONE", "wanna send")
        val client = currentClient ?: return
        Log.v("BACKBONE", "have client")
        val char = txChar ?: return
        Log.v("BACKBONE", "have char")
        val gatt = bluetoothLeService ?: return
        Log.v("BACKBONE", "have gatt")

        char.value = message.toByteArray(Charsets.UTF_8)
        gatt.notifyCharacteristicChanged(client, char, false)
        Log.v("BACKBONE", "sent: " + message)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}
}

