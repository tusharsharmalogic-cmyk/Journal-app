package com.tushar.journal

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    companion object {
        private const val TERMUX_PERM = "com.termux.permission.RUN_COMMAND"
        private const val FILE_REQ = 1001
        private const val PERM_REQ = 1002
        // Dono files APK ke assets me bundled hain -- Termux me kuch rakhna nahi padta.
        private const val START_ASSET = "start_journal.sh"
        private const val SERVER_ASSET = "j.py"
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }

    inner class Bridge {
        @JavascriptInterface
        fun ensureServer() {
            startTermuxServer()
        }

        @JavascriptInterface
        fun toast(msg: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RUN_COMMAND runtime permission
        if (ContextCompat.checkSelfPermission(this, TERMUX_PERM) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(TERMUX_PERM), PERM_REQ)
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        @Suppress("DEPRECATION")
        webView.settings.allowUniversalAccessFromFileURLs = true

        webView.addJavascriptInterface(Bridge(), "Android")
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(params!!.createIntent(), FILE_REQ)
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        webView.loadUrl("file:///android_asset/journal.html")
    }

    private fun readAsset(name: String): ByteArray? = try {
        assets.open(name).use { it.readBytes() }
    } catch (_: Exception) { null }

    private fun startTermuxServer() {
        val script = readAsset(START_ASSET)?.toString(Charsets.UTF_8)
        val server = readAsset(SERVER_ASSET)
        if (script == null || server == null) {
            runOnUiThread {
                Toast.makeText(this, "App ke assets me server files nahi mili", Toast.LENGTH_LONG).show()
            }
            return
        }
        val serverB64 = Base64.encodeToString(server, Base64.NO_WRAP)
        try {
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
                // bash -c "<script>" start_journal <j.py base64>
                // -> script j.py khud bana deti hai (delete kar do to bhi), phir server chalati hai
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script, "start_journal", serverB64))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            }
            if (startService(intent) == null) {
                runOnUiThread {
                    Toast.makeText(this, "Termux nahi mila — pehle Termux install karo", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: SecurityException) {
            runOnUiThread {
                Toast.makeText(
                    this,
                    "Permission nahi mili: Termux me allow-external-apps=true karo (termux.properties), Termux restart karo, aur app ko 'Run commands in Termux' permission do",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Termux start fail: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == FILE_REQ) {
            val result = if (resultCode == Activity.RESULT_OK)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data) else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        } else {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onPause() {
        super.onPause()
        // App background mein jaye to pending drafts device par flush karo
        webView.evaluateJavascript("try{saveDraft();flushDrafts();}catch(e){}", null)
    }
}