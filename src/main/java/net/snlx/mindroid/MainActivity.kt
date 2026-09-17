package net.snlx.mindroid

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webview = WebView(this)
        setContentView(webview)
        webview.settings.userAgentString = "mindroid"
        webview.settings.javaScriptEnabled = true
        webview.loadUrl("https://duckduckgo.com/?q=what%27s+my+user+agent&ia=answer")
    }
}
