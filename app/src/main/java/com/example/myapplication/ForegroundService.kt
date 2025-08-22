package com.example.myapplication

import CellInfoCollector
import CellInfoDatabaseHelper
import TestManager
import TestResultDatabaseHelper
import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val NOTIFICATION_CHANNEL_ID = "location_monitoring_channel"
private const val NOTIFICATION_ID = 1

class ForegroundService : Service() {

    // Define actions for communication with MainActivity
    companion object {
        const val ACTION_STOP_SERVICE = "com.example.myapplication.ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_UI = "com.example.myapplication.ACTION_UPDATE_UI"
    }

    // Coroutine scope for the service lifecycle, using SupervisorJob for fault tolerance
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var dbHelper: CellInfoDatabaseHelper
    private lateinit var testDbHelper: TestResultDatabaseHelper
    private lateinit var configurationManager: ConfigurationManager
    private lateinit var testManager: TestManager
    private lateinit var sessionManager: SessionManager

    // Coroutine Jobs for each periodic routine
    private var pollingJob: Job? = null
    private var testJob: Job? = null
    private var serverSyncJob: Job? = null

    // Status flags
    private var pollingSuccess = false
    private var serverSyncSuccess = false
    private var cellMeasurementSuccess = false
    private var testSuccess = false

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        dbHelper = CellInfoDatabaseHelper(this)
        testDbHelper = TestResultDatabaseHelper(this)
        configurationManager = ConfigurationManager(this)
        testManager = TestManager(this)
        sessionManager = SessionManager(this)

        // Attempt login on service creation
        serviceScope.launch {
            try {
                sessionManager.login("user@example.com", "string")
                // Broadcast initial configuration after login and setup
                broadcastConfigurationUpdate()
            } catch (e: Exception) {
                Log.e("ForegroundService", "Login failed", e)
            }
        }

        // Setup location updates and start all main routines
        setupAndStartLocationUpdates()
        startPollingRoutine()
        startTestRoutine()
        startServerSyncRoutine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure all resources are cleaned up
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        pollingJob?.cancel()
        testJob?.cancel()
        serverSyncJob?.cancel()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Location Monitoring Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Monitoring Location")
            .setContentText("The app is actively collecting location data.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun setupAndStartLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, configurationManager.getMeasurementInterval().toLong())
            .setMinUpdateIntervalMillis(configurationManager.getMeasurementInterval().toLong())
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                try {
                    val cellInfoCollector = CellInfoCollector(applicationContext)
                    val cellInfo = cellInfoCollector.getCellInfo()
                    val timestampFinal = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    serviceScope.launch {
                        dbHelper.insertLocation(location.latitude, location.longitude, timestampFinal, cellInfo)
                        cellMeasurementSuccess = true
                        broadcastConfigurationUpdate()
                    }
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error in cell measurement routine: ${e.message}")
                    cellMeasurementSuccess = false
                    broadcastConfigurationUpdate()
                }
            }
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    /**
     * Polling routine to check for configuration updates from the server.
     * Cancels any existing job before starting a new one.
     */
    private fun startPollingRoutine() {
        pollingJob?.cancel()
        pollingJob = serviceScope.launch {
            while (isActive) {
                val oldMeasurementInterval = configurationManager.getMeasurementInterval()
                val oldTestInterval = configurationManager.getTestInterval()
                val oldServerSyncInterval = configurationManager.getServerSyncInterval()

                try {
                    configurationManager.fetchConfigurationFromServer()
                    val newMeasurementInterval = configurationManager.getMeasurementInterval()
                    val newTestInterval = configurationManager.getTestInterval()
                    val newServerSyncInterval = configurationManager.getServerSyncInterval()

                    pollingSuccess = true
                    // Broadcast the new configuration to the UI
                    broadcastConfigurationUpdate()

                    if (oldMeasurementInterval != newMeasurementInterval) {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        setupAndStartLocationUpdates()
                    }
                    if (oldTestInterval != newTestInterval) {
                        startTestRoutine()
                    }
                    if (oldServerSyncInterval != newServerSyncInterval) {
                        startServerSyncRoutine()
                    }
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error in polling routine: ${e.message}")
                    pollingSuccess = false
                    broadcastConfigurationUpdate()
                }
                delay(configurationManager.getPollingInterval().toLong())
            }
        }
    }

