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
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import recieptservice.com.recieptservice.PrinterInterface
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var printerInterface: PrinterInterface? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerInterface = PrinterInterface.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            printerInterface = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind to Senraise printer service
        try {
            val intent = Intent().apply {
                setClassName(
                    "recieptservice.com.recieptservice",
                    "recieptservice.com.recieptservice.service.PrinterService"
                )
            }
            startService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Setup WebView
        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
            }
            addJavascriptInterface(PrintBridge(), "NativePrinter")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Tell the PWA it's running inside the native app
                    evaluateJavascript(
                        "window.NATIVE_PRINT = true; console.log('SWO POS native bridge ready');",
                        null
                    )
                }
            }
            loadUrl("https://pos.socialwifionline.com")
        }

        setContentView(webView)
    }

    inner class PrintBridge {

        @JavascriptInterface
        fun isAvailable(): Boolean = printerInterface != null

        @JavascriptInterface
        fun print(code: String, planName: String, venueName: String, validity: String) {
            runOnUiThread { printVoucher(code, planName, venueName, validity) }
        }

        @JavascriptInterface
        fun printMultiple(codesJson: String, planName: String, venueName: String, validity: String) {
            // codesJson: JSON array string e.g. '["ABC123","DEF456"]'
            try {
                val cleaned = codesJson.trim().removePrefix("[").removeSuffix("]")
                val codes = cleaned.split(",").map { it.trim().removeSurrounding("\"") }
                runOnUiThread {
                    codes.forEach { code ->
                        printVoucher(code, planName, venueName, validity)
                        Thread.sleep(800) // Brief pause between tickets
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun printVoucher(code: String, planName: String, venueName: String, validity: String) {
        val p = printerInterface ?: return
        try {
            val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
            p.beginWork()

            // Header — venue name centered, bold
            p.setAlignment(1)
            p.setTextBold(true)
            p.setTextSize(20f)
            p.printText("$venueName\n")
            p.setTextBold(false)
            p.setTextSize(9f)
            p.printText("WiFi Access Voucher\n")
            p.printText("--------------------------------\n")

            // Plan info — left aligned
            p.setAlignment(0)
            p.setTextSize(13f)
            p.printText("$planName")
            if (validity.isNotEmpty()) p.printText("  ·  $validity")
            p.printText("\n")
            p.printText("--------------------------------\n")

            // Access code — center, double size
            p.setAlignment(1)
            p.setTextSize(9f)
            p.printText("YOUR ACCESS CODE\n")
            p.setTextBold(true)
            p.setTextDoubleWidth(true)
            p.setTextDoubleHeight(true)
            p.setTextSize(24f)
            p.printText("$code\n")
            p.setTextDoubleWidth(false)
            p.setTextDoubleHeight(false)
            p.setTextBold(false)

            // Footer
            p.setTextSize(8f)
            p.printText("Enter code at the WiFi login page\n")
            p.printText("--------------------------------\n")
            p.printText("Issued: $date\n")
            p.printText("Thank you  ·  SocialWiFiOnline\n")

            p.nextLine(3)
            p.endWork()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
