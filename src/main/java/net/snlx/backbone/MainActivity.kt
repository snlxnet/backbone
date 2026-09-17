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
import java.net.Socket
import java.net.ServerSocket
import java.io.PrintWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Vector
import kotlin.sequences.takeWhile

val DEFAULT_URL = "http://192.168.50.174:8899"

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webview = WebView(this)
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
            val path = input.readLine().split(" ")[1]

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

            output.write("HTTP/1.1 200 OK\r\n\r\n")
            output.write(body)
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