    /**
     * Test routine to collect and save test results periodically.
     * Cancels any existing job before starting a new one.
     */
    private fun startTestRoutine() {
        testJob?.cancel()
        testJob = serviceScope.launch {
            while (isActive) {
                try {
                    @SuppressLint("MissingPermission")
                    val location = fusedLocationClient.lastLocation.await()
                    val testResults = testManager.CollectTestResult(configurationManager)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    if (testResults.hasFailedTests()) {
                        testSuccess = false
                        Log.e("ForegroundService", "One or more enabled tests failed.")
                    } else {
                        testSuccess = true
                    }

                    testDbHelper.insertTestResult(timestamp, location?.latitude, location?.longitude, testResults)
                    broadcastConfigurationUpdate()
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Failed to get last known location for test or test failed: ${e.message}")
                    testSuccess = false
                    broadcastConfigurationUpdate()
                }
                delay(configurationManager.getTestInterval().toLong())
            }
        }
    }

    /**
     * Server synchronization routine to upload unsent data.
     * Cancels any existing job before starting a new one.
     */
    private fun startServerSyncRoutine() {
        serverSyncJob?.cancel()
        serverSyncJob = serviceScope.launch {
            while (isActive) {
                try {
                    val deviceId = sessionManager.getSession().id
                    if (deviceId == null) {
                        Log.e("ForegroundService", "Cannot upload data without a device ID.")
                        serverSyncSuccess = false
                        broadcastConfigurationUpdate()
                        delay(configurationManager.getServerSyncInterval().toLong())
                        continue
                    }

                    val (unsentCellRecords, cellIds) = dbHelper.getUnsentRecords()
                    val (unsentTestRecords, testIds) = testDbHelper.getUnsentRecords()

                    if (unsentCellRecords.isNotEmpty() || unsentTestRecords.isNotEmpty()) {
                        val mainJsonObject = JSONObject()
                        val cellArray = JSONArray()
                        unsentCellRecords.forEach { record ->
                            record.put("deviceId", deviceId)
                            record.put("latitude", record.getDouble("latitude"))
                            record.put("longitude", record.getDouble("longitude"))
                            cellArray.put(record)
                        }
                        mainJsonObject.put("cell_measurements", cellArray)

                        val testArray = JSONArray()
                        unsentTestRecords.forEach { record ->
                            record.put("deviceId", deviceId)
                            record.put("latitude", record.getDouble("latitude"))
                            record.put("longitude", record.getDouble("longitude"))
                            testArray.put(record)
                        }
                        mainJsonObject.put("test_results", testArray)

                        val urlString = ConfigurationManager.getServerURL() + "/data/android/upload/"
                        (URL(urlString).openConnection() as HttpURLConnection).run {
                            requestMethod = "POST"
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            doOutput = true
                            connectTimeout = 10000
                            readTimeout = 10000
                            val authToken = sessionManager.getSession().accessToken
                            if (authToken != null) {
                                setRequestProperty("Authorization", "Bearer $authToken")
                            }
                            OutputStreamWriter(outputStream).use { it.write(mainJsonObject.toString()) }
                            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                                dbHelper.markRecordsAsSent(cellIds)
                                testDbHelper.deleteRecords(testIds)
                                serverSyncSuccess = true
                            } else {
                                Log.e("ForegroundService", "Upload failed with response code: $responseCode")
                                serverSyncSuccess = false
                            }
                        }
                    } else {
                        // No data to upload, consider it a success for this cycle
                        serverSyncSuccess = true
                    }
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error during server sync: ${e.message}")
                    serverSyncSuccess = false
                }
                broadcastConfigurationUpdate()
                delay(configurationManager.getServerSyncInterval().toLong())
            }
        }
    }

    private fun broadcastConfigurationUpdate() {
        val updateIntent = Intent(ACTION_UPDATE_UI).apply {
            putExtra("syncInterval", configurationManager.getServerSyncInterval().toLong())
            putExtra("measurementInterval", configurationManager.getMeasurementInterval().toLong())
            putExtra("testInterval", configurationManager.getTestInterval().toLong())
            putExtra("pollingSuccess", pollingSuccess)
            putExtra("serverSyncSuccess", serverSyncSuccess)
            putExtra("cellMeasurementSuccess", cellMeasurementSuccess)
            putExtra("testSuccess", testSuccess)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(updateIntent)
    }
}