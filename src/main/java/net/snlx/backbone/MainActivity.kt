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
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
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
import android.view.WindowManager
import android.media.AudioManager
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
val UART_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
val UART_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class MainActivity : AppCompatActivity() {
    lateinit var webview: WebView
    var bleClient: BleClient? = null
    var bleServer: BleServer? = null
    private var onPermsGranted: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WebView.setWebContentsDebuggingEnabled(true)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onPermsGranted = {
            webview = WebView(this)
            setContentView(webview)
            webview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webview.settings.javaScriptEnabled = true
            webview.settings.domStorageEnabled = true
            webview.settings.mediaPlaybackRequiresUserGesture = false
            webview.settings.allowFileAccess = true
            webview.settings.allowContentAccess = true
            webview.settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webview.addJavascriptInterface(System(this), "backbone")
            webview.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return false
                }
            }
            webview.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

                    request.grant(request.resources)
                }
            }

            val pref = this.getPreferences(Context.MODE_PRIVATE)

            val fullscreen = pref.getBoolean("fullscreen", false)
            if (fullscreen) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            val shellCommand = pref.getString("shellCommand", "").orEmpty()
            sh(shellCommand)

            val delay = pref.getLong("delay", 0)
            Thread.sleep(delay)

            Thread {
                val fallback = try {
                    Socket("127.0.0.1", 2903).close()
                    "http://127.0.0.1:2903/"
                } catch (e: Exception) {
                    Log.v("BACKBONE", e.toString())
                    "https://backbone.snlx.net/app"
                }
                runOnUiThread {
                    val url = pref.getString("url", fallback).toString()
                    webview.loadUrl(url)
                }
            }.start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                ),
                1001
            )
        } else {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                ),
                1001
            )
        }
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

    fun sh(command: String) {
        if (command.isEmpty() || command.isBlank()) {
            return
        }

        intent = Intent()
        intent.setClassName("com.termux", "com.termux.app.RunCommandService");
        intent.setAction("com.termux.RUN_COMMAND");
        intent.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
        intent.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command));
        intent.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            startService(intent)
            Toast.makeText(this, "Termux started", 0).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Failed to start termux", 0).show()
        }
    }
}

class System(private val app: MainActivity) {
    @JavascriptInterface
    fun startup(url: String, fullscreen: Boolean, shellCommand: String, delay: Long) {
        val pref = app.getPreferences(Context.MODE_PRIVATE) ?: return

        with (pref.edit()) {
            putString("url", url)
            putBoolean("fullscreen", fullscreen)
            putString("shellCommand", shellCommand)
            putLong("delay", delay)
            apply()
        }
    }

    @JavascriptInterface
    fun central(deviceNames: Array<String>) {
        app.bleServer?.stop()
        app.bleClient?.stop()
        app.bleClient = BleClient(app, deviceNames.toSet(), {msg, dev -> onBleMessage(msg, dev)})
        app.bleClient?.start()
    }

    @JavascriptInterface
    fun peripheral(deviceName: String) {
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
                "backbone.onmessage?.($messageJson, $deviceJson)",
                null
            )
        }
    }
}

class BleClient(
    private val context: Context,
    private val deviceNames: Set<String>,
    private val onMessage: (message: String, deviceName: String) -> Unit,
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
                onMessage("disconnected", name)
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
            onMessage("connected", name)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != UART_TX_UUID) return

            val name = gatt.device.name ?: gatt.device.address
            val message = characteristic.value?.toString(Charsets.UTF_8).orEmpty()
            onMessage(message, name)
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, txChar: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(txChar, true)
        val cccd = txChar.getDescriptor(CCCD_UUID) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(cccd)
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
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
        val gatt = session.gatt ?: return
        val rx = session.rxChar ?: return

        rx.value = message.toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(rx)
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
            if (descriptor.uuid == CCCD_UUID) {
                onMessage("connected")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        } else {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        adapter.name = deviceName

        bluetoothLeService = bluetoothManager.openGattServer(context, gattServerCallback)
        bluetoothLeService?.addService(service)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(UART_SERVICE_UUID))
            .build()

        advertiser.startAdvertising(settings, data, advertiseCallback)
    }

    fun stop() {
        advertiser.stopAdvertising(advertiseCallback)
        bluetoothLeService?.close()
        bluetoothLeService = null
    }

    fun send(message: String) {
        val client = currentClient ?: return
        val char = txChar ?: return
        val gatt = bluetoothLeService ?: return

        char.value = message.toByteArray(Charsets.UTF_8)
        gatt.notifyCharacteristicChanged(client, char, false)
    }

    private val advertiseCallback = object : AdvertiseCallback() {}
}

