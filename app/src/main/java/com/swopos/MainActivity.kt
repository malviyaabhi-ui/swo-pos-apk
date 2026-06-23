package com.swopos

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import recieptservice.com.recieptservice.PrinterInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var printer: PrinterInterface? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printer = PrinterInterface.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            printer = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val intent = Intent().apply {
                setClassName("recieptservice.com.recieptservice",
                    "recieptservice.com.recieptservice.service.PrinterService")
            }
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) { e.printStackTrace() }

        webView = WebView(this).apply {
            clearCache(true)
            clearHistory()
            WebStorage.getInstance().deleteAllData()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                cacheMode = WebSettings.LOAD_NO_CACHE
                userAgentString = "$userAgentString SWOPOSNative/1.0"
            }
            addJavascriptInterface(PrintBridge(), "NativePrinter")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    evaluateJavascript("window.NATIVE_PRINT = true;", null)
                }
            }
            loadUrl("https://pos.socialwifionline.com")
        }
        setContentView(webView)
    }

    inner class PrintBridge {
        @JavascriptInterface
        fun isAvailable(): Boolean = printer != null

        @JavascriptInterface
        fun print(code: String, planName: String, venueName: String, validity: String) {
            runOnUiThread { printVoucher(code, planName, venueName, validity) }
        }

        @JavascriptInterface
        fun printMultiple(codesJson: String, planName: String, venueName: String, validity: String) {
            try {
                val codes = codesJson.trim().removePrefix("[").removeSuffix("]")
                    .split(",").map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
                runOnUiThread {
                    codes.forEachIndexed { i, code ->
                        if (i > 0) Thread.sleep(1000)
                        printVoucher(code, planName, venueName, validity)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        @JavascriptInterface
        fun printCredentials(username: String, password: String, planName: String, venueName: String, validity: String) {
            runOnUiThread { printCreds(username, password, planName, venueName, validity) }
        }
    }

    private fun printVoucher(code: String, planName: String, venueName: String, validity: String) {
        val p = printer ?: return
        try {
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            p.beginWork()
            p.setDark(5)
            p.setAlignment(1)
            p.setTextBold(true)
            p.setTextSize(28f)
            p.printText("$venueName\n")
            p.setTextBold(false)
            p.setTextSize(18f)
            p.printText("WiFi Access Voucher\n")
            p.printText("--------------------------------\n")
            p.setAlignment(0)
            p.setTextSize(22f)
            p.printText("$planName  |  $validity\n")
            p.printText("--------------------------------\n")
            p.setAlignment(1)
            p.setTextBold(true)
            p.setTextSize(20f)
            p.printText("YOUR ACCESS CODE\n")
            p.setTextDoubleWidth(true)
            p.setTextDoubleHeight(true)
            p.setTextSize(28f)
            p.printText("$code\n")
            p.setTextDoubleWidth(false)
            p.setTextDoubleHeight(false)
            p.setTextBold(false)
            p.printText("--------------------------------\n")
            p.setTextSize(20f)
            p.printText("$date\n")
            p.printText("Thank you - SocialWiFiOnline\n")
            p.nextLine(3)
            p.endWork()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun printCreds(username: String, password: String, planName: String, venueName: String, validity: String) {
        val p = printer ?: return
        try {
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            p.beginWork()
            p.setDark(5)
            p.setAlignment(1)
            p.setTextBold(true)
            p.setTextSize(28f)
            p.printText("$venueName\n")
            p.setTextBold(false)
            p.setTextSize(18f)
            p.printText("WiFi Login Credentials\n")
            p.printText("--------------------------------\n")
            p.setAlignment(0)
            p.setTextSize(22f)
            p.printText("$planName  |  $validity\n")
            p.printText("--------------------------------\n")
            p.setAlignment(0)
            p.setTextSize(20f)
            p.printText("Username:\n")
            p.setTextBold(true)
            p.setTextDoubleWidth(true)
            p.setTextSize(24f)
            p.printText("$username\n")
            p.setTextDoubleWidth(false)
            p.setTextBold(false)
            p.setTextSize(20f)
            p.printText("Password:\n")
            p.setTextBold(true)
            p.setTextDoubleWidth(true)
            p.setTextSize(24f)
            p.printText("$password\n")
            p.setTextDoubleWidth(false)
            p.setTextBold(false)
            p.printText("--------------------------------\n")
            p.setTextSize(20f)
            p.printText("$date\n")
            p.printText("Thank you - SocialWiFiOnline\n")
            p.nextLine(3)
            p.endWork()
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unbindService(serviceConnection) } catch (_: Exception) {}
    }
}
