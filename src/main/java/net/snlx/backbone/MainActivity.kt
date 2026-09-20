package net.snlx.backbone

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebViewClient
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
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattDescriptor
import android.Manifest
import java.net.Socket
import java.net.ServerSocket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Vector
import java.util.UUID
import kotlin.sequences.takeWhile
import net.snlx.backbone.BleClient
import androidx.core.app.ActivityCompat

val DEFAULT_URL = "http://192.168.50.174:8899"

class MainActivity : Activity() {
    private lateinit var webview: WebView
    private lateinit var bleClient: BleClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleClient = BleClient(this, setOf("cp-test"), {msg ->
            runOnUiThread({
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            })
}, {_, _ -> })
bleClient.startScan()
        
        webview = WebView(this)
        setContentView(webview)
        webview.settings.userAgentString = "backbone"
        webview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webview.settings.javaScriptEnabled = true
        webview.addJavascriptInterface(System(this), "backbone")

        val pref = this.getPreferences(Context.MODE_PRIVATE) ?: return
        val url = pref.getString("url", DEFAULT_URL).toString()
        webview.loadUrl(url)

        startApi()
        serve(17500, {input, output ->
            val path = input.readLine().split(" ")[1]

            output.write("HTTP/1.1 200 OK\r\n\r\n")
            output.write("Path: " + path + "\n")
        })
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
            } else if (path == "/toggle") {
                output.write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                output.write("Okay")
                bleClient.sendToggle("cp-test")
            } else if (path == "/scan") {
                output.write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                bleClient.startScan()
                output.write("Okay")
            } else {
                output.write("HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                output.write("Command not found" + path)
            }
        })
    }
}

class System(private val app: MainActivity) {
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun replaceApp(url: String) {
        val pref = app.getPreferences(Context.MODE_PRIVATE) ?: return

        with (pref.edit()) {
            putString("url", url)
            apply()
        }
    }
}

class BleClient(
    private val context: Context,
    private val deviceNames: Set<String>,
    private val onStateChanged: (String) -> Unit,
    private val onMessage: (deviceName: String, message: String) -> Unit,
) {
    companion object {
        private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

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
            onStateChanged("Connecting to $name")
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val name = gatt.device.name
            val session = sessions[name] ?: return

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                session.gatt = gatt
                onStateChanged("Connected to ${gatt.device.name ?: name}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onStateChanged("Disconnected from ${gatt.device.name ?: name}")
                session.gatt?.close()
                sessions.remove(name)
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
            onStateChanged("Ready: ${name ?: gatt.device.address}")
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

    fun startScan() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            onStateChanged("Missing BLUETOOTH_SCAN")
            return
        }
        val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()
        scanner.startScan(null, settings, scanCallback)
        onStateChanged("Scanning")
    }

    fun stopScan() {
        scanner.stopScan(scanCallback)
    }

    fun sendToggle(deviceName: String) {
        val session = sessions[deviceName] ?: return
        val gatt = session.gatt ?: return
        val rx = session.rxChar ?: return

        rx.value = "TOGGLE".toByteArray(Charsets.UTF_8)
        gatt.writeCharacteristic(rx)
    }
}

