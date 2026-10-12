package com.mvpapps.uae_pass_flutter

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

/**
 * Full screen dialog hosting the UAE PASS login page.
 *
 * The content is padded to the system bars, display cutout and keyboard so the page always stays
 * inside the safe area, both on edge-to-edge windows (Android 15+) and on older versions.
 *
 * [onResult] is called exactly once with either the authorization code or an error message.
 */
class UaePassWebViewDialog(
    private val activity: Activity,
    private val config: UaePassConfig,
    private val loginUrl: String,
    private val onResult: (code: String?, error: String?) -> Unit,
) : Dialog(activity, android.R.style.Theme_DeviceDefault_Light_NoActionBar) {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private var successUrl: String? = null
    private var finished = false
    private var backCallback: Any? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        setCancelable(true)
        setOnCancelListener { finish(null, CANCELED_MESSAGE) }

        val root = FrameLayout(context).apply { setBackgroundColor(Color.WHITE) }
        webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(false)
            webViewClient = Client()
            isFocusable = true
            isFocusableInTouchMode = true
        }
        progressBar = ProgressBar(context).apply { isIndeterminate = true }
        root.addView(webView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(progressBar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        setContentView(root)

        window?.apply {
            setLayout(MATCH, MATCH)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // White background behind the bars, so request dark status/navigation bar icons.
                val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                insetsController?.setSystemBarsAppearance(light, light)
            }
        }
        applySafeAreaPadding(root)

        webView.loadUrl(loginUrl)
        webView.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { handleBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
            backCallback = callback
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            backCallback = null
        }
        super.onStop()
    }

    @Deprecated("Used below Android 13; newer versions use OnBackInvokedCallback.")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBack()
    }

    override fun dismiss() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.dismiss()
    }

    /** Called when the UAE PASS app reopens this app through `scheme://success` or `scheme://failure`. */
    fun onReturnFromUaePassApp(success: Boolean) {
        val url = successUrl
        if (success && url != null) {
            webView.loadUrl(url)
        } else {
            finish(null, CANCELED_MESSAGE)
        }
    }

    /** Cancels the flow from outside, e.g. when the host activity goes away. */
    fun abort(error: String) {
        finish(null, error)
    }

    private fun handleBack() {
        if (webView.canGoBack()) webView.goBack() else finish(null, CANCELED_MESSAGE)
    }

    private fun finish(code: String?, error: String?) {
        if (finished) return
        finished = true
        if (isShowing) dismiss()
        onResult(code, error)
    }

    private fun applySafeAreaPadding(root: View) {
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val safe = insets.getInsets(
                    WindowInsets.Type.systemBars() or
                            WindowInsets.Type.displayCutout() or
                            WindowInsets.Type.ime()
                )
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            insets
        }
        root.requestApplyInsets()
    }

    /** Returns true when the URL was consumed and must not be loaded by the WebView. */
    private fun handleUrl(url: String): Boolean {
        if (url.contains("error=access_denied") || url.contains("error=cancelled")) {
            finish(null, CANCELED_MESSAGE)
            return true
        }
        if (url.startsWith(config.redirectUrl)) {
            val uri = Uri.parse(url)
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
            when {
                error != null -> finish(null, error)
                code.isNullOrEmpty() -> finish(null, "UAE PASS did not return an authorization code.")
                uri.getQueryParameter("state") != config.state -> finish(null, "State mismatch.")
                else -> finish(code, null)
            }
            return true
        }
        if (url.startsWith("uaepass://")) {
            openUaePassApp(Uri.parse(url))
            return true
        }
        return false
    }

    /** Hands the login over to the installed UAE PASS app (app-to-app flow). */
    private fun openUaePassApp(uri: Uri) {
        successUrl = uri.getQueryParameter("successurl")
        // Same format as the official UAE PASS Android SDK: original query replaced by unencoded
        // success/failure URLs. Do not add `closeondone=true` (iOS only): with it the Android UAE PASS
        // app closes itself without opening the success URL, leaving the login stuck.
        val appUrl = config.environment.appScheme + uri.encodedAuthority.orEmpty() + uri.encodedPath.orEmpty() +
                "?successurl=" + config.returnUrl(SUCCESS_HOST) +
                "&failureurl=" + config.returnUrl(FAILURE_HOST)
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(appUrl)))
        } catch (e: ActivityNotFoundException) {
            finish(null, "UAE PASS app is not installed.")
        }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            handleUrl(request.url.toString())

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
            progressBar.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String) {
            progressBar.visibility = View.GONE
        }
    }

    companion object {
        const val SUCCESS_HOST = "success"
        const val FAILURE_HOST = "failure"
        const val CANCELED_MESSAGE = "Authentication Process Canceled By User."
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
