package com.mvpapps.uae_pass_flutter

import ae.sdg.libraryuaepass.*
import ae.sdg.libraryuaepass.UAEPassController.getAccessToken
import ae.sdg.libraryuaepass.UAEPassController.getAccessCode
import ae.sdg.libraryuaepass.UAEPassController.resume
import ae.sdg.libraryuaepass.UAEPassController.getUserProfile
import ae.sdg.libraryuaepass.business.profile.model.ProfileModel
import ae.sdg.libraryuaepass.business.authentication.model.UAEPassAccessTokenRequestModel
import ae.sdg.libraryuaepass.business.profile.model.UAEPassProfileRequestModel

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.CookieManager
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import ae.sdg.libraryuaepass.business.Environment
import android.content.pm.PackageManager
import ae.sdg.libraryuaepass.business.Language
import ae.sdg.libraryuaepass.utils.Utils.generateRandomString
import com.google.gson.Gson


// create a class that implements the PluginRegistry.NewIntentListener interface


/** UaePassPlugin */
class UaePassFlutterPlugin : FlutterPlugin, MethodCallHandler, ActivityAware,
    PluginRegistry.NewIntentListener, PluginRegistry.ActivityResultListener {

    private lateinit var channel: MethodChannel
    private lateinit var requestModel: UAEPassAccessTokenRequestModel

    private var client_id: String? = null
    private var client_secret: String? = null
    private var redirect_url: String? = "https://oauthtest.com/authorization/return"
    private var environment: Environment = Environment.STAGING
    private var state: String? = null
    private var scheme: String? = null
    private var failureHost: String? = null
    private var successHost: String? = null
    private var scope: String? = "urn:uae:digitalid:profile"
    private var language: String? = "en"

    private val UAE_PASS_PACKAGE_ID = "ae.uaepass.mainapp"
    private val UAE_PASS_QA_PACKAGE_ID = "ae.uaepass.mainapp.qa"
    private val UAE_PASS_STG_PACKAGE_ID = "ae.uaepass.mainapp.stg"

    private val DOCUMENT_SIGNING_SCOPE = "urn:safelayer:eidas:sign:process:document"
    private val RESPONSE_TYPE = "code"
    private val SCOPE = "urn:uae:digitalid:profile"
    private val ACR_VALUES_MOBILE = "urn:digitalid:authentication:flow:mobileondevice"
    private val ACR_VALUES_WEB = "urn:safelayer:tws:policies:authentication:level:low"

    private var activity: Activity? = null
    private lateinit var result: Result


    override fun onAttachedToActivity(@NonNull binding: ActivityPluginBinding) {
        if (activity == null)
            activity = binding.activity
        binding.addOnNewIntentListener(this)
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onReattachedToActivityForConfigChanges(@NonNull binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addOnNewIntentListener(this)
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }


    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "uae_pass")
        channel.setMethodCallHandler(this)

    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        this.result = result
        if (call.method == "set_up_environment") {
            CookieManager.getInstance().removeAllCookies { }
            CookieManager.getInstance().flush()
            client_id = call.argument<String>("client_id")
            client_secret = call.argument<String>("client_secret")
            redirect_url = call.argument<String?>("redirect_url")
            environment =
                if (call.argument<String>("environment") != null && call.argument<String>("environment") == "production") Environment.PRODUCTION else Environment.STAGING
            state = call.argument<String?>("state")
            scheme = call.argument<String>("scheme")
            failureHost = call.argument<String?>("failureHost")
            successHost = call.argument<String?>("successHost")
            scope = call.argument<String?>("scope")
            language = call.argument<String?>("language")

            if (redirect_url == null) {
                redirect_url = "https://oauthtest.com/authorization/return"
            }
            if (state == null) {
                state = generateRandomString(24)
            }

            if (failureHost == null) {
                failureHost = "failure"
            }
            if (successHost == null) {
                successHost = "success"
            }

        } else if (call.method == "sign_out") {

            CookieManager.getInstance().removeAllCookies { }
            CookieManager.getInstance().flush()
        } else if (call.method == "sign_in") {
            /** Login with UAE Pass using custom full screen webview */
            val authUrl = buildAuthUrl()
            val environmentString = if (environment == Environment.PRODUCTION) "production" else "staging"
            val intent = Intent(activity, UAEPassWebViewActivity::class.java).apply {
                putExtra(UAEPassWebViewActivity.EXTRA_AUTH_URL, authUrl)
                putExtra(UAEPassWebViewActivity.EXTRA_REDIRECT_URI, redirect_url)
                putExtra(UAEPassWebViewActivity.EXTRA_SCHEME, scheme)
                putExtra(UAEPassWebViewActivity.EXTRA_ENVIRONMENT, environmentString)
            }

            activity?.startActivityForResult(intent, 1001)
        } else if (call.method == "access_token") {
            requestModel = getAuthenticationRequestModel(activity!!)

            getAccessToken(activity!!, requestModel, object : UAEPassAccessTokenCallback {
                override fun getToken(accessToken: String?, state: String, error: String?) {
                    if (error != null) {
                        result.error("ERROR", error, null);
                    } else {
                        result.success(accessToken)
                    }
                }
            })
        } else if (call.method == "profile") {
            val requestModel = getProfileRequestModel(activity!!)

            Log.d("TAG", "profile ${Gson().toJson(requestModel)}")
            getUserProfile(activity!!, requestModel, object : UAEPassProfileCallback {
                override fun getProfile(
                    profileModel: ProfileModel?,
                    state: String,
                    error: String?
                ) {
                    Log.d("TAG", "error $error")
                    if (error != null) {
                        result.error("ERROR", error, null);
                    } else {
                        val gson = Gson()
                        val profileJson = gson.toJson(profileModel)
                        result.success(profileJson)
                    }
                }
            })
        } else {
            result.notImplemented()
        }
    }


    override fun onNewIntent(intent: Intent): Boolean {
        handleIntent(intent)
        return false
    }

    private fun handleIntent(intent: Intent?) {
        if (intent != null && intent.data != null) {
            if (scheme!! == intent.data!!.scheme) {
                resume(intent.dataString)
            }
        }
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun isPackageInstalled(packageManager: PackageManager): Boolean {
        val packageName = when (environment) {
            is Environment.STAGING -> {
                UAE_PASS_STG_PACKAGE_ID
            }

            is Environment.QA -> {
                UAE_PASS_QA_PACKAGE_ID
            }

            is Environment.PRODUCTION -> {
                UAE_PASS_PACKAGE_ID
            }

            else -> {
                UAE_PASS_PACKAGE_ID
            }
        }
        var found = true
        try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            found = false
        }
        return found
    }

    fun getAuthenticationRequestModel(context: Context): UAEPassAccessTokenRequestModel {
        val ACR_VALUE = if (isPackageInstalled(context.packageManager)) {
            ACR_VALUES_MOBILE
        } else {
            ACR_VALUES_WEB
        }
        val LANGUAGE = if (language == "ar") {
            Language.AR
        } else {
            Language.EN
        }
        return UAEPassAccessTokenRequestModel(
            environment!!,
            client_id!!,
            client_secret!!,
            scheme!!,
            failureHost!!,
            successHost!!,
            redirect_url!!,
            scope!!,
            RESPONSE_TYPE,
            ACR_VALUE,
            state!!,
            LANGUAGE
        )
    }

    fun getProfileRequestModel(context: Context): UAEPassProfileRequestModel {
        val ACR_VALUE = if (isPackageInstalled(context.packageManager)) {
            ACR_VALUES_MOBILE
        } else {
            ACR_VALUES_WEB
        }
        val LANGUAGE = if (language == "ar") {
            Language.AR
        } else {
            Language.EN
        }
        return UAEPassProfileRequestModel(
            environment!!,
            client_id!!,
            client_secret!!,
            scheme!!,
            failureHost!!,
            successHost!!,
            redirect_url!!,
            scope!!,
            RESPONSE_TYPE,
            ACR_VALUE,
            state!!,
            LANGUAGE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == 1001) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val code = data?.getStringExtra(UAEPassWebViewActivity.RESULT_CODE_SUCCESS)
                    if (code != null) {
                        result.success(code)
                    } else {
                        result.error("ERROR", "Unable to get access code", null)
                    }
                }
                Activity.RESULT_CANCELED -> {
                    val error = data?.getStringExtra(UAEPassWebViewActivity.RESULT_CODE_CANCELLED)
                        ?: "Authentication Process Canceled By User"
                    result.error("ERROR", error, null)
                }
                else -> {
                    result.error("ERROR", "Unknown error occurred", null)
                }
            }
            return true
        }
        return false
    }

    private fun buildAuthUrl(): String {
        val acrValue = if (isPackageInstalled(activity!!.packageManager)) {
            ACR_VALUES_MOBILE
        } else {
            ACR_VALUES_WEB
        }

        val baseUrl = when (environment) {
            Environment.PRODUCTION -> "https://id.uaepass.ae/idshub/authorize"
            else -> "https://stg-id.uaepass.ae/idshub/authorize"
        }

        return "$baseUrl?" +
                "response_type=$RESPONSE_TYPE&" +
                "client_id=$client_id&" +
                "redirect_uri=$redirect_url&" +
                "scope=$scope&" +
                "state=$state&" +
                "acr_values=$acrValue&" +
                "ui_locales=${if (language == "ar") "ar" else "en"}"
    }
}
