package com.example.myapplication

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity.MODE_PRIVATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject;


class ConfigurationManager(private val context: Context) {

    companion object {
        private const val SERVER_URL = "https://polaris-back.liara.run"
        private const val SMS_PHONE_NUMBER = "09186988776"
        private const val SMS_TEXT = "Hello! This is a Message for SMS test!"
        private const val THROUGHPUT_URL = "https://www.google.com"
        private const val DNS_RESOLVE_DOMAIN = "www.digikala.com"
        private const val PREFS_NAME = "MyPrefs"
        private const val POLLING_INTERVAL_KEY = "pollingInterval"
        private const val MEASUREMENT_INTERVAL_KEY = "measurementInterval"
        private const val PING_TEST_URL_KEY = "pingURL"
        private const val WEB_TEST_URL_KEY = "webURL"
        private const val SERVER_SYNC_INTERVAL_KEY = "serverSyncInterval"
        private const val TEST_INTERVAL_KEY = "testInterval"
        private const val THROUGHPUT_INCLUDED_KEY = "throughputIncluded"
        private const val PING_INCLUDED_KEY = "pingIncluded"
        private const val WEB_INCLUDED_KEY = "webIncluded"
        private const val DNS_INCLUDED_KEY = "dnsIncluded"
        private const val SMS_INCLUDED_KEY = "smsIncluded"


        private const val DEFAULT_POLLING_INTERVAL = /*1 * 60 **/ 60 * 1000L //1 hour
        private const val DEFAULT_MEASUREMENT_INTERVAL = 3 * 1000L //30 seconds
        private const val DEFAULT_PING_TEST_URL = "google.com"
        private const val DEFAULT_WEB_TEST_URL = "https://www.google.com"
        private const val DEFAULT_SERVER_SYNC_INTERVAL = /*10 */ 10 * 1000L //10 minutes
        private const val DEFAULT_TEST_INTERVAL = 6 * 1000L //5 minutes
        private const val DEFAULT_THROUGHPUT_INCLUDED = true
        private const val DEFAULT_PING_INCLUDED = true
        private const val DEFAULT_WEB_INCLUDED = true
        private const val DEFAULT_DNS_INCLUDED = true
        private const val DEFAULT_SMS_INCLUDED = true


        public fun getServerURL() : String {
            return SERVER_URL
        }

        public fun getSMSPhoneNumber() : String {
            return SMS_PHONE_NUMBER
        }

        public fun getSMSText() : String {
            return SMS_TEXT
        }

        public fun getThroughputURL() : String {
            return THROUGHPUT_URL
        }

        public fun getDnsResolveDomain() : String {
            return DNS_RESOLVE_DOMAIN
        }
    }

    private fun saveNewConfiguration(
        pollingInterval: Long?, measurementInterval: Long?, pingTestUrl: String?,
        webTestUrl: String?, serverSyncInterval: Long?, testInterval: Long?,
        throughputIncluded: Boolean?, pingIncluded: Boolean?, webIncluded: Boolean?,
        dnsIncluded: Boolean?, smsIncluded: Boolean?
    ) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            if (pollingInterval != null)
                putLong(POLLING_INTERVAL_KEY, pollingInterval)

            if(measurementInterval != null)
                putLong(MEASUREMENT_INTERVAL_KEY, measurementInterval)

            if(!pingTestUrl.isNullOrEmpty())
                putString(PING_TEST_URL_KEY, pingTestUrl)

            if(!webTestUrl.isNullOrEmpty())
                putString(WEB_TEST_URL_KEY, webTestUrl)

            if(serverSyncInterval != null)
                putLong(SERVER_SYNC_INTERVAL_KEY, serverSyncInterval)

            if(testInterval != null)
                putLong(TEST_INTERVAL_KEY, testInterval)

            if(throughputIncluded != null)
                putBoolean(THROUGHPUT_INCLUDED_KEY, throughputIncluded)

            if(pingIncluded != null)
                putBoolean(PING_INCLUDED_KEY, pingIncluded)

            if(webIncluded != null)
                putBoolean(WEB_INCLUDED_KEY, webIncluded)

            if(dnsIncluded != null)
                putBoolean(DNS_INCLUDED_KEY, dnsIncluded)

            if(smsIncluded != null)
                putBoolean(SMS_INCLUDED_KEY, smsIncluded)

