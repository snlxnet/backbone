package net.snlx.backbone

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.content.Context

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val webview = WebView(this)
        setContentView(webview)
        webview.settings.userAgentString = "backbone"
        webview.settings.javaScriptEnabled = true
        webview.addJavascriptInterface(System(this), "system")
        webview.loadUrl("data:text/html;utf-8,<h1>Hi android</h1><script>system.showToast('hi')</script>")
    }
}

class System(private val mContext: Context) {
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show()
    }
}
