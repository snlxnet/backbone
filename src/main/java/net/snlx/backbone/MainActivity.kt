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
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import java.net.Socket
import java.net.ServerSocket
import java.net.DatagramSocket
import java.net.DatagramPacket
import java.net.InetAddress
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Vector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.sequences.takeWhile

val DEFAULT_URL = "http://192.168.50.174:8899"
val BROADCAST_PORT = 2903

class MainActivity : Activity() {
    private lateinit var webview: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
        listenToBroadcast(BROADCAST_PORT, {message ->
            runOnUiThread({
                Toast.makeText(this, "sock:"+message, Toast.LENGTH_SHORT).show()
            })
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

    fun listenToBroadcast(port: Int, handler: (message: String) -> Unit) {
        val socket = DatagramSocket(port)
        Thread {
            val buf = ByteArray(2048)
            while (true) {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                handler(message)
            }
        }.start()
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
            } else if (path == "/broadcast") {
                output.write("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\n\r\n")

                val socket = DatagramSocket()
                socket.broadcast = true
                val data = "backbone: hi".toByteArray()
                socket.send(DatagramPacket(data, data.size, broadcastAddress(this), BROADCAST_PORT))
                socket.close()
            } else {
                output.write("HTTP/1.1 404 Not Found\r\nAccess-Control-Allow-Origin: *\r\n\r\n")
                output.write("Command not found" + path)
            }
        })
    }
}

// LLM slop that I don't understand. Yet. I'll remove the comment when I do :)
fun broadcastAddress(context: Context): InetAddress {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val lp = cm.getLinkProperties(cm.activeNetwork) ?: error("No network")

    val la = lp.linkAddresses.first { it.address is java.net.Inet4Address }
    val ip = ByteBuffer.wrap(la.address.address).order(ByteOrder.BIG_ENDIAN).int
    val mask = if (la.prefixLength == 0) 0 else -1 shl (32 - la.prefixLength)

    val broadcast = (ip and mask) or mask.inv()
    return InetAddress.getByAddress(ByteBuffer.allocate(4).putInt(broadcast).array())
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
