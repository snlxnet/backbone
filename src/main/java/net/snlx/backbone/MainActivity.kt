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

        Thread(Runnable {
            val socket = ServerSocket(3000)
            while (true) {
                val client = socket.accept()
                val output = PrintWriter(client.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(client.getInputStream()))

                val req = generateSequence { input.readLine() }
                    .takeWhile { it.isNotEmpty() }
                    .joinToString(separator = "\n")

                output.write("HTTP/1.1 200 OK\r\n\r\n")
                output.write(req)
                output.flush()
                output.close()
            }
        }).start()
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
