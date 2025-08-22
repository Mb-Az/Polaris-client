package com.example.myapplication

import Session
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SessionManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "session_prefs"
        private const val JWT_TOKEN_KEY = "jwt_token"
        private const val USER_ID_KEY = "user_id"
    }

    /**
     * Saves the JWT token and user ID to SharedPreferences.
     */
    fun saveAuthToken(token: String, userId: String) {
        val editor = prefs.edit()
        editor.putString(JWT_TOKEN_KEY, token)
        editor.putString(USER_ID_KEY, userId)
        editor.apply()
    }

    /**
     * Retrieves a complete Session object containing the JWT token and user ID.
     */
    fun getSession(): Session {
        val token = prefs.getString(JWT_TOKEN_KEY, null)
        val userId = prefs.getString(USER_ID_KEY, null)
        return Session(token, userId)
    }

    /**
     * Clears the saved JWT token and user ID (e.g., on logout).
     */
    fun clearSession() {
        val editor = prefs.edit()
        editor.remove(JWT_TOKEN_KEY)
        editor.remove(USER_ID_KEY)
        editor.apply()
    }

    /**
     * Authenticates with the server and saves the JWT and user ID on success.
     * @return true if login is successful, false otherwise.
     */
    suspend fun login(email: String, password: String): Boolean {
        return withContext(Dispatchers.IO) {
            val urlString = ConfigurationManager.getServerURL() + "/user/getId/"
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                val requestBody = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }.toString()

                // Write the request body to the output stream
                connection.outputStream.use { os ->
                    OutputStreamWriter(os, "UTF-8").use { writer ->
                        writer.write(requestBody)
                    }
                }

                // Get the response code after the request is sent
                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Read the response from the input stream
                    val responseJson = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseJson)
                    val accessToken = jsonObject.getString("access_token")
                    val userId = jsonObject.getString("Id")

                    saveAuthToken(accessToken, userId)
                    true
                } else {
                    Log.e("SessionManager", "Login failed with code: $responseCode")
                    false
                }
            } catch (e: Exception) {
                Log.e("SessionManager", "Error during login: ${e.message}", e)
                false
            } finally {
                connection?.disconnect()
            }
        }
    }
}