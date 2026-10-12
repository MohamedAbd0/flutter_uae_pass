package com.mvpapps.uae_pass_flutter

import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class UaePassEnvironment(val baseUrl: String, val appScheme: String, val appPackage: String) {
    STAGING("https://stg-id.uaepass.ae/", "uaepassstg://", "ae.uaepass.mainapp.stg"),
    PRODUCTION("https://id.uaepass.ae/", "uaepass://", "ae.uaepass.mainapp"),
}

data class UaePassConfig(
    val clientId: String,
    val clientSecret: String,
    val environment: UaePassEnvironment,
    val scheme: String,
    val redirectUrl: String,
    val state: String,
    val scope: String,
    val language: String,
) {
    /** Deep link the UAE PASS app opens to return to this app. Schemes are case-insensitive, Android intent filters expect lowercase. */
    fun returnUrl(host: String): String = "${scheme.lowercase()}://$host"

    fun loginUrl(useUaePassApp: Boolean): String =
        Uri.parse(environment.baseUrl + "idshub/authorize").buildUpon()
            .appendQueryParameter("redirect_uri", redirectUrl)
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("acr_values", if (useUaePassApp) ACR_VALUES_MOBILE else ACR_VALUES_WEB)
            .appendQueryParameter("ui_locales", language)
            .build()
            .toString()

    /** Exchanges an authorization code for an access token. Blocking; call off the main thread. */
    fun fetchAccessToken(code: String): String {
        val credentials = Base64.encodeToString("$clientId:$clientSecret".toByteArray(), Base64.NO_WRAP)
        val body = "grant_type=authorization_code" +
                "&redirect_uri=" + URLEncoder.encode(redirectUrl, "UTF-8") +
                "&code=" + URLEncoder.encode(code, "UTF-8")
        val json = JSONObject(
            request(environment.baseUrl + "idshub/token", "Basic $credentials", body)
        )
        return json.optString("access_token").ifEmpty {
            throw IllegalStateException(json.optString("error_description").ifEmpty { "Unable to get user token, Please try again." })
        }
    }

    /** Returns the user profile JSON. Blocking; call off the main thread. */
    fun fetchProfile(accessToken: String): String =
        request(environment.baseUrl + "idshub/userinfo", "Bearer $accessToken", null)

    private fun request(url: String, authorization: String, formBody: String?): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", authorization)
            connection.setRequestProperty("Accept", "application/json")
            if (formBody != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connection.outputStream.use { it.write(formBody.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException("UAE PASS request failed ($status): $response")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ACR_VALUES_MOBILE = "urn:digitalid:authentication:flow:mobileondevice"
        const val ACR_VALUES_WEB = "urn:safelayer:tws:policies:authentication:level:low"
        const val TIMEOUT_MS = 30_000
    }
}
