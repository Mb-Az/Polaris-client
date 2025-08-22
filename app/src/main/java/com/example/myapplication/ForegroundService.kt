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
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Timer
import java.util.TimerTask

private const val NOTIFICATION_CHANNEL_ID = "location_monitoring_channel"
private const val NOTIFICATION_ID = 1

class ForegroundService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var dbHelper: CellInfoDatabaseHelper
    private lateinit var testDbHelper: TestResultDatabaseHelper
    private lateinit var configurationManager: ConfigurationManager
    private lateinit var testManager: TestManager
    private lateinit var sessionManager: SessionManager

    private var pollingTimer: Timer? = null
    private var testTimer: Timer? = null
    private var serverSyncTimer: Timer? = null

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())



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
        serviceScope.launch {
            try {
                sessionManager.login("user@example.com", "string")
            } catch (e: Exception) {
                // Handle any login errors here
                Log.e("ForegroundService", "Login failed", e)
            }
        }
        // Moved location setup to onCreate to prevent redundant listeners.
        setupAndStartLocationUpdates()

        // Start all main routines as soon as the service is created.
        startPollingRoutine()
        startTestRoutine()
        startServerSyncRoutine()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure location updates are removed when the service is destroyed.
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
        pollingTimer?.cancel()
        testTimer?.cancel()
        serverSyncTimer?.cancel()
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

    // New method to set up and start location updates.
    private fun setupAndStartLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, configurationManager.getMeasurementInterval())
            .setMinUpdateIntervalMillis(configurationManager.getMeasurementInterval())
            .build()

        locationCallback = object : LocationCallback() {
            @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val cellInfoCollector = CellInfoCollector(applicationContext)
                val cellInfo = cellInfoCollector.getCellInfo()
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                serviceScope.launch {
                    dbHelper.insertLocation(location.latitude, location.longitude, timestamp, cellInfo)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun startPollingRoutine() {
        pollingTimer?.cancel()
        pollingTimer = Timer()
        val oldPollingInterval = configurationManager.getPollingInterval()
        val oldMeasurementInterval = configurationManager.getMeasurementInterval()
        val oldTestMeasurementInterval = configurationManager.getTestInterval()
        val oldServerSyncInterval = configurationManager.getServerSyncInterval()

        val pollingTask = object : TimerTask() {
            override fun run() {
                serviceScope.launch {
                    configurationManager.fetchConfigurationFromServer()
                    val newPollingInterval = configurationManager.getPollingInterval()
                    val newMeasurementInterval = configurationManager.getMeasurementInterval()
                    val newTestMeasurementInterval = configurationManager.getTestInterval()
                    val newServerSyncInterval = configurationManager.getServerSyncInterval()

                    if (oldPollingInterval != newPollingInterval) {
                        startPollingRoutine()
                    }
                    if (oldMeasurementInterval != newMeasurementInterval) {
                        // If the measurement interval changes, restart the location updates with the new settings.
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        setupAndStartLocationUpdates()
                    }
                    if (oldTestMeasurementInterval != newTestMeasurementInterval) {
                        startTestRoutine()
                    }
                    if (oldServerSyncInterval != newServerSyncInterval) {
                        startServerSyncRoutine()
                    }
                }
            }
        }
        pollingTimer?.schedule(pollingTask, 0, configurationManager.getPollingInterval())
    }

    // This routine is now redundant and can be removed.
    // The single, active location listener handles the measurement interval.
    // private fun startMeasurementRoutine() { ... }

    private fun startTestRoutine() {
        testTimer?.cancel()
        testTimer = Timer()
        val testTask = object : TimerTask() {
            override fun run() {
                serviceScope.launch(Dispatchers.IO){
                    val location = try {
                        @SuppressLint("MissingPermission")
                        fusedLocationClient.lastLocation.await()
                    } catch (e: Exception) {
                        Log.e("LocationService", "Failed to get last known location for test: ${e.message}")
                        null
                    }
                    val testResults = testManager.CollectTestResult(configurationManager)
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    testDbHelper.insertTestResult(timestamp,location?.latitude, location?.longitude,testResults)
                }
            }
        }
        testTimer?.schedule(testTask, 0, configurationManager.getTestInterval())
    }

    /**
     * Refactored server sync routine to retrieve and send all unsent data from both databases.
     */
    private fun startServerSyncRoutine() {
        serverSyncTimer?.cancel()
        serverSyncTimer = Timer()
        val serverSyncTask = object : TimerTask() {
            override fun run() {
                serviceScope.launch(Dispatchers.IO) {
                    val deviceId = sessionManager.getSession().id
                    if (deviceId == null) {
                        // Cannot upload without a device ID
                        return@launch
                    }

                    val (unsentCellRecords, cellIds) = dbHelper.getUnsentRecords()
                    val (unsentTestRecords, testIds) = testDbHelper.getUnsentRecords()

                    if (unsentCellRecords.isEmpty() && unsentTestRecords.isEmpty()) {
                        return@launch
                    }

                    val mainJsonObject = JSONObject()
                    val cellArray = JSONArray()
                    unsentCellRecords.forEach { record ->
                        // Add deviceId and location to the cell measurement record
                        record.put("deviceId", deviceId)
                        record.put("latitude", record.getDouble("latitude")) // Ensure location is included
                        record.put("longitude", record.getDouble("longitude"))
                        cellArray.put(record)
                    }
                    mainJsonObject.put("cell_measurements", cellArray)

                    val testArray = JSONArray()
                    unsentTestRecords.forEach { record ->
                        // Add deviceId and location to the test result record
                        record.put("deviceId", deviceId)
                        // You might need to add latitude and longitude to the TestResultDatabaseHelper
                        // if it doesn't already store it. Assuming it does, you can retrieve it here.
                        // For now, this is a placeholder.
                        record.put("latitude", record.getDouble("latitude")) // Placeholder
                        record.put("longitude", record.getDouble("longitude")) // Placeholder
                        testArray.put(record)
                    }
                    mainJsonObject.put("test_results", testArray)

                    val urlString = ConfigurationManager.getServerURL() + "/data/android/upload/"
                    try {
                        val url = URL(urlString)
                        (url.openConnection() as HttpURLConnection).run {
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

                            val responseCode = responseCode
                            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                                // Mark records as sent only if the upload was successful
                                dbHelper.markRecordsAsSent(cellIds)
                                testDbHelper.markRecordsAsSent(testIds)
                            } else {
                                // Handle the failed upload (e.g., log the error)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        serverSyncTimer?.schedule(serverSyncTask, 0, configurationManager.getServerSyncInterval())
    }
}
