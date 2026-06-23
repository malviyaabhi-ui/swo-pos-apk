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
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var printerBinder: IBinder? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            printerBinder = service
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            printerBinder = null
        }
    }

    private fun callPrinter(method: String, vararg args: Any?) {
        val binder = printerBinder ?: return
        try {
            val clazz = Class.forName("recieptservice.com.recieptservice.PrinterInterface\$Stub")
            val asInterface = clazz.getMethod("asInterface", IBinder::class.java)
            val printer = asInterface.invoke(null, binder)
            val printerClass = printer.javaClass

            val paramTypes = args.map { arg ->
                when (arg) {
                    is Boolean -> Boolean::class.javaPrimitiveType!!
                    is Int -> Int::class.javaPrimitiveType!!
                    is Float -> Float::class.javaPrimitiveType!!
                    is String -> String::class.java
                    else -> arg?.javaClass ?: String::class.java
                }
            }.toTypedArray()

            val m = printerClass.getMethod(method, *paramTypes)
            m.invoke(printer, *args)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
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
        fun isAvailable(): Boolean = printerBinder != null

        @JavascriptInterface
        fun print(code: String, planName: String, venueName: String, validity: String) {
            runOnUiThread { printVoucher(code, planName, venueName, validity) }
        }

        @JavascriptInterface
        fun printMultiple(codesJson: String, planName: String, venueName: String, validity: String) {
            try {
                val codes = codesJson.trim()
                    .removePrefix("[").removeSuffix("]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotEmpty() }
                runOnUiThread {
                    codes.forEachIndexed { i, code ->
                        if (i > 0) Thread.sleep(800)
                        printVoucher(code, planName, venueName, validity)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun printVoucher(code: String, planName: String, venueName: String, validity: String) {
        val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        callPrinter("beginWork")
        callPrinter("setAlignment", 1)
        callPrinter("setTextBold", true)
        callPrinter("setTextSize", 20f)
        callPrinter("printText", "$venueName\n")
        callPrinter("setTextBold", false)
        callPrinter("setTextSize", 9f)
        callPrinter("printText", "WiFi Access Voucher\n")
        callPrinter("printText", "--------------------------------\n")
        callPrinter("setAlignment", 0)
        callPrinter("setTextSize", 13f)
        callPrinter("printText", "$planName  ·  $validity\n")
        callPrinter("printText", "--------------------------------\n")
        callPrinter("setAlignment", 1)
        callPrinter("setTextSize", 9f)
        callPrinter("printText", "YOUR ACCESS CODE\n")
        callPrinter("setTextBold", true)
        callPrinter("setTextDoubleWidth", true)
        callPrinter("setTextDoubleHeight", true)
        callPrinter("setTextSize", 24f)
        callPrinter("printText", "$code\n")
        callPrinter("setTextDoubleWidth", false)
        callPrinter("setTextDoubleHeight", false)
        callPrinter("setTextBold", false)
        callPrinter("setTextSize", 8f)
        callPrinter("printText", "Enter code at WiFi login page\n")
        callPrinter("printText", "--------------------------------\n")
        callPrinter("printText", "Issued: $date\n")
        callPrinter("printText", "Thank you  ·  SocialWiFiOnline\n")
        callPrinter("nextLine", 3)
        callPrinter("endWork")
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
