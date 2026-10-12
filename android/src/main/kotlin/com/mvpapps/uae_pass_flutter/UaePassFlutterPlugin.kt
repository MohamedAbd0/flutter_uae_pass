package com.mvpapps.uae_pass_flutter

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebStorage
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.concurrent.Executors

/** UaePassPlugin */
class UaePassFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.NewIntentListener {

    private lateinit var channel: MethodChannel
    private var activityBinding: ActivityPluginBinding? = null
    private var config: UaePassConfig? = null
    private var loginDialog: UaePassWebViewDialog? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "uae_pass")
        channel.setMethodCallHandler(this)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        executor.shutdown()
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
        binding.addOnNewIntentListener(this)
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onDetachedFromActivity() {
        activityBinding?.removeOnNewIntentListener(this)
        activityBinding = null
        loginDialog?.abort("The app was closed before UAE PASS finished.")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "set_up_environment" -> {
                clearWebData()
                config = UaePassConfig(
                    clientId = call.argument<String>("client_id").orEmpty(),
                    clientSecret = call.argument<String>("client_secret").orEmpty(),
                    environment = if (call.argument<String>("environment") == "production")
                        UaePassEnvironment.PRODUCTION else UaePassEnvironment.STAGING,
                    scheme = call.argument<String>("scheme").orEmpty(),
                    redirectUrl = call.argument<String>("redirect_url")
                        ?: "https://oauthtest.com/authorization/return",
                    state = call.argument<String>("state") ?: java.util.UUID.randomUUID().toString(),
                    scope = call.argument<String>("scope") ?: "urn:uae:digitalid:profile",
                    language = if (call.argument<String>("language") == "ar") "ar" else "en",
                )
                result.success(null)
            }

            "sign_out" -> {
                clearWebData()
                result.success(true)
            }

            "sign_in" -> signIn(result)

            "access_token" -> {
                val code = call.argument<String>("code")
                if (code.isNullOrEmpty()) {
                    result.error("ERROR", "Authorization code is missing.", null)
                } else {
                    runInBackground(result) { it.fetchAccessToken(code) }
                }
            }

            "profile" -> {
                val token = call.argument<String>("token")
                if (token.isNullOrEmpty()) {
                    result.error("ERROR", "Access token is missing.", null)
                } else {
                    runInBackground(result) { it.fetchProfile(token) }
                }
            }

            else -> result.notImplemented()
        }
    }

    private fun signIn(result: Result) {
        val config = config ?: return result.error("ERROR", NOT_CONFIGURED, null)
        val activity = activityBinding?.activity
            ?: return result.error("ERROR", "UAE PASS needs a foreground activity.", null)
        if (loginDialog != null) {
            return result.error("ERROR", "A UAE PASS sign in is already in progress.", null)
        }
        val useApp = isUaePassAppInstalled(activity, config.environment)
        loginDialog = UaePassWebViewDialog(activity, config, config.loginUrl(useApp)) { code, error ->
            loginDialog = null
            if (code != null) result.success(code) else result.error("ERROR", error, null)
        }.also { it.show() }
    }

    private fun runInBackground(result: Result, block: (UaePassConfig) -> String) {
        val config = config ?: return result.error("ERROR", NOT_CONFIGURED, null)
        executor.execute {
            val outcome = runCatching { block(config) }
            mainHandler.post {
                outcome.fold(
                    onSuccess = { result.success(it) },
                    onFailure = { result.error("ERROR", it.message, null) },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent): Boolean {
        val data = intent.data ?: return false
        val config = config ?: return false
        val dialog = loginDialog ?: return false
        if (!data.scheme.equals(config.scheme, ignoreCase = true)) return false
        when (data.host) {
            UaePassWebViewDialog.SUCCESS_HOST -> dialog.onReturnFromUaePassApp(success = true)
            UaePassWebViewDialog.FAILURE_HOST -> dialog.onReturnFromUaePassApp(success = false)
            else -> return false
        }
        return true
    }

    private fun clearWebData() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private fun isUaePassAppInstalled(activity: Activity, environment: UaePassEnvironment): Boolean =
        try {
            activity.packageManager.getPackageInfo(environment.appPackage, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }

    private companion object {
        const val NOT_CONFIGURED = "UAE PASS is not configured. Call setUpEnvironment first."
    }
}