            apply()
        }
    }

    public fun getPollingInterval(): Long {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getLong(POLLING_INTERVAL_KEY, DEFAULT_POLLING_INTERVAL)
    }

    public fun getMeasurementInterval(): Long {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getLong(MEASUREMENT_INTERVAL_KEY, DEFAULT_MEASUREMENT_INTERVAL)
    }

    public fun getPingTestUrl(): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getString(PING_TEST_URL_KEY, DEFAULT_PING_TEST_URL) ?: DEFAULT_PING_TEST_URL
    }

    public fun getWebTestUrl(): String {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getString(WEB_TEST_URL_KEY, DEFAULT_WEB_TEST_URL) ?: DEFAULT_WEB_TEST_URL
    }

    public fun getServerSyncInterval(): Long {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getLong(SERVER_SYNC_INTERVAL_KEY, DEFAULT_SERVER_SYNC_INTERVAL)
    }

    public fun getTestInterval() : Long {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getLong(TEST_INTERVAL_KEY, DEFAULT_TEST_INTERVAL)
    }

    public fun getThroughputIncluded() : Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getBoolean(THROUGHPUT_INCLUDED_KEY, DEFAULT_THROUGHPUT_INCLUDED)
    }

    public fun getPingIncluded() : Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getBoolean(PING_INCLUDED_KEY, DEFAULT_PING_INCLUDED)
    }

    public fun getWebIncluded() : Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getBoolean(WEB_INCLUDED_KEY, DEFAULT_WEB_INCLUDED)
    }

    public fun getDNSIncluded() : Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getBoolean(DNS_INCLUDED_KEY, DEFAULT_DNS_INCLUDED)
    }

    public fun getSMSIncluded() : Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return sharedPreferences.getBoolean(SMS_INCLUDED_KEY, DEFAULT_SMS_INCLUDED)
    }

    suspend fun fetchConfigurationFromServer(){
        return withContext(Dispatchers.IO) {
            val urlString = ConfigurationManager.getServerURL() + "/config/get/" // Set the correct API endpoint
            try {
                val url = URL(urlString)
                (url.openConnection() as HttpURLConnection).run {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000

                    val responseCode = responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val reader = inputStream.bufferedReader()
                        val responseJson = reader.use { it.readText() }
                        reader.close()

                        val jsonObject = JSONObject(responseJson)

                        val newPollingIntervalMinutes = jsonObject.getLong("polling_interval")
                        val newPollingIntervalMillis = newPollingIntervalMinutes * 60000
                        val newMeasurementIntervalMinutes = jsonObject.getLong("measurement_interval")
                        val newMeasurementIntervalMillis = newMeasurementIntervalMinutes * 60000
                        val newServerSyncIntervalMinutes = jsonObject.getLong("server_sync_interval")
                        val newServerSyncIntervalMillis = newServerSyncIntervalMinutes * 60000
                        val newTestIntervalMinutes = jsonObject.getLong("test_interval")
                        val newTestIntervalMillis = newTestIntervalMinutes * 60000
                        val newPingTestUrl = jsonObject.getString("ping_url")
                        val newWebTestUrl = jsonObject.getString("web_url")
                        // Note: The API response format doesn't provide sms_phone_number or throughput_url,
                        // so those will continue to use the hardcoded values.
                        throw Exception()
                        val newThroughputIncluded = jsonObject.getBoolean("throughput_included")
                        val newPingIncluded = jsonObject.getBoolean("ping_included")
                        val newWebIncluded = jsonObject.getBoolean("web_included")
                        val newDnsIncluded = jsonObject.getBoolean("dns_included")
                        val newSmsIncluded = jsonObject.getBoolean("sms_included")

                        saveNewConfiguration(
                            newPollingIntervalMillis,
                            newMeasurementIntervalMillis,
                            newPingTestUrl,
                            newWebTestUrl,
                            newServerSyncIntervalMillis,
                            newTestIntervalMillis,
                            newThroughputIncluded,
                            newPingIncluded,
                            newWebIncluded,
                            newDnsIncluded,
                            newSmsIncluded
                        )

                    } else {
                        Log.e("ConfigManager", "Failed to fetch config: $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("ConfigManager", "Error fetching config: ${e.message}", e)
            }
        }
    }
}